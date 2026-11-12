#!/usr/bin/env bash
# One Bukkit-family runtime scenario, on a fresh JVM and a fresh world.
#
# Scenarios: default | custom | vanilla | reload | skip
# Each boot proves only its own scenario; nothing is carried over between runs.
#
# Usage: probe-bukkit.sh <mc> <scenario>
set -uo pipefail

mc="$1"
scenario="$2"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Java/jdk1.8.0_503}"
JAVA="$JAVA_HOME/bin/java.exe"
DIR="$ROOT/runtime/bukkit-$mc/server"
JAR="$ROOT/dist/rottenleather-bukkit-1.0.1.jar"
PAPER="$ROOT/../livedaw-mc/automation/jars/paper-$mc.jar"
LOG="$ROOT/logs/probe-bukkit-$mc-$scenario.log"
OUT="$ROOT/logs/probe-bukkit-$mc"
PROBE_TAG="bukkit-$mc/$scenario"
mkdir -p "$OUT" "$ROOT/logs"
: > "$LOG"
. "$ROOT/tools/probe-lib.sh"

BOOT_PID=""
cleanup() {
    [ -n "${BOOT_PID:-}" ] || return 0
    rcon stop > /dev/null 2>&1 || true
    for i in 1 2 3 4 5 6 7 8 9 10; do
        kill -0 "$BOOT_PID" 2>/dev/null || break
        sleep 1
    done
    kill "$BOOT_PID" 2>/dev/null || true
    wait "$BOOT_PID" 2>/dev/null || true
}
trap cleanup EXIT
trap 'cleanup; exit 130' INT TERM

[ -f "$JAR" ] || fail "missing $JAR (run tools/build-bukkit.sh)"
[ -f "$PAPER" ] || fail "missing server jar $PAPER"
DEFAULT_FINGERPRINT="$(grep -oE '[0-9a-f]{64}' "$ROOT/shared/src/main/java/com/rottenleather/core/BuildDefaults.java" | head -1)"
[ -n "$DEFAULT_FINGERPRINT" ] || fail "could not read the generated spec fingerprint"
assign_ports 0 0
RPORT="$PROBE_RPORT"

rm -rf "$DIR/world" "$DIR/world_nether" "$DIR/world_the_end" "$DIR/plugins/RottenFleshToLeather" "$DIR/logs" \
    || fail "could not remove the old world under $DIR (is a stale server still running?)"
[ -d "$DIR/world" ] && fail "old world under $DIR survived deletion"
mkdir -p "$DIR/plugins/RottenFleshToLeather"
cp -f "$PAPER" "$DIR/server.jar"
cat > "$DIR/server.properties" <<EOF
online-mode=false
level-type=FLAT
level-seed=rottenleather
spawn-npcs=false
spawn-animals=false
spawn-monsters=false
generate-structures=false
difficulty=0
spawn-protection=0
view-distance=6
max-tick-time=-1
server-port=$PROBE_PORT
enable-rcon=true
rcon.port=$PROBE_RPORT
rcon.password=rf
motd=rottenleather bukkit probe
EOF
printf 'eula=true\n' > "$DIR/eula.txt"

write_plugin_config() { # input output count xp ticks policy
    cat > "$DIR/plugins/RottenFleshToLeather/config.yml" <<EOF
recipe:
  input-item: $1
  output-item: $2
  output-count: $3
  experience: $4
  smelt-time-ticks: $5
  conflict-policy: $6
logging:
  log-registration: true
network:
  report-to-proxy: false
  server-name: probe
EOF
}

case "$scenario" in
    default) write_plugin_config minecraft:rotten_flesh minecraft:leather 1 0.1 200 skip
             EXPECTED_FP=$(fingerprint_for minecraft:rotten_flesh minecraft:leather 1 0.1 200 skip)
             [ "$EXPECTED_FP" = "$DEFAULT_FINGERPRINT" ]                  || fail "generated defaults are stale: BuildDefaults has $DEFAULT_FINGERPRINT, the recipe hashes to $EXPECTED_FP" ;;
    custom)  write_plugin_config minecraft:rotten_flesh minecraft:leather 3 0.35 60 skip
             EXPECTED_FP=$(fingerprint_for minecraft:rotten_flesh minecraft:leather 3 0.35 60 skip) ;;
    vanilla) write_plugin_config minecraft:rotten_flesh minecraft:leather 3 0.35 60 skip
             EXPECTED_FP=$(fingerprint_for minecraft:rotten_flesh minecraft:leather 3 0.35 60 skip) ;;
    reload)  write_plugin_config minecraft:rotten_flesh minecraft:leather 1 0.1 200 skip
             EXPECTED_FP=$(fingerprint_for minecraft:rotten_flesh minecraft:leather 1 0.1 200 skip) ;;
    skip)    write_plugin_config minecraft:beef minecraft:leather 1 0.1 200 skip
             EXPECTED_FP=$(fingerprint_for minecraft:beef minecraft:leather 1 0.1 200 skip) ;;
    *) fail "unknown scenario '$scenario'" ;;
esac

installed=no
for attempt in 1 2 3 4 5 6 7 8 9 10; do
    rm -f "$DIR/plugins/"*.jar 2>/dev/null
    cp -f "$JAR" "$DIR/plugins/" 2>/dev/null
    if cmp -s "$JAR" "$DIR/plugins/$(basename "$JAR")"; then installed=yes; break; fi
    sleep 1
