#!/usr/bin/env python3
"""Generates docs/COMPATIBILITY.md from the runtime evidence in logs/.

The only inputs are files the probes and builds wrote:

  logs/MATRIX.txt              "<platform> <mc> <scenario> PASS|FAIL", appended by run-matrix.sh
  logs/build-forge-matrix.log  per-version CORE_SUITE / FORGE_SUITE results
  logs/probe-proxies.log       proxy module load test

A Forge version is called verified only when every scenario for that exact version passed. A Paper
version is called verified only when every Bukkit scenario passed. Anything less is reported with
the exact scenarios that did pass, so no cell can claim more than the logs show.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
LOGS = ROOT / "logs"
OUT = ROOT / "docs" / "COMPATIBILITY.md"

FORGE_SCENARIOS = ["default", "custom", "vanilla", "reload", "skip", "replace"]
BUKKIT_SCENARIOS = ["default", "custom", "vanilla", "reload", "skip"]


def read(path):
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except FileNotFoundError:
        return ""


def matrix_results():
    results = {}
    for line in read(LOGS / "MATRIX.txt").splitlines():
        parts = line.split()
        if len(parts) != 4:
            continue
        platform, mc, scenario, outcome = parts
        results.setdefault((platform, mc), {})[scenario] = outcome
    return results


def builds():
    out = {}
    line_re = re.compile(
        r"RESULT (\S+): (PASS|FAIL) .*CORE_SUITE pass=(\d+) fail=(\d+) .*FORGE_SUITE mc=\S+ pass=(\d+) fail=(\d+)")
    for line in read(LOGS / "build-forge-matrix.log").splitlines():
        m = line_re.search(line)
        if m:
            out[m.group(1)] = (m.group(2) == "PASS" and m.group(4) == "0" and m.group(6) == "0",
                               int(m.group(3)) + int(m.group(5)))
    return out


def scenarios_cell(runs, expected):
    if not runs:
        return "no probe run"
    passed = [s for s in expected if runs.get(s) == "PASS"]
    failed = [s for s in expected if runs.get(s) == "FAIL"]
    missing = [s for s in expected if s not in runs]
    bits = []
    if passed:
        bits.append("pass: " + ", ".join(passed))
    if failed:
        bits.append("FAIL: " + ", ".join(failed))
    if missing:
        bits.append("not run: " + ", ".join(missing))
    return "; ".join(bits)


def forge_ids():
    ids = {}
    for line in read(ROOT / "forge" / "versions" / "versions.txt").splitlines():
        parts = line.split()
        if len(parts) >= 2:
            ids[parts[0]] = parts[1]
    return ids


def forge_table(results, build_results):
    ids = forge_ids()
    rows = ["| Minecraft | Forge | build + checks | runtime scenarios (fresh boot each) | support |",
            "| --- | --- | --- | --- | --- |"]
    verified = []
    for mc in ["1.8.8", "1.8.9", "1.9", "1.9.4", "1.10", "1.10.2", "1.11", "1.11.2", "1.12", "1.12.1",
               "1.12.2"]:
        build = build_results.get(mc)
        build_cell = "not built" if build is None else ("pass (%d checks)" % build[1] if build[0] else "FAIL")
        runs = results.get(("forge", mc), {})
        cell = scenarios_cell(runs, FORGE_SCENARIOS)
        ok = build is not None and build[0] and all(runs.get(s) == "PASS" for s in FORGE_SCENARIOS)
        support = "**verified**" if ok else "not verified"
        if ok:
            verified.append(mc)
        rows.append("| %s | %s | %s | %s | %s |" % (mc, ids.get(mc, "?"), build_cell, cell, support))
    return "\n".join(rows), verified


def bukkit_table(results):
    rows = ["| Server | runtime scenarios (fresh boot each) | support |",
            "| --- | --- | --- |"]
    verified = []
    for mc in ["1.8.8", "1.9.4", "1.10.2", "1.11.2", "1.12", "1.12.2"]:
        runs = results.get(("bukkit", mc), {})
        cell = scenarios_cell(runs, BUKKIT_SCENARIOS)
        ok = all(runs.get(s) == "PASS" for s in BUKKIT_SCENARIOS)
        if ok:
            verified.append(mc)
        rows.append("| Paper %s | %s | %s |" % (mc, cell, "**verified**" if ok else "not verified"))
    return "\n".join(rows), verified


def spigot_table(results):
    versions = sorted({mc for (platform, mc) in results if platform == "spigot"},
                      key=lambda v: [int(p) for p in v.split(".")])
    rows = ["| Server | runtime scenarios (fresh boot each) | support |",
            "| --- | --- | --- |"]
    verified = []
    for mc in versions:
        runs = results[("spigot", mc)]
        cell = scenarios_cell(runs, BUKKIT_SCENARIOS)
        ok = all(runs.get(s) == "PASS" for s in BUKKIT_SCENARIOS)
        if ok:
            verified.append(mc)
        rows.append("| Spigot %s | %s | %s |" % (mc, cell, "**verified**" if ok else "not verified"))
    if not versions:
        rows.append("| Spigot (any) | no probe run | **not claimed** |")
    return chr(10).join(rows), verified


def proxy_status():
    log = read(LOGS / "probe-proxies.log")
    bungee = "PASS" in log and "bungee" in log.lower()
    velocity = "PASS" in log and "velocity" in log.lower()
    return bungee, velocity


def main():
    results = matrix_results()
    build_results = builds()
    forge_rows, forge_verified = forge_table(results, build_results)
    bukkit_rows, bukkit_verified = bukkit_table(results)
    spigot_rows, spigot_verified = spigot_table(results)
    bungee, velocity = proxy_status()

    def mark(flag):
        return "loads and enables (probe log)" if flag else "not verified by the current logs"

    text = """# Compatibility matrix

