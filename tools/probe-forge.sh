#!/usr/bin/env bash
# Boots a real Forge dedicated server for one release and proves the shipped jar's behaviour:
#   A. default recipe: chest -> hopper -> furnace -> hopper -> chest produces exactly 1 leather
#   B. custom config: outputCount=3, xp=0.35, ticks=60 finishes in ~3s, produces 3 leather, and a beef
#      furnace in the same world keeps vanilla 200-tick timing
#   C. conflictPolicy=skip keeps a vanilla porkchop->cooked_porkchop recipe
#   D. conflictPolicy=replace takes the input over
#   E. reloading back to skip restores the displaced vanilla recipe
#
# /blockdata cannot read NBT in 1.12, so every assertion reads an output chest with /testforblock,
# which exists in 1.8-1.12 and matches the supplied tags exactly. Each furnace therefore feeds a
# hopper into a chest that contains only the expected output stack.
#
# Usage: probe-forge.sh <mc> <forge-id> [server-port] [rcon-port]
set -uo pipefail

mc="$1"
forge="$2"
port="${3:-25571}"
rport="${4:-25581}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Java/jdk1.8.0_503}"
JAVA="$JAVA_HOME/bin/java.exe"
DIR="$ROOT/runtime/$mc/server"
JAR="$ROOT/forge/versions/$mc/build/libs/rottenleather-$mc-1.0.0.jar"
LOG="$ROOT/logs/probe-forge-$mc.log"
OUT="$ROOT/logs/probe-forge-$mc"
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
say() { echo "[$mc] $*" | tee -a "$LOG"; }
fail() { say "FAIL: $*"; exit 1; }
rcon() { python "$ROOT/tools/rcon.py" "$rport" rf "$@"; }

[ -f "$JAR" ] || fail "missing built jar $JAR (run tools/build-forge.sh $mc)"
[ -n "$FINGERPRINT" ] || fail "could not read SPEC_FINGERPRINT"

