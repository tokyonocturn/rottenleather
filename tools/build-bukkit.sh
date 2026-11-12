#!/usr/bin/env bash
# Builds the single Bukkit/Spigot/Paper plugin jar. Compiled against the 1.8.8 API so the same jar
# loads on 1.8.8-1.12.2; the NMS bridge discovers the server's own revision at runtime.
set -ueo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Java/jdk1.8.0_503}"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"
API="$ROOT/libs/spigot-api-1.8.8.jar"
OUT="$ROOT/build/bukkit"
DIST="$ROOT/dist"

if [ ! -f "$API" ]; then
    mkdir -p "$ROOT/libs"
    curl -sSfL -o "$API" \
        "https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/1.8.8-R0.1-SNAPSHOT/spigot-api-1.8.8-R0.1-20160221.082514-43.jar"
fi

python "$ROOT/tools/generate.py" > /dev/null

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$DIST"

"$JAVAC" -encoding UTF-8 -source 1.8 -target 1.8 -Xlint:-options -d "$OUT/classes" \
    -classpath "$API" \
    $(find "$ROOT/shared/src/main/java" "$ROOT/bukkit/src/main/java" -name '*.java')

cp -r "$ROOT/bukkit/src/main/resources/." "$OUT/classes/"

"$JAR" cf "$DIST/rottenleather-bukkit-1.0.0.jar" -C "$OUT/classes" .

echo "built $DIST/rottenleather-bukkit-1.0.0.jar"
"$JAR" tf "$DIST/rottenleather-bukkit-1.0.0.jar" | grep -E "plugin.yml|config.yml|RottenLeatherPlugin.class|BuildDefaults.class"