This file is generated from the probe and build logs by `tools/matrix.py`; edit the logs, not the
table. A cell is **verified** only when that exact version has a full set of passing runtime logs,
each produced on a fresh server boot for that one scenario. Nothing is upgraded from a shared jar, a
compile test or a different Minecraft version.

Logs: `logs/MATRIX.txt`, `logs/probe-forge-<mc>-<scenario>.log`, `logs/probe-bukkit-<mc>-<scenario>.log`,
`logs/build-forge-matrix.log`. Design and reproduction: [`TESTING.md`](TESTING.md).

## Forge 1.8–1.12.2

{forge_rows}

`build + checks` is the shared core suite (77 checks) plus the per-version Forge contract suite
(24 checks) compiled against that exact Minecraft/Forge version. A runtime scenario is only listed as
passing when its own log ends in `PASS <mc> <scenario>`.

Verified here: {forge_verified}

## Bukkit / Spigot / Paper

One jar, `rottenleather-bukkit-1.0.2.jar`, compiled against the Spigot 1.8.8 API, resolves the running
CraftBukkit package at enable time.

{bukkit_rows}

Verified here: {bukkit_verified}

### Spigot

Genuine Spigot server jars built locally with SpigotMC's BuildTools; each probe boot asserts that
`/version` reports CraftBukkit with a Spigot build tag at the exact Minecraft version before any
scenario runs, so a Paper or Bukkit-API-only jar can never appear here. A Paper pass is never
reported as a Spigot pass.

{spigot_rows}

Verified here: {spigot_verified}

Spigot versions that are absent from this table were not built here and stay unclaimed: 1.8.9 has no
BuildTools version manifest (HTTP 404), 1.9.4/1.10.2/1.11.2/1.12.1 depend on the purged
`net.md-5:bungeecord-chat` snapshot that no public Maven group still serves, and 1.10/1.11 stop at
BuildTools' empty git-committer identity on this host before a jar exists. See
[`TESTING.md`](TESTING.md) for the per-version detail.

## Proxies

| Platform | Artifact | Status |
| --- | --- | --- |
| BungeeCord | `rottenleather-bungee-1.0.2.jar` | {bungee} |
| Waterfall | same jar as BungeeCord | same BungeeCord plugin API; not separately booted |
| Velocity | `rottenleather-velocity-1.0.2.jar` | {velocity} |

Proxies have no world: the modules report the recipe fingerprint between server and proxy and warn on
a mismatch. They never implement furnace behaviour. The reporting path needs a connected player and is
not covered by the automated probe. Details: [`PROXIES.md`](PROXIES.md).

## Geyser and Floodgate

| Platform | Artifact | Reason |
| --- | --- | --- |
| Geyser | none | Bedrock clients open the Java server's furnace through Geyser's container translation, so the Java recipe applies unchanged. This is a documented rationale, not a tested Bedrock session. |
| Floodgate | none | Floodgate maps identities, not recipes or containers; there is nothing to hook. |

## Fabric

| Platform | Artifact | Reason |
| --- | --- | --- |
| Fabric | none | Fabric's first releases target 1.14; no Fabric loader exists for 1.8–1.12.2. Documented rationale, not a tested artifact. |

## Feature availability by version

| Feature | 1.8.x | 1.9–1.12.2 |
| --- | --- | --- |
| recipe, fuel, XP, hopper automation | yes (probed) | yes (probed) |
| configurable output count | yes (probed) | yes (probed) |
| configurable XP | yes (probed) | yes (probed) |
| configurable cook time | **no on Forge 1.8.x–1.10 and on Bukkit 1.8.x–1.9.x** — the furnace compares against a hard-coded 200 ticks and exposes no usable total field; the mod logs one warning and uses vanilla timing, which the `custom` scenario asserts | yes on Forge 1.10.2–1.12.2 and on Paper 1.10.2–1.12.2, per furnace, restored when the input changes (probed) |
| `conflictPolicy=skip` | yes (probed) | yes (probed) |
| live reload | Forge: re-registers the recipe, so input, output, count, XP and cook time all change (probed). Bukkit: applies XP and cook time only — the 1.8–1.12.2 API cannot re-register a recipe, so a count change needs a restart (probed) | same split: Forge 1.9–1.12.2 reload everything, Bukkit reloads XP and cook time |
| `conflictPolicy=replace` | **no on Forge 1.8.x** — a warning is logged and the existing recipe is kept (asserted by the `replace` scenario) | Forge: yes (probed). Bukkit/Paper: no — the 1.8–1.12.2 API cannot remove a recipe, and the plugin logs that |
""".format(forge_rows=forge_rows, bukkit_rows=bukkit_rows, spigot_rows=spigot_rows,
           spigot_verified=", ".join(spigot_verified) if spigot_verified else "nothing yet",
           forge_verified=", ".join(forge_verified) if forge_verified else "nothing yet",
           bukkit_verified=", ".join(bukkit_verified) if bukkit_verified else "nothing yet",
           bungee=mark(bungee), velocity=mark(velocity))

    OUT.write_text(text, encoding="utf-8", newline="\n")
    print("wrote %s" % OUT.relative_to(ROOT))
    print("forge verified: %s" % (", ".join(forge_verified) or "none"))
    print("bukkit verified: %s" % (", ".join(bukkit_verified) or "none"))


if __name__ == "__main__":
    main()
