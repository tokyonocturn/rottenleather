#!/usr/bin/env bash
# One Spigot runtime scenario, on a fresh JVM and a fresh world.
#
# Uses the genuine BuildTools-built server at runtime/spigot/spigot-<mc>.jar and asserts the server
# reports itself as Spigot at that exact Minecraft version before any scenario assertion runs.
#
# Usage: probe-spigot.sh <mc> <scenario>
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mc="$1"
scenario="$2"
SERVER_JAR="$ROOT/runtime/spigot/spigot-$mc.jar"
[ -f "$SERVER_JAR" ] || { echo "[spigot-$mc/$scenario] missing $SERVER_JAR (build it with tools/build-spigot.sh)"; exit 1; }
SERVER_KIND=spigot SERVER_JAR="$SERVER_JAR" exec bash "$ROOT/tools/probe-bukkit.sh" "$mc" "$scenario"