# ------------------------------------------------------------------ server install (cached)
if [ ! -f "$DIR/forge-$forge-universal.jar" ]; then
    mkdir -p "$DIR"
    if [ -f "$ROOT/../dupeguard/runtime/$mc/server/forge-$forge-universal.jar" ]; then
        say "reusing the already-installed Forge server from ../dupeguard/runtime/$mc"
        cp -r "$ROOT/../dupeguard/runtime/$mc/server/." "$DIR/"
        rm -rf "$DIR"/world "$DIR"/world_nether "$DIR"/world_the_end "$DIR"/logs "$DIR"/mods/* "$DIR"/crash-reports
    else
        say "installing Forge $forge"
        ( cd "$DIR" && curl -sSfL -o installer.jar \
            "https://maven.minecraftforge.net/net/minecraftforge/forge/$forge/forge-$forge-installer.jar" \
            && "$JAVA" -jar installer.jar --installServer "$DIR" > install.log 2>&1 )
    fi
fi
[ -f "$DIR/forge-$forge-universal.jar" ] || fail "no forge-$forge-universal.jar in $DIR"

mkdir -p "$DIR/mods"
rm -f "$DIR"/mods/*.jar
cp -f "$JAR" "$DIR/mods/"
rm -rf "$DIR/world" "$DIR/world_nether" "$DIR/world_the_end" "$DIR/crash-reports"

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
mkdir -p "$DIR/config"

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
furnace {
    B:adjustCookTime=true
}
logging {
    B:logRegistration=true
}
EOF
}

boot() { # tag
    local tag="$1" i
    : > "$DIR/console.$tag.log"
    ( cd "$DIR" && "$JAVA" -Xmx1200M -Xms512M -jar "forge-$forge-universal.jar" nogui > "console.$tag.log" 2>&1 ) &
    BOOT_PID=$!
    for i in $(seq 1 240); do
        grep -q 'Done (' "$DIR/console.$tag.log" 2>/dev/null && { say "boot $tag reached Done"; return 0; }
        kill -0 "$BOOT_PID" 2>/dev/null || { say "boot $tag exited before Done"; tail -25 "$DIR/console.$tag.log"; return 1; }
        sleep 1
    done
    say "boot $tag timed out after 240s"
    return 1
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
# Feeding through a hopper keeps the test identical to how a player or farm actually uses the recipe
# and avoids relying on command-placed furnace inventory NBT being read back by /testforblock.
place_chain() {
    rcon "setblock $1 8 $1 minecraft:chest 0 replace {Items:[{Slot:0b,id:\"$2\",Count:${3}b}]}" > /dev/null
    rcon "setblock $1 7 $1 minecraft:hopper 0 replace" > /dev/null
    rcon "setblock $1 6 $1 minecraft:furnace 2 replace {Items:[{Slot:1b,id:\"minecraft:coal\",Count:1b}]}" > /dev/null
    rcon "setblock $1 5 $1 minecraft:hopper 0 replace" > /dev/null
    rcon "setblock $1 4 $1 minecraft:chest 0 replace" > /dev/null
}

# chest_has <x> <y> <z> <item> <count>
chest_has() {
    local out
    out=$(rcon "testforblock $1 $2 $3 minecraft:chest -1 {Items:[{Slot:0b,id:\"$4\",Count:${5}b}]}")
    case "$out" in
        *Successfully*) return 0 ;;
        *) echo "  check failed: chest $1,$2,$3 expected $4 x$5 -> $out" >> "$OUT/checks.txt"; return 1 ;;
    esac
}

# ------------------------------------------------------------------ boot 1: default behaviour
write_config minecraft:rotten_flesh minecraft:leather 1 0.1 200 skip
say "boot 1: default recipe through a hopper chain"
boot 1 || fail "server did not start"
rcon "rottenleather status" > "$OUT/status-default.txt" 2>&1
grep -q 'registered=true' "$OUT/status-default.txt" || fail "recipe not registered: $(cat "$OUT/status-default.txt")"
grep -q 'runtimeXp=0.1' "$OUT/status-default.txt" || fail "runtime XP is not 0.1: $(cat "$OUT/status-default.txt")"
grep -q "fingerprint=$FINGERPRINT" "$OUT/status-default.txt" || fail "fingerprint mismatch"

rcon "setblock 2 8 2 minecraft:chest 0 replace {Items:[{Slot:0b,id:\"minecraft:rotten_flesh\",Count:1b}]}" > /dev/null
rcon "setblock 2 7 2 minecraft:hopper 0 replace" > /dev/null
rcon "setblock 2 6 2 minecraft:furnace 2 replace {Items:[{Slot:1b,id:\"minecraft:coal\",Count:1b}]}" > /dev/null
rcon "setblock 2 5 2 minecraft:hopper 0 replace" > /dev/null
rcon "setblock 2 4 2 minecraft:chest 0 replace" > /dev/null
say "waiting 18s for hopper-fed smelting"
sleep 18
chest_has 2 4 2 minecraft:leather 1 || fail "hopper output chest does not hold exactly 1 leather"
say "hopper automation PASS"
shutdown

# ------------------------------------------------------------------ boot 2: custom timing + conflicts
write_config minecraft:rotten_flesh minecraft:leather 3 0.35 60 skip
say "boot 2: custom output/XP/time and conflict policies"
boot 2 || fail "second boot did not start"
rcon "rottenleather status" > "$OUT/status-custom.txt" 2>&1
grep -q 'ticks=60' "$OUT/status-custom.txt" || fail "config ticks=60 not applied"
grep -q 'runtimeXp=0.35' "$OUT/status-custom.txt" || fail "config xp=0.35 not applied"

if grep -q 'customCookTime=false' "$OUT/status-custom.txt"; then
    # 1.8.x has no total-cook-time field; the documented behaviour is vanilla 200-tick timing.
    place_chain 4 minecraft:rotten_flesh 1
    sleep 5
    chest_has 4 4 4 minecraft:leather 3 && fail "recipe finished in 5s although custom cook time is unsupported"
    say "custom cook time unsupported on this version, as documented"
    sleep 11
    chest_has 4 4 4 minecraft:leather 3 || fail "vanilla-timed recipe did not finish by 16s"
    say "vanilla 200-tick timing PASS"
else
    place_chain 4 minecraft:rotten_flesh 1
    place_chain 6 minecraft:beef 1
    sleep 2
    chest_has 4 4 4 minecraft:leather 3 && fail "60-tick recipe finished and reached the chest after 2s"
    say "60-tick recipe not finished at 2s (correct)"
    sleep 3
    chest_has 4 4 4 minecraft:leather 3 || fail "60-tick recipe did not put 3 leather in the chest by 5s"
    say "custom time + output count PASS"
    chest_has 6 4 6 minecraft:cooked_beef 1 && fail "beef smelted faster than vanilla (furnace total was not restored)"
    say "other recipes keep vanilla timing at 5s (correct)"
    sleep 11
    chest_has 6 4 6 minecraft:cooked_beef 1 || fail "beef did not smelt at vanilla speed"
    say "vanilla beef recipe still works PASS"
fi

write_config minecraft:beef minecraft:leather 1 0.1 200 skip
rcon "rottenleather reload" > "$OUT/reload-skip.txt"
grep -q 'install=KEEP_EXISTING' "$OUT/reload-skip.txt" || fail "skip policy did not keep vanilla porkchop recipe: $(cat "$OUT/reload-skip.txt")"
place_chain 8 minecraft:beef 1
sleep 18
chest_has 8 4 8 minecraft:cooked_beef 1 || fail "vanilla beef recipe was not preserved under skip"
say "conflictPolicy=skip PASS"

if grep -q 'replaceRecipe=false' "$OUT/status-custom.txt"; then
    # 1.8.x Forge cannot replace an existing smelting entry; the documented behaviour is to warn and
    # keep the vanilla recipe instead of silently deleting it or claiming success.
    write_config minecraft:beef minecraft:leather 1 0.1 200 replace
    rcon "rottenleather reload" > "$OUT/reload-replace.txt"
    grep -q 'not available on MC' "$OUT/reload-replace.txt"         || fail "replace on a version without replace support did not warn: $(cat "$OUT/reload-replace.txt")"
    place_chain 10 minecraft:beef 1
    sleep 25
    chest_has 10 4 10 minecraft:cooked_beef 1 || fail "unsupported replace did not keep the vanilla beef recipe"
    say "conflictPolicy=replace unsupported on this version, warned and preserved (documented) PASS"
else
    write_config minecraft:beef minecraft:leather 1 0.1 200 replace
    rcon "rottenleather reload" > "$OUT/reload-replace.txt"
    grep -q 'registered=true' "$OUT/reload-replace.txt" || fail "replace policy did not register"
    place_chain 10 minecraft:beef 1
    sleep 25
    chest_has 10 4 10 minecraft:leather 1 || fail "replace policy did not take over the input"
    say "conflictPolicy=replace PASS"

    write_config minecraft:beef minecraft:leather 1 0.1 200 skip
    rcon "rottenleather reload" > "$OUT/reload-restore.txt"
    place_chain 12 minecraft:beef 1
    sleep 25
    chest_has 12 4 12 minecraft:cooked_beef 1 || fail "displaced vanilla recipe was not restored on reload"
    say "displaced vanilla recipe restored PASS"
fi

shutdown

# ------------------------------------------------------------------ log assertions
cat "$DIR/console.1.log" "$DIR/console.2.log" > "$OUT/console.log"
grep -q "Rotten Flesh to Leather 1.0.0 for MC $mc starting" "$OUT/console.log" || fail "no startup line"
grep -q 'registered minecraft:rotten_flesh -> minecraft:leather x1' "$OUT/console.log" || fail "no registration line"
grep -q 'kept the existing recipe' "$OUT/console.log" || fail "no conflict-skip log line"
if grep -Eq 'NoSuchField|NoSuchMethod|ClassNotFound|NoClassDefFound|InaccessibleObject' "$OUT/console.log"; then
    fail "reflection/classloading error in console.log"
fi
if grep -Eq '^[[:space:]]+at com\.rottenleather' "$OUT/console.log"; then
    fail "mod stack trace in console.log"
fi
grep -q 'cook-time adjustment failed' "$OUT/console.log" && fail "cook-time adjuster reported a failure"

say "PASS $mc (hopper, XP, custom time/count, conflicts, restore)"
