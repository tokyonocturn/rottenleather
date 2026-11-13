#!/usr/bin/env bash
# Builds genuine Spigot server jars with BuildTools, one revision at a time, into runtime/spigot/.
#
# BuildTools is distributed by SpigotMC and is the only way to obtain a real Spigot server jar; the
# jars are never redistributed by this repository. Legacy revisions (1.8-1.12.2) are compiled with
# the JDK 8 toolchain they expect.
#
# Usage: build-spigot.sh [mc ...]      (defaults to every 1.8-1.12.2 release)
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
JAVA8="${JAVA8:-/c/Program Files/Java/jdk1.8.0_503/bin/java}"
BT="$ROOT/runtime/buildtools/BuildTools.jar"
[ -f "$BT" ] || { echo "missing $BT; download it from https://hub.spigotmc.org/jenkins/job/BuildTools/" >&2; exit 1; }
mkdir -p "$ROOT/runtime/spigot" "$ROOT/logs" "$ROOT/runtime/git-home"
# Spigot's applyPatches.sh is a bash script; a Windows git with core.autocrlf=true checks it out
# with CRLF endings and bash then rejects it. Force LF for the BuildTools checkouts through a private
# global git config instead of touching the user's own git settings.
printf '[core]\n\tautocrlf = false\n\teol = lf\n[user]\n\tname = Spigot BuildTools\n\temail = buildtools@localhost\n' > "$ROOT/runtime/git-home/.gitconfig"
export GIT_CONFIG_GLOBAL="$ROOT/runtime/git-home/.gitconfig"
# applyPatches.sh runs `git am` inside the BuildTools checkouts; without an identity git refuses to
# create the patch commits, so supply one through the environment.
# Legacy Spigot revs depend on net.md-5:bungeecord-chat snapshot artifacts that Sonatype has purged;
# the same artifacts are still served by SpigotMC's and PaperMC's public Maven groups, so point the
# Maven run at those instead of a dead repository. Nothing is faked: the jars come from the projects
# that published them.
mkdir -p "$ROOT/runtime/git-home/.m2"
cat > "$ROOT/runtime/git-home/.m2/settings.xml" <<'SETTINGS'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <profiles>
    <profile>
      <id>legacy-spigot-repositories</id>
      <repositories>
        <repository>
          <id>spigot-public</id>
          <url>https://hub.spigotmc.org/nexus/content/groups/public/</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
        <repository>
          <id>papermc-public</id>
          <url>https://repo.papermc.io/repository/maven-public/</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>legacy-spigot-repositories</activeProfile>
  </activeProfiles>
</settings>
SETTINGS
export MAVEN_ARGS="-U"
export GIT_AUTHOR_NAME="Spigot BuildTools" GIT_AUTHOR_EMAIL="buildtools@localhost"
export GIT_COMMITTER_NAME="Spigot BuildTools" GIT_COMMITTER_EMAIL="buildtools@localhost"

# BuildTools reuses its checkouts between revisions and applies its patches with `git am`, which
# needs a committer identity and rejects CRLF shell scripts. Give every existing checkout an identity
# and LF line endings before each revision; BuildTools keeps the same trees across revisions.
prepare_legacy_checkouts() {
    local base="$1"
    find "$base" -name .git -type d 2>/dev/null | while read -r gitdir; do
        dir=$(dirname "$gitdir")
        git -C "$dir" config user.name "Spigot BuildTools"
        git -C "$dir" config user.email "buildtools@localhost"
        git -C "$dir" config core.autocrlf false
    done
    find "$base" -name '*.sh' -type f -exec sed -i 's/
$//' {} + 2>/dev/null || true
    # BuildTools runs the patch scripts with an environment that has no git identity, so `git am`
    # refuses to commit. Give the scripts an identity of their own; this survives the resets that
    # happen between patch rounds.
    find "$base" -name applyPatches.sh -type f 2>/dev/null | while read -r script; do
        grep -q 'GIT_AUTHOR_NAME' "$script" && continue
        sed -i '1a export GIT_AUTHOR_NAME="Spigot BuildTools" GIT_AUTHOR_EMAIL="buildtools@localhost" GIT_COMMITTER_NAME="Spigot BuildTools" GIT_COMMITTER_EMAIL="buildtools@localhost"' "$script"
    done
}

VERSIONS="${*:-1.8.8 1.8.9 1.9 1.9.4 1.10 1.10.2 1.11 1.11.2 1.12 1.12.1 1.12.2}"
failed=""
for mc in $VERSIONS; do
    out="$ROOT/runtime/spigot/spigot-$mc.jar"
    if [ -f "$out" ]; then
        echo "spigot $mc: already built ($(basename "$out"))"
        continue
    fi
    work="$ROOT/runtime/buildtools/work-$mc"
    mkdir -p "$work"
    # BuildTools reads user.name/user.email to build the GIT_COMMITTER_* environment it passes to the
    # patch scripts; it reads them in its working directory, so that directory needs to be a repo with
    # an identity or the exported name is empty and `git am` refuses to commit.
    if [ ! -d "$work/.git" ]; then
        git -C "$work" init -q
        git -C "$work" config user.name "Spigot BuildTools"
        git -C "$work" config user.email "buildtools@localhost"
    fi
    # BuildTools clones the Spigot repo itself with core.autocrlf=true, which turns applyPatches.sh
    # into a CRLF script that bash refuses to run. Pre-clone it with LF so BuildTools reuses this
    # checkout instead of creating a broken one.
    for repo in spigot:Spigot bukkit:Bukkit craftbukkit:CraftBukkit; do
        remote="${repo%%:*}"; dir="${repo##*:}"
        [ -d "$work/$dir/.git" ] && continue
        git -c core.autocrlf=false clone --quiet "https://hub.spigotmc.org/stash/scm/spigot/$remote.git" "$work/$dir" \
            || echo "spigot $mc: could not pre-clone $dir; BuildTools will try" >&2
    done
    prepare_legacy_checkouts "$work"
    log="$ROOT/logs/buildtools-$mc.log"
    echo "spigot $mc: building with BuildTools (log: logs/buildtools-$mc.log)"
    ( cd "$work" && HOME="$ROOT/runtime/git-home" USERPROFILE="$ROOT/runtime/git-home" \
        GIT_CONFIG_GLOBAL="$GIT_CONFIG_GLOBAL" "$JAVA8" -jar "$BT" --rev "$mc" --compile SPIGOT \
        --output-dir "$ROOT/runtime/spigot" --nogui > "$log" 2>&1 )
    rc=$?
    if [ $rc -eq 0 ] && [ -f "$out" ]; then
        echo "spigot $mc: BUILD PASS ($(stat -c %s "$out") bytes)"
        rm -rf "$work"
    else
        echo "spigot $mc: BUILD FAIL (rc=$rc, last lines below)"
        tail -12 "$log" | sed 's/^/    /'
        failed="$failed $mc"
    fi
done
[ -z "$failed" ] || { echo "failed to build:$failed" >&2; }
