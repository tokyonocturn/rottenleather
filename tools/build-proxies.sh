#!/usr/bin/env bash
# Builds the two proxy modules against the APIs shipped inside the proxy servers themselves.
# BungeeCord and Waterfall share the BungeeCord API; Velocity has its own. Neither proxy can run a
# furnace, so both modules implement the network recipe-consistency bridge instead.
set -ueo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK21="/c/Program Files/Java/jdk-21.0.12"
JAVAC="$JDK21/bin/javac"
JAR="$JDK21/bin/jar"
LIB="$ROOT/libs/proxy"
DIST="$ROOT/dist"
mkdir -p "$LIB" "$DIST"

BUNGEE="$LIB/BungeeCord.jar"
if [ ! -f "$BUNGEE" ]; then
    echo "downloading BungeeCord (API + proxy)"
    curl -sSL -o "$BUNGEE" "https://ci.md-5.net/job/BungeeCord/lastSuccessfulBuild/artifact/bootstrap/target/BungeeCord.jar"
fi

# The modern BungeeCord bootstrap bundles the API as a module jar instead of exposing it on its own
# classpath, so extract it for compilation (the runtime jar below is the same distribution).
BUNGEE_LIBS="$LIB/bungee-libs"
if [ ! -d "$BUNGEE_LIBS" ]; then
    mkdir -p "$BUNGEE_LIBS"
    unzip -o -q "$BUNGEE" "META-INF/libraries/bungeecord-*.jar" -d "$LIB/bungee-extract"
    cp "$LIB"/bungee-extract/META-INF/libraries/bungeecord-*.jar "$BUNGEE_LIBS/"
    rm -rf "$LIB/bungee-extract"
fi

VELOCITY="$LIB/velocity.jar"
if [ ! -f "$VELOCITY" ]; then
    echo "downloading Velocity 3.5.1"
    url="$(curl -s "https://fill.papermc.io/v3/projects/velocity/versions/3.5.1/builds" \
        | python -c "import json,sys; b=json.load(sys.stdin)[0]; print(b['downloads']['server:default']['url'])")"
    curl -sSL -o "$VELOCITY" "$url"
fi

rm -rf "$ROOT/build/bungee" "$ROOT/build/velocity"
mkdir -p "$ROOT/build/bungee" "$ROOT/build/velocity"

echo "building BungeeCord/Waterfall module"
cd "$ROOT"
BUNGEE_CP="$(ls libs/proxy/bungee-libs/*.jar | tr '\n' ';')"
"$JAVAC" --release 8 -encoding UTF-8 -Xlint:-options -classpath "$BUNGEE_CP" \
    -d "$ROOT/build/bungee" \
    $(find "$ROOT/shared/src/main/java" "$ROOT/proxy/bungee/src/main/java" -name '*.java')
cp -r "$ROOT/proxy/bungee/src/main/resources/." "$ROOT/build/bungee/"
"$JAR" cf "$DIST/rottenleather-bungee-1.0.1.jar" -C "$ROOT/build/bungee" .

echo "building Velocity module"
"$JAVAC" --release 11 -encoding UTF-8 -Xlint:-options -classpath "libs/proxy/velocity.jar" \
    -d "$ROOT/build/velocity" \
    $(find "$ROOT/shared/src/main/java" "$ROOT/proxy/velocity/src/main/java" -name '*.java')
cp -r "$ROOT/proxy/velocity/src/main/resources/." "$ROOT/build/velocity/"
"$JAR" cf "$DIST/rottenleather-velocity-1.0.1.jar" -C "$ROOT/build/velocity" .

ls -la "$DIST"/rottenleather-bungee-1.0.1.jar "$DIST"/rottenleather-velocity-1.0.1.jar
