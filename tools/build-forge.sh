#!/usr/bin/env bash
# Builds every Forge release and runs both verification suites for each one.
#
# ForgeGradle 2.x cannot run on Gradle 5+, so three Gradle generations are needed:
#   1.8.8/1.8.9/1.9      Gradle 2.14.1 + ForgeGradle 2.1
#   1.9.4-1.11.2         Gradle 3.5    + ForgeGradle 2.2
#   1.12-1.12.2          Gradle 4.10.3 + ForgeGradle 2.3
# Point GRADLE_2141 / GRADLE_35 / GRADLE_4103 at those distributions. When unset, the copies used by
# the neighbouring dupeguard project are reused, because that is what this machine already has.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Java/jdk1.8.0_503}"
LOG="$ROOT/logs"
mkdir -p "$LOG"

ORDER="${*:-1.12.2 1.8.8 1.8.9 1.9 1.9.4 1.10 1.10.2 1.11 1.11.2 1.12 1.12.1}"

gradle_version_for() {
  case "$1" in
    1.8.8|1.8.9|1.9) echo 2.14.1 ;;
    1.9.4|1.10|1.10.2|1.11|1.11.2) echo 3.5 ;;
    *) echo 4.10.3 ;;
  esac
}

gradle_bin() {
  local version="$1" candidate
  case "$version" in
    2.14.1) candidate="${GRADLE_2141:-}" ;;
    3.5) candidate="${GRADLE_35:-}" ;;
    *) candidate="${GRADLE_4103:-}" ;;
  esac
  if [ -n "$candidate" ]; then
    echo "$candidate/bin/gradle"
    return
  fi
  if [ -f "$ROOT/../dupeguard/tools/gradle-$version/bin/gradle" ]; then
    echo "$ROOT/../dupeguard/tools/gradle-$version/bin/gradle"
    return
  fi
  if [ -f "$ROOT/../dupe-mod/tools/gradle-$version/bin/gradle" ]; then
    echo "$ROOT/../dupe-mod/tools/gradle-$version/bin/gradle"
    return
  fi
  echo "gradle-$version/bin/gradle"
}

: > "$LOG/build-forge.log"
FAILED=""
for mc in $ORDER; do
  gv="$(gradle_version_for "$mc")"
  gb="$(gradle_bin "$gv")"
  echo "=== $mc (Gradle $gv) ===" | tee -a "$LOG/build-forge.log"
  if [ ! -f "$gb" ] && ! command -v "$gb" >/dev/null 2>&1; then
    echo "RESULT $mc: FAIL - no Gradle $gv distribution (set GRADLE_$gv)" | tee -a "$LOG/build-forge.log"
    FAILED="$FAILED $mc"
    continue
  fi
  if ( cd "$ROOT/forge/versions/$mc" && "$gb" build verifyCore verifyForge --no-daemon ) \
      > "$LOG/forge-$mc.log" 2>&1; then
    jar="$ROOT/forge/versions/$mc/build/libs/rottenleather-$mc-1.0.1.jar"
    core="$(grep -o 'CORE_SUITE pass=[0-9]* fail=[0-9]*' "$LOG/forge-$mc.log" | tail -1)"
    forge="$(grep -o 'FORGE_SUITE mc=[0-9.]* pass=[0-9]* fail=[0-9]*' "$LOG/forge-$mc.log" | tail -1)"
    if [ ! -f "$jar" ]; then
      echo "RESULT $mc: FAIL - no jar produced" | tee -a "$LOG/build-forge.log"
      FAILED="$FAILED $mc"
    else
      echo "RESULT $mc: PASS $(basename "$jar") ${core:-core-missing} ${forge:-forge-missing}" \
          | tee -a "$LOG/build-forge.log"
    fi
  else
    FAILED="$FAILED $mc"
    echo "RESULT $mc: FAIL (exit $?)" | tee -a "$LOG/build-forge.log"
    grep -E "error:|What went wrong|Could not (find|resolve)|FAIL " "$LOG/forge-$mc.log" | head -10 \
        | tee -a "$LOG/build-forge.log"
  fi
done

echo "SUMMARY failed:${FAILED:- none}" | tee -a "$LOG/build-forge.log"
[ -z "${FAILED:-}" ] || exit 1
echo "ALL FORGE BUILDS PASS" | tee -a "$LOG/build-forge.log"
