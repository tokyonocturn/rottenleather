#!/usr/bin/env bash
# Runs the whole runtime matrix sequentially, one fresh server per scenario, and records results.
#
#   logs/MATRIX.txt        "<platform> <mc> <scenario> PASS|FAIL" per line, appended as it goes
#   logs/probe-forge-<mc>-<scenario>.log
#   logs/probe-bukkit-<mc>-<scenario>.log
#
# Usage: run-matrix.sh [forge|bukkit|spigot|all]
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KIND="${1:-all}"
SUMMARY="$ROOT/logs/MATRIX.txt"
mkdir -p "$ROOT/logs"
[ "${MATRIX_APPEND:-0}" = "1" ] || : > "$SUMMARY"

FORGE_SCENARIOS="default custom vanilla reload skip replace"
BUKKIT_SCENARIOS="default custom vanilla reload skip"

run() { # platform mc scenario script [args...]
    local platform=$1 mc=$2 scenario=$3
    shift 3
    if "$@" > /dev/null 2>&1; then
        echo "$platform $mc $scenario PASS" | tee -a "$SUMMARY"
    else
        echo "$platform $mc $scenario FAIL" | tee -a "$SUMMARY"
    fi
}

if [ "$KIND" = "forge" ] || [ "$KIND" = "all" ]; then
    while read -r mc forge _rest; do
        [ -n "$mc" ] || continue
        case "$mc" in \#*) continue ;; esac
        for scenario in $FORGE_SCENARIOS; do
            run forge "$mc" "$scenario" bash "$ROOT/tools/probe-forge.sh" "$mc" "$forge" "$scenario"
        done
    done < "$ROOT/forge/versions/versions.txt"
fi

if [ "$KIND" = "bukkit" ] || [ "$KIND" = "all" ]; then
    for mc in 1.8.8 1.9.4 1.10.2 1.11.2 1.12 1.12.2; do
        for scenario in $BUKKIT_SCENARIOS; do
            run bukkit "$mc" "$scenario" bash "$ROOT/tools/probe-bukkit.sh" "$mc" "$scenario"
        done
    done
fi

if [ "$KIND" = "spigot" ] || [ "$KIND" = "all" ]; then
    for mc in 1.8.8 1.8.9 1.9 1.9.4 1.10 1.10.2 1.11 1.11.2 1.12 1.12.1 1.12.2; do
        [ -f "$ROOT/runtime/spigot/spigot-$mc.jar" ] || { echo "spigot $mc: no server jar, skipped"; continue; }
        for scenario in $BUKKIT_SCENARIOS; do
            run spigot "$mc" "$scenario" bash "$ROOT/tools/probe-spigot.sh" "$mc" "$scenario"
        done
    done
fi

echo "matrix complete"
