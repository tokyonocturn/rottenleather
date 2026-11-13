#!/usr/bin/env bash
# One Forge runtime scenario, on a fresh JVM and a fresh world.
#
# Every scenario is a separate boot so no chunk, hopper, furnace or config state can leak from the
# previous one; each run therefore proves its own claims and nothing depends on run order.
#
# Scenarios:
#   default  rotten flesh -> 1 leather at 200 ticks, coal consumed, slots emptied, hopper automation
#   custom   outputCount=3, xp=0.35, smeltTimeTicks=60 (or the documented warning where unsupported)
#   vanilla  the same custom config must leave beef -> cooked beef at vanilla 200-tick timing
#   reload   boot at defaults, /rottenleather reload to outputCount=3/xp=0.35/ticks=80, then smelt
#   skip     beef -> leather with conflictPolicy=skip keeps the vanilla beef recipe
#   replace  beef -> leather with conflictPolicy=replace (1.9+; 1.8.x asserts the documented warning)
#
# Usage: probe-forge.sh <mc> <forge-id> <scenario>
set -uo pipefail

mc="$1"
forge="$2"
scenario="$3"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Java/jdk1.8.0_503}"
JAVA="$JAVA_HOME/bin/java.exe"
DIR="$ROOT/runtime/$mc/server"
JAR="$ROOT/dist/rottenleather-$mc-1.0.2.jar"
LOG="$ROOT/logs/probe-forge-$mc-$scenario.log"
OUT="$ROOT/logs/probe-forge-$mc"
PROBE_TAG="$mc/$scenario"
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
    if kill -0 "$BOOT_PID" 2>/dev/null; then
        # A Windows JVM can ignore the polite signal; a surviving server poisons the next scenario.
        taskkill //PID "$BOOT_PID" //T //F > /dev/null 2>&1 || true
    fi
}
trap cleanup EXIT
trap 'cleanup; exit 130' INT TERM

[ -f "$JAR" ] || fail "missing $JAR (run tools/build-forge.sh $mc)"
[ -f "$DIR/forge-$forge-universal.jar" ] || fail "missing server jar $DIR/forge-$forge-universal.jar"
DEFAULT_FINGERPRINT="$(grep -oE '[0-9a-f]{64}' "$ROOT/shared/src/main/java/com/rottenleather/core/BuildDefaults.java" | head -1)"
[ -n "$DEFAULT_FINGERPRINT" ] || fail "could not read the generated spec fingerprint"
assign_ports 0 0
RPORT="$PROBE_RPORT"

rm -rf "$DIR/world" "$DIR/world_nether" "$DIR/world_the_end" "$DIR/crash-reports" \
    || fail "could not remove the old world under $DIR (is a stale server still running?)"