done
[ "$installed" = yes ] || fail "could not install $JAR into $DIR/plugins (stale server holding the file?)"
: > "$DIR/console.probe.log"
( cd "$DIR" && "$JAVA" -Xmx1200M -Xms512M -jar server.jar nogui > console.probe.log 2>&1 ) &
BOOT_PID=$!
for i in $(seq 1 300); do
    if grep -q 'Done (' "$DIR/console.probe.log" 2>/dev/null; then
        grep -q "RCON running on .*:$PROBE_RPORT" "$DIR/console.probe.log" \
            || fail "RCON did not bind $PROBE_RPORT (another server may own it)"
        break
    fi
    kill -0 "$BOOT_PID" 2>/dev/null || { tail -25 "$DIR/console.probe.log"; fail "server exited before Done"; }
    sleep 1
done
grep -q 'Done (' "$DIR/console.probe.log" || fail "server did not reach Done"
say "boot reached Done (game $PROBE_PORT, rcon $PROBE_RPORT)"
rcon "setworldspawn 2 4 2" > /dev/null
wait_world_ready 90 || fail "the world never accepted block edits"

rcon "rottenleather status" > "$OUT/status-$scenario.txt" 2>&1
grep -q "fingerprint=$EXPECTED_FP" "$OUT/status-$scenario.txt"     || fail "the running recipe does not match the config the probe wrote"
say "status: $(tail -1 "$OUT/status-$scenario.txt")"

case "$scenario" in
    default)
        grep -q 'registered=true' "$OUT/status-$scenario.txt" || fail "default recipe not registered"
        grep -q 'nmsXp=0.1' "$OUT/status-$scenario.txt" || fail "configured XP not in the server's record"
        smelt_and_measure 2 minecraft:rotten_flesh minecraft:leather 1 vanilla 240
        ;;
    custom)
        grep -q 'registered=true' "$OUT/status-$scenario.txt" || fail "custom recipe not registered"
        grep -q 'nmsXp=0.35' "$OUT/status-$scenario.txt" || fail "configured XP not in the server's record"
        if grep -q 'customCookTime=false' "$OUT/status-$scenario.txt"; then
            grep -q 'furnace is fixed at 200 ticks' "$DIR/console.probe.log" \
                || fail "custom cook time unsupported here but the documented warning is missing"
            say "custom cook time unsupported on this version: warning present, asserting vanilla timing"
            smelt_and_measure 2 minecraft:rotten_flesh minecraft:leather 3 vanilla 240
        else
            smelt_and_measure 2 minecraft:rotten_flesh minecraft:leather 3 custom 240
        fi
        ;;
    vanilla)
        grep -q 'registered=true' "$OUT/status-$scenario.txt" || fail "recipe not registered"
        smelt_and_measure 2 minecraft:beef minecraft:cooked_beef 1 vanilla 240
        chest_lacks 2 4 2 minecraft:leather || fail "beef produced leather instead of cooked beef"
        ;;
    reload)
        grep -q 'registered=true' "$OUT/status-$scenario.txt" || fail "default recipe not registered"
        grep -q 'nmsXp=0.1' "$OUT/status-$scenario.txt" || fail "default XP not in the server's record"
        # The 1.8-1.12.2 Bukkit API cannot remove a furnace recipe, so the plugin applies XP and
        # cook time live and needs a restart for a changed output count. The reload scenario therefore
        # changes exactly what reload is documented to change.
        write_plugin_config minecraft:rotten_flesh minecraft:leather 1 0.35 80 skip
        rcon "rottenleather reload" > "$OUT/reload-$scenario.txt" 2>&1
        RELOAD_FP=$(fingerprint_for minecraft:rotten_flesh minecraft:leather 1 0.35 80 skip)
        grep -q "fingerprint=$RELOAD_FP" "$OUT/reload-$scenario.txt"             || fail "reload registered a different recipe than the config describes"
        grep -q 'ticks=80' "$OUT/reload-$scenario.txt" || fail "reload did not apply ticks=80"
        grep -q 'nmsXp=0.35' "$OUT/reload-$scenario.txt" || fail "reload did not apply XP=0.35"
        if grep -q 'customCookTime=false' "$OUT/reload-$scenario.txt"; then
            grep -q 'furnace is fixed at 200 ticks' "$DIR/console.probe.log" \
                || fail "custom cook time unsupported here but the documented warning is missing"
            smelt_and_measure 2 minecraft:rotten_flesh minecraft:leather 1 vanilla 240
        else
            smelt_and_measure 2 minecraft:rotten_flesh minecraft:leather 1 custom 240
        fi
        ;;
    skip)
        grep -q 'install=KEEP_EXISTING' "$OUT/status-$scenario.txt" || fail "skip did not keep the vanilla beef recipe"
        grep -q 'registered=false' "$OUT/status-$scenario.txt" || fail "skip reports its recipe as registered"
        smelt_and_measure 2 minecraft:beef minecraft:cooked_beef 1 vanilla 240
        chest_lacks 2 4 2 minecraft:leather || fail "skip produced leather instead of vanilla cooked beef"
        ;;
esac

cat "$DIR"/console.probe.log > "$OUT/console-$scenario.log"
grep -q "Rotten Flesh to Leather" "$OUT/console-$scenario.log" || fail "no activation line"
if grep -Eq '^[[:space:]]+at com\.rottenleather' "$OUT/console-$scenario.log"; then
    fail "plugin stack trace in the server console"
fi
grep -q 'recipe experience API not available' "$OUT/console-$scenario.log" && fail "NMS experience bridge unavailable"
if ! grep -q 'customCookTime=false' "$OUT/status-$scenario.txt"; then
    grep -q 'cook-time API not available' "$OUT/console-$scenario.log" && fail "NMS cook-time bridge unavailable"
fi

say "PASS bukkit-$mc $scenario"
