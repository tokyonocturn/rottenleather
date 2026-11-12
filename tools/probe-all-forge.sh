#!/usr/bin/env bash
# Runs probe-forge.sh for every version in forge/versions/versions.txt, sequentially (servers share
# the machine, so parallel boots distort tick timing and make vanilla-time assertions flaky).
# Writes logs/probe-all-forge.log and exits non-zero if any version fails.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="$ROOT/logs/probe-all-forge.log"
mkdir -p "$ROOT/logs"
: > "$LOG"
failures=0
while read -r mc forge _rest; do
    [ -n "$mc" ] || continue
    echo "=== $mc ($forge) ===" | tee -a "$LOG"
    if ( cd "$ROOT" && bash tools/probe-forge.sh "$mc" "$forge" 25571 25581 ) >> "$LOG" 2>&1; then
        echo "RESULT $mc: PASS" | tee -a "$LOG"
    else
        echo "RESULT $mc: FAIL" | tee -a "$LOG"
        failures=$((failures + 1))
    fi
done < "$ROOT/forge/versions/versions.txt"

if [ "$failures" -eq 0 ]; then
    echo "ALL FORGE RUNTIME PROBES PASS" | tee -a "$LOG"
    exit 0
fi
echo "$failures FORGE VERSION(S) FAILED" | tee -a "$LOG"
exit 1
