#!/usr/bin/env bash
# Compiles and runs the shared-core suite with the Java 8 toolchain that the 1.8-1.12.2 modules use.
set -ueo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/c/Program Files/Java/jdk1.8.0_503}"
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"
OUT="$ROOT/build/core-test"

rm -rf "$OUT"
mkdir -p "$OUT"

python "$ROOT/tools/generate.py" --check

"$JAVAC" -encoding UTF-8 -source 1.8 -target 1.8 -d "$OUT" \
    $(find "$ROOT/shared/src/main/java" -name '*.java') \
    $(find "$ROOT/shared/src/test/java" -name '*.java')

"$JAVA" -cp "$OUT" com.rottenleather.test.CoreSuite
