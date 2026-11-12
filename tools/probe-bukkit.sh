#!/usr/bin/env bash
# Boots a real Paper server for one release and proves the shipped Bukkit jar:
#   A. default recipe: furnace produces leather; status reports nmsXp=0.1; hopper chain moves leather
#   B. restart with output-count=3, experience=0.35, smelt-time-ticks=60: count/time/XP honoured and
#      a beef furnace in the same world keeps vanilla timing; /rottenleather reload applies a new
#      XP + tick value live
#   C. restart with input-item=porkchop: the vanilla porkchop recipe is preserved (skip policy)
#
# 1.8-1.12 have no Bukkit API for per-recipe removal, so recipe-shape changes need a restart; that is
# tested here as a restart rather than pretended to work live.
#
# Usage: probe-bukkit.sh <mc> [server-port] [rcon-port]
set -uo pipefail

mc="$1"
port="${2:-25671}"
rport="${3:-25681}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Java/jdk1.8.0_503}"
JAVA="$JAVA_HOME/bin/java.exe"
DIR="$ROOT/runtime/bukkit-$mc/server"
JAR="$ROOT/dist/rottenleather-bukkit-1.0.0.jar"
PAPER="$ROOT/../livedaw-mc/automation/jars/paper-$mc.jar"
LOG="$ROOT/logs/probe-bukkit-$mc.log"
OUT="$ROOT/logs/probe-bukkit-$mc"
FINGERPRINT="$(grep -oE '[0-9a-f]{64}' "$ROOT/shared/src/main/java/com/rottenleather/core/BuildDefaults.java" | head -1)"
BOOT_PID=""
cleanup() {
    if [ -n "${BOOT_PID:-}" ] && kill -0 "$BOOT_PID" 2>/dev/null; then
        rcon stop > /dev/null 2>&1 || true
        sleep 3
        kill "$BOOT_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

mkdir -p "$OUT" "$ROOT/logs"
: > "$LOG"
say() { echo "[bukkit-$mc] $*" | tee -a "$LOG"; }
fail() { say "FAIL: $*"; exit 1; }
rcon() { python "$ROOT/tools/rcon.py" "$rport" rf "$@"; }

[ -f "$JAR" ] || fail "missing $JAR (run tools/build-bukkit.sh)"
[ -f "$PAPER" ] || fail "missing Paper jar $PAPER"

mkdir -p "$DIR/plugins"
cp -f "$PAPER" "$DIR/server.jar"
rm -rf "$DIR/world" "$DIR/world_nether" "$DIR/world_the_end" "$DIR/plugins/RottenFleshToLeather" "$DIR/logs"

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
server-port=$port
enable-rcon=true
rcon.port=$rport
rcon.password=rf
motd=rottenleather bukkit probe
EOF
printf 'eula=true\n' > "$DIR/eula.txt"

write_plugin_config() { # input output count xp ticks policy
    mkdir -p "$DIR/plugins/RottenFleshToLeather"
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

boot() { # tag
    local tag="$1" i
    mkdir -p "$DIR/plugins/RottenFleshToLeather"
    cp -f "$JAR" "$DIR/plugins/"
    : > "$DIR/console.$tag.log"
    ( cd "$DIR" && "$JAVA" -Xmx1200M -Xms512M -jar server.jar nogui > "console.$tag.log" 2>&1 ) &
    BOOT_PID=$!
    for i in $(seq 1 300); do
        grep -q 'Done (' "$DIR/console.$tag.log" 2>/dev/null && { say "boot $tag reached Done"; return 0; }
        kill -0 "$BOOT_PID" 2>/dev/null || { say "boot $tag exited before Done"; tail -25 "$DIR/console.$tag.log"; return 1; }
        sleep 1
    done
    say "boot $tag timed out"; return 1
}

shutdown() {
    local i
    rcon stop > /dev/null 2>&1 || true
    for i in $(seq 1 60); do
        kill -0 "$BOOT_PID" 2>/dev/null || return 0
        sleep 1
    done
    kill "$BOOT_PID" 2>/dev/null || true
    sleep 2
}

# place_chain <x> <input-id> <input-count>
# input chest -> hopper -> furnace -> hopper -> output chest, all above the same x/z.
# Feeding through a hopper matches how the recipe is used in game and makes every assertion a read of
# a chest that holds only the expected output stack.
place_chain() {
    rcon "setblock $1 8 $1 minecraft:chest 0 replace {Items:[{Slot:0b,id:\"$2\",Count:${3}b}]}" > /dev/null
    rcon "setblock $1 7 $1 minecraft:hopper 0 replace" > /dev/null
    rcon "setblock $1 6 $1 minecraft:furnace 2 replace {Items:[{Slot:1b,id:\"minecraft:coal\",Count:1b}]}" > /dev/null
    rcon "setblock $1 5 $1 minecraft:hopper 0 replace" > /dev/null
    rcon "setblock $1 4 $1 minecraft:chest 0 replace" > /dev/null
}

# chest_has <x> <y> <z> <item> [count]
chest_has() {
    local count_part="" out
    if [ -n "${5:-}" ]; then count_part=",Count:${5}b"; fi
    out=$(rcon "testforblock $1 $2 $3 minecraft:chest -1 {Items:[{Slot:0b,id:\"$4\"${count_part}}]}")
    case "$out" in
        *Successfully*) return 0 ;;
        *) echo "  check failed: chest $1,$2,$3 $4 x${5:-1} -> $out" >> "$OUT/checks.txt"; return 1 ;;
    esac
}

