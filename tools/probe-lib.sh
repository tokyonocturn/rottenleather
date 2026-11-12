#!/usr/bin/env bash
# Shared helpers for the runtime probes.
#
# Every wait is driven by observable server state (a block matching /testforblock, the server tick
# counter) rather than a fixed sleep, so a slow or loaded machine cannot turn a correct mod into a
# false failure. Timing assertions use /time query gametime, which advances in server ticks, so they
# measure the furnace, not the host's wall clock.

PROBE_TICKS=0
PROBE_PORT=""
PROBE_RPORT=""

say() { echo "[$PROBE_TAG] $*" | tee -a "$LOG"; }
fail() { say "FAIL: $*"; exit 1; }
rcon() { python "$ROOT/tools/rcon.py" "$RPORT" rf "$@"; }

# free_ports <count> -> newline-separated ports that were all bindable at the same moment, so the
# game port and the RCON port cannot collide with each other or with a stale server. RCON talking to
# a leftover instance used to look like a mod failure.
free_ports() {
    python - "$1" <<'PYEOF'
import socket, sys
wanted = int(sys.argv[1])
socks, ports = [], []
for port in range(25600, 26000):
    if len(ports) == wanted:
        break
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        s.bind(("0.0.0.0", port))
    except OSError:
        s.close()
        continue
    socks.append(s)
    ports.append(port)
print(" ".join(str(p) for p in ports))
for s in socks:
    s.close()
PYEOF
}

# assign_ports <game-base> <rcon-base> -> PROBE_PORT / PROBE_RPORT on free ports.
assign_ports() {
    local picked
    picked=$(free_ports 2)
    PROBE_PORT=$(echo "$picked" | cut -d' ' -f1)
    PROBE_RPORT=$(echo "$picked" | cut -d' ' -f2)
    [ -n "$PROBE_PORT" ] && [ -n "$PROBE_RPORT" ] || fail "could not find two free ports"
    say "using ports $PROBE_PORT/$PROBE_RPORT"
}

# fingerprint_for <input> <output> <count> <xp> <ticks> <policy>
# Reproduces RecipeSpec.canonical()/fingerprint() so a probe can prove the running server registered
# exactly the recipe its config file describes (and, for defaults, that code generation is in sync).
fingerprint_for() {
    python - "$1" "$2" "$3" "$4" "$5" "$6" <<'PYEOF'
import hashlib, struct, sys
inp, out, count, xp, ticks, policy = sys.argv[1:7]
bits = struct.unpack("<i", struct.pack("<f", float(xp)))[0]
canonical = ("v1|input=%s|inputCount=1|output=%s|outputCount=%d|xpBits=%d|ticks=%d|policy=%s"
             % (inp, out, int(count), bits, int(ticks), policy))
print(hashlib.sha256(canonical.encode("utf-8")).hexdigest())
PYEOF
}

# Server game time in ticks.
ticks_now() {
    local out
    out=$(rcon "time query gametime" 2>/dev/null)
    echo "$out" | grep -oE '[0-9]+' | tail -1
}

# block_matches <testforblock args...>  -> 0 when the block/tag matches
block_matches() {
    rcon "testforblock $*" 2>/dev/null | grep -q 'Successfully found'
}

# server_alive -> 0 while the server process started by the probe is still running
server_alive() {
    [ -n "${BOOT_PID:-}" ] && kill -0 "$BOOT_PID" 2>/dev/null
}


# wait_world_ready [timeout-seconds] -> 0 once the probe column accepts block edits.
# A freshly generated world can answer "outside of the world" for a moment after Done while the
# server finishes initialising its world border and spawn chunks; retrying is a wait, not a pass.
wait_world_ready() {
    local timeout=${1:-90} waited=0 out
    while [ "$waited" -lt "$timeout" ]; do
        out=$(rcon "setblock 2 9 2 minecraft:glass 0 replace" 2>&1 | tail -1)
        case "$out" in
            *"Block placed"*|*"Successfully placed"*|*"couldn't be placed"*)
                rcon "setblock 2 9 2 minecraft:air 0 replace" > /dev/null 2>&1
                return 0 ;;
        esac
        say "  world not ready for block edits yet ($out); retrying"
        sleep 2
        waited=$((waited + 2))
    done
    return 1
}

# wait_block <timeout-seconds> <testforblock args...>
wait_block() {
    local timeout=$1
    shift
    python "$ROOT/tools/rcon-wait.py" "$RPORT" rf "$timeout" contains "Successfully found" \
        "testforblock $*" > /dev/null
}

# wait_gone <timeout-seconds> <testforblock args...>  -> 0 once the tag no longer matches
wait_gone() {
    local timeout=$1
    shift
    python "$ROOT/tools/rcon-wait.py" "$RPORT" rf "$timeout" absent "Successfully found" \
        "testforblock $*" > /dev/null
}

