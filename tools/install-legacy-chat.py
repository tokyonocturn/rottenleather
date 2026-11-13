#!/usr/bin/env python3
"""Installs the genuine net.md-5 snapshot artifacts that legacy Spigot revisions need.

Spigot 1.9-1.12 declare a snapshot dependency on net.md-5:bungeecord-chat (with
net.md-5:bungeecord-parent as its parent), and Sonatype purged those snapshots. The same artifacts
are still served by SpigotMC's and PaperMC's public Maven groups, so this script downloads the
published files from there and installs them into the local Maven repository. Nothing is forged: the
jars and poms are the ones those projects published.

Usage: install-legacy-chat.py 1.9 [1.10 ...]
"""
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

GROUP = "net.md-5"
REPOS = [
    "https://hub.spigotmc.org/nexus/content/groups/public",
    "https://repo.papermc.io/repository/maven-public",
]


def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read()


def latest_snapshot(version, artifact):
    for repo in REPOS:
        try:
            raw = fetch("%s/%s/%s/%s-SNAPSHOT/maven-metadata.xml"
                        % (repo, GROUP.replace(".", "/"), artifact, version))
        except Exception:
            continue
        root = ET.fromstring(raw)
        for element in root.iter():
            if "}" in element.tag:
                element.tag = element.tag.split("}", 1)[1]
        snapshot = root.find("versioning/snapshot")
        if snapshot is None:
            continue
        stamp = snapshot.findtext("timestamp")
        build = snapshot.findtext("buildNumber")
        if stamp and build:
            return repo, stamp, build
    return None


def install(version, artifact, repo, stamp, build, repository, with_jar):
    target = repository / GROUP.replace(".", "/") / artifact / ("%s-SNAPSHOT" % version)
    target.mkdir(parents=True, exist_ok=True)
    unique = "%s-%s-%s" % (version, stamp, build)
    extensions = ["pom"] + (["jar"] if with_jar else [])
    for ext in extensions:
        url = "%s/%s/%s/%s-SNAPSHOT/%s-%s.%s" % (
            repo, GROUP.replace(".", "/"), artifact, version, artifact, unique, ext)
        data = fetch(url)
        (target / ("%s-%s-SNAPSHOT.%s" % (artifact, version, ext))).write_bytes(data)
        print("  %s.%s (%d bytes)" % (artifact, ext, len(data)))
    updated = re.sub(r"\D", "", stamp)[:14]
    versions = "".join(
        "      <snapshotVersion><extension>%s</extension><value>%s-SNAPSHOT</value>"
        "<updated>%s</updated></snapshotVersion>\n" % (ext, version, updated)
        for ext in extensions)
    metadata = """<metadata>
  <groupId>{group}</groupId>
  <artifactId>{artifact}</artifactId>
  <version>{version}-SNAPSHOT</version>
  <versioning>
    <snapshot><localCopy>true</localCopy></snapshot>
    <lastUpdated>{updated}</lastUpdated>
    <snapshotVersions>
{versions}    </snapshotVersions>
  </versioning>
</metadata>
""".format(group=GROUP, artifact=artifact, version=version, updated=updated, versions=versions)
    (target / "maven-metadata-local.xml").write_text(metadata, encoding="utf-8", newline="\n")
    for stale in list(target.glob("*.lastUpdated")) + list(target.glob("resolver-status.properties")):
        stale.unlink()


def main():
    versions = sys.argv[1:]
    if not versions:
        raise SystemExit("usage: install-legacy-chat.py <mcversion> [...]")
    repository = Path(os.environ.get("M2_REPO", str(Path.home() / ".m2" / "repository")))
    failed = []
    for version in versions:
        print("%s: installing net.md-5 snapshots" % version)
        ok = True
        for artifact, with_jar in (("bungeecord-parent", False), ("bungeecord-chat", True)):
            found = latest_snapshot(version, artifact)
            if not found:
                print("  %s:%s:%s-SNAPSHOT is not published by SpigotMC or PaperMC" % (GROUP, artifact, version))
                ok = False
                continue
            install(version, artifact, *found, repository=repository, with_jar=with_jar)
        if not ok:
            failed.append(version)
    if failed:
        raise SystemExit("incomplete artifacts for: %s" % ", ".join(failed))


if __name__ == "__main__":
    main()
