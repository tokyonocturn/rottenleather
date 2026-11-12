#!/usr/bin/env python3
"""Generates the compatibility tables from the logs the build and probe scripts write.

Only evidence counts. A Forge version is "verified" when its build log shows both suites passing and
either probe log ends in PASS; otherwise it is reported as not verified.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
LOGS = ROOT / "logs"


def read(path):
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except FileNotFoundError:
        return ""


def forge_builds():
    out = {}
    for line in read(LOGS / "build-forge-final.log").splitlines():
        m = re.match(
            r"RESULT (\S+): PASS .*CORE_SUITE pass=(\d+) fail=(\d+) .*FORGE_SUITE mc=\S+ pass=(\d+) fail=(\d+)",
            line)
        if m:
            ok = m.group(3) == "0" and m.group(5) == "0"
            out[m.group(1)] = (ok, int(m.group(2)) + int(m.group(4)))
    return out


def probe_result(mc):
    for name in (f"probe-quick-{mc}.log", f"probe-forge-{mc}.log", f"probe-{mc}.out"):
        text = read(LOGS / name)
        if not text:
            continue
        if f"PASS {mc}" in text:
            return "pass"
        if "FAIL" in text:
            return "fail"
    return "not run"


def main():
    builds = forge_builds()
    print("| Minecraft | Forge | build + API checks | runtime probe | support |")
    print("| --- | --- | --- | --- | --- |")
    for line in read(ROOT / "forge" / "versions" / "versions.txt").splitlines():
        if not line.strip():
            continue
        parts = line.split()
        mc, forge = parts[0], parts[1]
        build = builds.get(mc)
        runtime = probe_result(mc)
        verified = bool(build and build[0]) and runtime == "pass"
        print(f"| {mc} | {forge} | {build[1] if build else '-'} checks | {runtime} | "
              f"{'verified' if verified else 'not verified'} |")

    print()
    print("### Bukkit")
    print("| Server | runtime probe | support |")
    print("| --- | --- | --- |")
    for mc in ("1.8.8", "1.8.9", "1.9", "1.9.4", "1.10", "1.10.2", "1.11", "1.11.2", "1.12", "1.12.2"):
        text = read(LOGS / f"probe-bukkit-{mc}.out") or read(LOGS / f"probe-bukkit-{mc}.log")
        if f"PASS bukkit-{mc}" in text:
            state = "pass"
        elif "FAIL" in text:
            state = "fail"
        else:
            state = "not run"
        print(f"| Paper {mc} | {state} | {'verified' if state == 'pass' else 'not verified'} |")

    print()
    print("### Proxies")
    print(read(LOGS / "proxies.out").strip().splitlines()[-1] if read(LOGS / "proxies.out").strip() else "no proxy probe log")


if __name__ == "__main__":
    main()
