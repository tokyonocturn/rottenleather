#!/usr/bin/env bash
# Builds every artifact into dist/ and runs the shared core + per-version Forge suites.
# Probes (which boot real servers) are separate: tools/probe-all-forge.sh and tools/probe-bukkit.sh.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mkdir -p dist logs

status=0
echo "=== shared core ==="
bash tools/test-core.sh || status=1

echo "=== forge (all versions) ==="
bash tools/build-forge.sh || status=1

echo "=== bukkit ==="
bash tools/build-bukkit.sh || status=1

echo "=== proxies ==="
bash tools/build-proxies.sh || status=1

echo "=== collecting dist ==="
for jar in forge/versions/*/build/libs/rottenleather-*-1.0.0.jar; do
    case "$jar" in *-sources.jar) continue ;; esac
    cp -f "$jar" dist/
done

echo
echo "=== dist ==="
ls -1 dist
[ "$status" -eq 0 ] && echo "BUILD PASS" || echo "BUILD FAILED"
exit "$status"