# wait_chest <x> <y> <z> <item> <count> <timeout>  -> sets PROBE_TICKS to the tick count seen
wait_chest() {
    local x=$1 y=$2 z=$3 item=$4 count=$5 timeout=$6 out
    out=$(python "$ROOT/tools/rcon-wait.py" "$RPORT" rf "$timeout" contains "Successfully found" \
        "testforblock $x $y $z minecraft:chest -1 {Items:[{Slot:0b,id:\"$item\",Count:${count}b}]}")
    if [ "$out" = "OK" ]; then
        PROBE_TICKS=$(ticks_now)
        return 0
    fi
    say "  chest $x,$y,$z: $out"
    say "  furnace at $x,6,$x: $(rcon "testforblock $x 6 $x minecraft:furnace -1" 2>&1 | tail -1)"
    say "  furnace input: $(rcon "testforblock $x 6 $x minecraft:furnace -1 {Items:[{Slot:0b,id:\"$item_last\"}]}" 2>&1 | tail -1)"
    say "  chest above: $(rcon "testforblock $x 8 $x minecraft:chest -1 {Items:[{Slot:0b,id:\"$item_last\"}]}" 2>&1 | tail -1)"
    say "  gametime: $(ticks_now)"
    return 1
}

# chest_lacks <x> <y> <z> <item>
chest_lacks() {
    ! block_matches "$1" "$2" "$3" minecraft:chest -1 "{Items:[{Slot:0b,id:\"$4\"}]}"
}

# place_chain <x> <input-id> [count]
# input chest -> hopper -> furnace (1 coal) -> hopper -> output chest, stacked above x/z.
place_chain() {
    local x=$1 input=$2 count=${3:-1}
    say "  build chain at x=$x with $count x $input: $(rcon "setblock $x 8 $x minecraft:chest 0 replace {Items:[{Slot:0b,id:\"$input\",Count:${count}b}]}" | tail -1)"
    say "  hopper above: $(rcon "setblock $x 7 $x minecraft:hopper 0 replace" | tail -1)"
    say "  furnace: $(rcon "setblock $x 6 $x minecraft:furnace 2 replace {Items:[{Slot:1b,id:\"minecraft:coal\",Count:1b}]}" | tail -1)"
    say "  hopper below: $(rcon "setblock $x 5 $x minecraft:hopper 0 replace" | tail -1)"
    say "  output chest: $(rcon "setblock $x 4 $x minecraft:chest 0 replace" | tail -1)"
}

# smelt_and_measure <x> <input> <output-item> <output-count> <custom|vanilla> <timeout>
# Places the chain, waits until the furnace is actually burning, then until the output reaches the
# chest, and reports the server ticks between those two observable events in PROBE_TICKS.
smelt_and_measure() {
    local x=$1 input=$2 output=$3 count=$4 speed=$5 timeout=$6
    item_last=$input
    item_last_output=$output
    place_chain "$x" "$input"
    wait_block 30 "$x" 6 "$x" minecraft:lit_furnace -1 \
        || fail "furnace at $x did not ignite (fuel or hopper feed failed)"
    local start
    start=$(ticks_now)
    wait_chest "$x" 4 "$x" "$output" "$count" "$timeout" \
        || fail "chest $x,4,$x never received $output x$count"
    PROBE_TICKS=$((PROBE_TICKS - start))
    # Fuel is consumed at ignition; input and output are gone once the output has been pulled.
    # Assert each slot separately rather than matching an empty Items list.
    wait_gone 30 "$x" 6 "$x" minecraft:furnace -1 "{Items:[{Slot:1b,id:\"minecraft:coal\"}]}" \
        || fail "coal was not consumed by the furnace at $x"
    wait_gone 30 "$x" 6 "$x" minecraft:furnace -1 "{Items:[{Slot:0b,id:\"$input\"}]}" \
        || fail "input stack was not consumed by the furnace at $x"
    wait_gone 30 "$x" 6 "$x" minecraft:furnace -1 "{Items:[{Slot:2b,id:\"$output\"}]}" \
        || fail "output stack was not pulled out of the furnace at $x"
    case "$speed" in
        custom)
            [ "$PROBE_TICKS" -ge 30 ] && [ "$PROBE_TICKS" -le 150 ] \
                || fail "custom cook time took $PROBE_TICKS ticks, outside the 30-150 tick window" ;;
        vanilla)
            [ "$PROBE_TICKS" -ge 150 ] && [ "$PROBE_TICKS" -le 420 ] \
                || fail "vanilla timing took $PROBE_TICKS ticks, outside the 150-420 tick window" ;;
    esac
    say "smelt at x=$x: $PROBE_TICKS ticks ($speed), output $output x$count, fuel/input consumed"
}
