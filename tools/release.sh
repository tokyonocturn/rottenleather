#!/usr/bin/env bash
# Tags the current commit and publishes a GitHub release with every built jar.
# Requires an authenticated gh CLI and a repository remote. Run tools/build-all.sh first.
set -euo pipefail

VERSION="1.0.1"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

[ -d dist ] || { echo "dist/ is missing; run tools/build-all.sh first" >&2; exit 1; }
shopt -s nullglob
jars=(dist/*.jar)
[ "${#jars[@]}" -gt 0 ] || { echo "dist/ has no jars" >&2; exit 1; }

if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo "not a git repository" >&2
    exit 1
fi

if git rev-parse "v$VERSION" > /dev/null 2>&1; then
    echo "tag v$VERSION already exists" >&2
    exit 1
fi

git tag -a "v$VERSION" -m "Rotten Flesh to Leather $VERSION"
git push origin "v$VERSION"

gh release create "v$VERSION" "${jars[@]}" \
    --title "Rotten Flesh to Leather $VERSION" \
    --notes-file CHANGELOG.md

echo "released v$VERSION with ${#jars[@]} jars"