# block_has <x> <y> <z> <block> <slot> <item> [count]
block_has() {
    local count_part="" out
    if [ -n "${7:-}" ]; then count_part=",Count:${7}b"; fi
    out=$(rcon "testforblock $1 $2 $3 $4 -1 {Items:[{Slot:${5}b,id:\"$6\"${count_part}}]}")
    case "$out" in
        *Successfully*) return 0 ;;
        *) echo "  check failed: $1,$2,$3 $6 x${7:-1} -> $out" >> "$OUT/checks.txt"; return 1 ;;
    esac
}

# ------------------------------------------------------------------ boot 1: default
write_plugin_config minecraft:rotten_flesh minecraft:leather 1 0.1 200 skip
say "boot 1: default recipe + hopper automation"
boot 1 || fail "Paper $mc did not start"
rcon "rottenleather status" > "$OUT/status-default.txt" 2>&1
grep -q 'registered=true' "$OUT/status-default.txt" || fail "recipe not registered: $(cat "$OUT/status-default.txt")"
grep -q 'nmsXp=0.1' "$OUT/status-default.txt" || fail "XP not registered: $(cat "$OUT/status-default.txt")"
grep -q "fingerprint=$FINGERPRINT" "$OUT/status-default.txt" || fail "fingerprint mismatch"

rcon "setblock 2 8 2 minecraft:chest 0 replace {Items:[{Slot:0b,id:\"minecraft:rotten_flesh\",Count:4b}]}" > /dev/null
rcon "setblock 2 7 2 minecraft:hopper 0 replace" > /dev/null
rcon "setblock 2 6 2 minecraft:furnace 2 replace {Items:[{Slot:1b,id:\"minecraft:coal\",Count:2b}]}" > /dev/null
rcon "setblock 2 5 2 minecraft:hopper 0 replace" > /dev/null
rcon "setblock 2 4 2 minecraft:chest 0 replace" > /dev/null
say "waiting 28s for hopper-fed smelting"
sleep 28
block_has 2 4 2 minecraft:chest 0 minecraft:leather || fail "hopper output chest has no leather"
say "hopper automation PASS"
shutdown

# ------------------------------------------------------------------ boot 2: custom shape + reload
write_plugin_config minecraft:rotten_flesh minecraft:leather 3 0.35 60 skip
say "boot 2: output-count=3, XP=0.35, 60 ticks"
boot 2 || fail "second Paper boot did not start"
rcon "rottenleather status" > "$OUT/status-custom.txt" 2>&1
grep -q 'ticks=60' "$OUT/status-custom.txt" || fail "ticks=60 not applied"
grep -q 'nmsXp=0.35' "$OUT/status-custom.txt" || fail "XP=0.35 not applied"

place_chain 4 minecraft:rotten_flesh 1
place_chain 6 minecraft:beef 1
sleep 2
chest_has 4 4 4 minecraft:leather && fail "60-tick recipe finished after 2s"
say "60-tick recipe not finished at 2s (correct)"
sleep 3
chest_has 4 4 4 minecraft:leather 3 || fail "custom time/count did not produce 3 leather by 5s"
say "custom time/count PASS"
chest_has 6 4 6 minecraft:cooked_beef && fail "beef smelted faster than vanilla"
sleep 11
chest_has 6 4 6 minecraft:cooked_beef || fail "beef did not smelt at vanilla speed"
say "vanilla timing preserved PASS"

# Same item shape, new XP and tick value: reload must apply both live.
write_plugin_config minecraft:rotten_flesh minecraft:leather 3 0.25 80 skip
rcon "rottenleather reload" > "$OUT/reload-live.txt" 2>&1
grep -q 'ticks=80' "$OUT/reload-live.txt" || fail "reload did not apply ticks=80: $(cat "$OUT/reload-live.txt")"
rcon "rottenleather status" > "$OUT/status-reload.txt" 2>&1
grep -q 'nmsXp=0.25' "$OUT/status-reload.txt" || fail "reload did not apply XP=0.25"
place_chain 7 minecraft:rotten_flesh 1
sleep 2
chest_has 7 4 7 minecraft:leather && fail "80-tick recipe finished after 2s"
sleep 5
chest_has 7 4 7 minecraft:leather 3 || fail "80-tick recipe did not finish by 7s"
say "live reload of XP + cook time PASS"
shutdown

# ------------------------------------------------------------------ boot 3: conflict skip
write_plugin_config minecraft:porkchop minecraft:leather 1 0.1 200 skip
say "boot 3: conflict skip with a vanilla porkchop recipe"
boot 3 || fail "third Paper boot did not start"
rcon "rottenleather status" > "$OUT/status-conflict.txt" 2>&1
grep -q 'install=KEEP_EXISTING' "$OUT/status-conflict.txt" || fail "skip did not keep vanilla recipe: $(cat "$OUT/status-conflict.txt")"
place_chain 8 minecraft:porkchop 1
sleep 18
chest_has 8 4 8 minecraft:cooked_porkchop || fail "vanilla porkchop recipe was not preserved"
say "conflictPolicy=skip PASS"
shutdown

cat "$DIR/console.1.log" "$DIR/console.2.log" "$DIR/console.3.log" > "$OUT/console.log"
grep -q "Rotten Flesh to Leather 1.0.0 active" "$OUT/console.log" || fail "no activation line"
grep -q 'Rotten Flesh to Leather: registered' "$OUT/console.log" || fail "no registration line"
if grep -Eq '^[[:space:]]+at com\.rottenleather' "$OUT/console.log"; then
    fail "plugin stack trace in console.log"
fi
grep -q 'cook-time API not available' "$OUT/console.log" && fail "NMS cook-time bridge unavailable on this server"
grep -q 'recipe experience API not available' "$OUT/console.log" && fail "NMS experience bridge unavailable on this server"

say "PASS bukkit-$mc (recipe, XP, hopper, custom time/count, live reload, conflict skip)"