[ -d "$DIR/world" ] && fail "old world under $DIR survived deletion"
mkdir -p "$DIR/mods" "$DIR/config"
# A previous server that has not fully exited keeps the jar locked; copying then silently leaves an
# older build in place, which makes a run test stale code. Retry until the bytes really match.
installed=no
for attempt in 1 2 3 4 5 6 7 8 9 10; do
    rm -f "$DIR"/mods/*.jar 2>/dev/null
    cp -f "$JAR" "$DIR/mods/" 2>/dev/null
    if cmp -s "$JAR" "$DIR/mods/$(basename "$JAR")"; then installed=yes; break; fi
    sleep 1
done
[ "$installed" = yes ] || fail "could not install $JAR into $DIR/mods (stale server holding the file?)"
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
motd=rottenleather probe
EOF
printf 'eula=true\n' > "$DIR/eula.txt"

write_config() { # input output count xp ticks policy
    cat > "$DIR/config/rottenleather.cfg" <<EOF
recipe {
    S:inputItem=$1
    S:outputItem=$2
    I:outputCount=$3
    F:experience=$4
    I:smeltTimeTicks=$5
    S:conflictPolicy=$6
}
furnace { B:adjustCookTime=true }
logging { B:logRegistration=true }
EOF
}

case "$scenario" in
    default) write_config minecraft:rotten_flesh minecraft:leather 1 0.1 200 skip
             EXPECTED_FP=$(fingerprint_for minecraft:rotten_flesh minecraft:leather 1 0.1 200 skip)
             [ "$EXPECTED_FP" = "$DEFAULT_FINGERPRINT" ]                  || fail "generated defaults are stale: BuildDefaults has $DEFAULT_FINGERPRINT, the recipe hashes to $EXPECTED_FP" ;;
    custom)  write_config minecraft:rotten_flesh minecraft:leather 3 0.35 60 skip
             EXPECTED_FP=$(fingerprint_for minecraft:rotten_flesh minecraft:leather 3 0.35 60 skip) ;;
    vanilla) write_config minecraft:rotten_flesh minecraft:leather 3 0.35 60 skip
             EXPECTED_FP=$(fingerprint_for minecraft:rotten_flesh minecraft:leather 3 0.35 60 skip) ;;
    reload)  write_config minecraft:rotten_flesh minecraft:leather 1 0.1 200 skip
             EXPECTED_FP=$(fingerprint_for minecraft:rotten_flesh minecraft:leather 1 0.1 200 skip) ;;
    skip)    write_config minecraft:beef minecraft:leather 1 0.1 200 skip
             EXPECTED_FP=$(fingerprint_for minecraft:beef minecraft:leather 1 0.1 200 skip) ;;
    replace) write_config minecraft:beef minecraft:leather 1 0.1 200 replace
             EXPECTED_FP=$(fingerprint_for minecraft:beef minecraft:leather 1 0.1 200 replace) ;;
    *) fail "unknown scenario '$scenario'" ;;
esac

: > "$DIR/console.probe.log"
# exec so BOOT_PID is the java process itself; killing a subshell would orphan the server and leave
# the next scenario fighting a stale world and ports.
( cd "$DIR" && exec "$JAVA" -Xmx1200M -jar "forge-$forge-universal.jar" nogui > console.probe.log 2>&1 ) &
BOOT_PID=$!
for i in $(seq 1 240); do
    grep -q 'Done (' "$DIR/console.probe.log" 2>/dev/null && break
    kill -0 "$BOOT_PID" 2>/dev/null || { tail -25 "$DIR/console.probe.log"; fail "server exited before Done"; }
    sleep 1
done
grep -q 'Done (' "$DIR/console.probe.log" || fail "server did not reach Done"
grep -q "RCON running on .*:$PROBE_RPORT" "$DIR/console.probe.log" \
    || fail "RCON did not bind $PROBE_RPORT (another server may own it)"
say "boot reached Done (game $PROBE_PORT, rcon $PROBE_RPORT)"
# No player is online, so keep the probe area inside the spawn chunks the server holds loaded;
# otherwise /testforblock starts reporting "outside of the world" part way through a run.
rcon "setworldspawn 2 4 2" > /dev/null
wait_world_ready 90 || fail "the world never accepted block edits"

rcon "rottenleather status" > "$OUT/status-$scenario.txt" 2>&1
grep -q "fingerprint=$EXPECTED_FP" "$OUT/status-$scenario.txt"     || fail "the running recipe does not match the config the probe wrote"
say "status: $(tail -1 "$OUT/status-$scenario.txt")"

case "$scenario" in
    default)
        grep -q 'registered=true' "$OUT/status-$scenario.txt" || fail "default recipe not registered"
        grep -q 'runtimeXp=0.1' "$OUT/status-$scenario.txt" || fail "configured XP not in Forge's map"
        smelt_and_measure 2 minecraft:rotten_flesh minecraft:leather 1 vanilla 240
        ;;
    custom)
        grep -q 'registered=true' "$OUT/status-$scenario.txt" || fail "custom recipe not registered"
        grep -q 'runtimeXp=0.35' "$OUT/status-$scenario.txt" || fail "configured XP not in Forge's map"
        if grep -q 'customCookTime=false' "$OUT/status-$scenario.txt"; then
            grep -q 'fixed at 200 ticks' "$DIR/console.probe.log" \
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
        grep -q 'runtimeXp=0.1' "$OUT/status-$scenario.txt" || fail "default XP not in Forge's map"
        write_config minecraft:rotten_flesh minecraft:leather 3 0.35 80 skip
        rcon "rottenleather reload" > "$OUT/reload-$scenario.txt" 2>&1
        RELOAD_FP=$(fingerprint_for minecraft:rotten_flesh minecraft:leather 3 0.35 80 skip)
        grep -q "fingerprint=$RELOAD_FP" "$OUT/reload-$scenario.txt"             || fail "reload registered a different recipe than the config describes"
        grep -q 'ticks=80' "$OUT/reload-$scenario.txt" || fail "reload did not apply ticks=80"
        grep -q 'runtimeXp=0.35' "$OUT/reload-$scenario.txt" || fail "reload did not apply XP=0.35"
        grep -q 'registered=true' "$OUT/reload-$scenario.txt" || fail "recipe missing after reload"
        if grep -q 'customCookTime=false' "$OUT/reload-$scenario.txt"; then
            grep -q 'fixed at 200 ticks' "$DIR/console.probe.log" \
                || fail "custom cook time unsupported here but the documented warning is missing"
            smelt_and_measure 2 minecraft:rotten_flesh minecraft:leather 3 vanilla 240
        else
            smelt_and_measure 2 minecraft:rotten_flesh minecraft:leather 3 custom 240
        fi
        ;;
    skip)
        grep -q 'install=KEEP_EXISTING' "$OUT/status-$scenario.txt" || fail "skip did not keep the vanilla beef recipe"
        grep -q 'registered=false' "$OUT/status-$scenario.txt" || fail "skip reports its recipe as registered"
        smelt_and_measure 2 minecraft:beef minecraft:cooked_beef 1 vanilla 240
        chest_lacks 2 4 2 minecraft:leather || fail "skip produced leather instead of vanilla cooked beef"
        ;;
    replace)
        if grep -q 'replaceRecipe=false' "$OUT/status-$scenario.txt"; then
            grep -q 'not available on MC' "$DIR/console.probe.log" \
                || fail "replace unsupported here but the documented warning is missing"
            smelt_and_measure 2 minecraft:beef minecraft:cooked_beef 1 vanilla 240
            say "replace unsupported on this version: warning present, vanilla recipe preserved"
        else
            grep -q 'registered=true' "$OUT/status-$scenario.txt" || fail "replace did not register"
            smelt_and_measure 2 minecraft:beef minecraft:leather 1 vanilla 240
        fi
        ;;
esac

grep -q 'cook-time adjustment failed' "$DIR/console.probe.log" && fail "cook-time adjuster logged a failure"
if grep -Eq '^[[:space:]]+at com\.rottenleather' "$DIR/console.probe.log"; then
    fail "mod stack trace in the server console"
fi
if grep -Eq 'NoSuchField|NoSuchMethod|ClassNotFound|NoClassDefFound' "$DIR/console.probe.log"; then
    fail "reflection/classloading error in the server console"
fi

say "PASS $mc $scenario"
