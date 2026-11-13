#!/usr/bin/env bash
# One-boot runtime probe: default-equivalent recipe through hoppers, custom output/XP/time, vanilla
# timing isolation, then conflictPolicy=skip against a vanilla recipe. It covers the gameplay claims
# in the compatibility matrix without the second boot the full probe uses for replace/restore.
# Usage: probe-forge-quick.sh <mc> <forge-id> [port] [rcon-port]
set -uo pipefail

mc="$1"
forge="$2"
port="${3:-25571}"
rport="${4:-25581}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Java/jdk1.8.0_503}"
JAVA="$JAVA_HOME/bin/java.exe"
DIR="$ROOT/runtime/$mc/server"
JAR="$ROOT/dist/rottenleather-$mc-1.0.2.jar"
OUT="$ROOT/logs/quick-$mc"
LOG="$ROOT/logs/probe-quick-$mc.log"
BOOT_PID=""
mkdir -p "$OUT" "$ROOT/logs"
: > "$LOG"
say() { echo "[$mc] $*" | tee -a "$LOG"; }
fail() { say "FAIL: $*"; exit 1; }
rcon() { python "$ROOT/tools/rcon.py" "$rport" rf "$@"; }
cleanup() {
    [ -n "${BOOT_PID:-}" ] || return 0
    rcon stop > /dev/null 2>&1 || true
    sleep 3
    kill "$BOOT_PID" 2>/dev/null || true
}
trap cleanup EXIT

[ -f "$JAR" ] || fail "missing $JAR (run tools/build-forge.sh $mc)"
[ -f "$DIR/forge-$forge-universal.jar" ] || fail "missing installed server for $mc"
mkdir -p "$DIR/mods" "$DIR/config"
rm -f "$DIR"/mods/*.jar
cp -f "$JAR" "$DIR/mods/"
rm -rf "$DIR/world" "$DIR/world_nether" "$DIR/world_the_end"
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
place_chain() { # x input
    rcon "setblock $1 8 $1 minecraft:chest 0 replace {Items:[{Slot:0b,id:\"$2\",Count:1b}]}" > /dev/null
    rcon "setblock $1 7 $1 minecraft:hopper 0 replace" > /dev/null
    rcon "setblock $1 6 $1 minecraft:furnace 2 replace {Items:[{Slot:1b,id:\"minecraft:coal\",Count:1b}]}" > /dev/null
    rcon "setblock $1 5 $1 minecraft:hopper 0 replace" > /dev/null
    rcon "setblock $1 4 $1 minecraft:chest 0 replace" > /dev/null
}
chest_has() { # x item count
    local count_part=""
    [ -n "${3:-}" ] && count_part=",Count:${3}b"
    rcon "testforblock $1 4 $1 minecraft:chest -1 {Items:[{Slot:0b,id:\"$2\"${count_part}}]}" | grep -q Successfully
}

write_config minecraft:rotten_flesh minecraft:leather 3 0.35 60 skip
: > "$DIR/console.quick.log"
( cd "$DIR" && "$JAVA" -Xmx1200M -jar "forge-$forge-universal.jar" nogui > console.quick.log 2>&1 ) &
BOOT_PID=$!
for i in $(seq 1 240); do
    grep -q 'Done (' "$DIR/console.quick.log" 2>/dev/null && break
    kill -0 "$BOOT_PID" 2>/dev/null || { tail -20 "$DIR/console.quick.log"; fail "server exited before Done"; }
    sleep 1
done
grep -q 'Done (' "$DIR/console.quick.log" || fail "server did not reach Done"
say "boot reached Done"
rcon "rottenleather status" > "$OUT/status.txt" 2>&1
grep -q 'registered=true' "$OUT/status.txt" || fail "recipe not registered"
grep -q 'runtimeXp=0.35' "$OUT/status.txt" || fail "XP not applied"

place_chain 4 minecraft:rotten_flesh
place_chain 6 minecraft:beef
sleep 5
if grep -q 'customCookTime=false' "$OUT/status.txt"; then
    chest_has 4 minecraft:leather 3 && fail "recipe finished in 5s although custom cook time is unsupported"
    say "custom cook time unsupported on this version, as documented"
    sleep 11
else
    chest_has 4 minecraft:leather 3 || fail "60-tick recipe did not produce 3 leather by 5s"
    chest_has 6 minecraft:cooked_beef && fail "beef smelted faster than vanilla"
    say "custom time + output count PASS"
    sleep 11
fi
chest_has 4 minecraft:leather 3 || fail "recipe did not produce 3 leather by 16s"
chest_has 6 minecraft:cooked_beef || fail "vanilla beef recipe did not finish at vanilla speed"
say "hopper automation + vanilla timing PASS"

write_config minecraft:beef minecraft:leather 1 0.1 200 skip
rcon "rottenleather reload" > "$OUT/reload-skip.txt"
grep -q 'install=KEEP_EXISTING' "$OUT/reload-skip.txt" || fail "skip did not keep the vanilla beef recipe"
place_chain 8 minecraft:beef
sleep 18
chest_has 8 minecraft:cooked_beef || fail "vanilla beef recipe did not survive skip"
say "conflictPolicy=skip PASS"

grep -q 'cook-time adjustment failed' "$DIR/console.quick.log" && fail "cook-time adjuster reported a failure"
if grep -Eq '^[[:space:]]+at com\.rottenleather' "$DIR/console.quick.log"; then
    fail "mod stack trace in console"
fi
say "PASS $mc quick runtime probe"
