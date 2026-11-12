#!/usr/bin/env python3
"""Generates platform defaults from spec/recipe.json.

The spec is the single source of truth. This script writes:

  shared/src/main/java/com/rottenleather/core/BuildDefaults.java
  bukkit/src/main/resources/config.yml

Run with --check to verify the generated files are up to date (used by CI and the test suite).
The spec fingerprint is computed identically here and in RecipeSpec.java, so a mismatch between a
platform default and the shared spec fails the core test suite instead of shipping silently.
"""
import hashlib
import json
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC_PATH = ROOT / "spec" / "recipe.json"
JAVA_PATH = ROOT / "shared" / "src" / "main" / "java" / "com" / "rottenleather" / "core" / "BuildDefaults.java"
BUKKIT_CONFIG = ROOT / "bukkit" / "src" / "main" / "resources" / "config.yml"

VALID_POLICIES = ("skip", "replace")


def fail(message):
    sys.stderr.write("spec error: %s\n" % message)
    raise SystemExit(2)


def validate(spec):
    if spec.get("specVersion") != 1:
        fail("specVersion must be 1")
    spec_id = spec.get("id")
    if not isinstance(spec_id, str) or not spec_id:
        fail("id must be a non-empty string")

    for side in ("input", "output"):
        part = spec.get(side)
        if not isinstance(part, dict):
            fail("%s must be an object" % side)
        item = part.get("item")
        count = part.get("count")
        if not isinstance(item, str) or ":" not in item:
            fail("%s.item must be a namespaced id like minecraft:leather" % side)
        namespace, path = item.split(":", 1)
        if not namespace or not path or " " in item:
            fail("%s.item is not a valid namespaced id: %r" % (side, item))
        if not isinstance(count, int) or isinstance(count, bool):
            fail("%s.count must be an integer" % side)
        if not 1 <= count <= 64:
            fail("%s.count must be between 1 and 64" % side)

    if spec["input"]["count"] != 1:
        fail("input.count must be 1: a vanilla furnace smelts exactly one input item per cook")

    xp = spec.get("experience")
    if not isinstance(xp, (int, float)) or isinstance(xp, bool):
        fail("experience must be a number")
    if not 0.0 <= float(xp) <= 100.0:
        fail("experience must be between 0 and 100")

    ticks = spec.get("smeltTimeTicks")
    if not isinstance(ticks, int) or isinstance(ticks, bool) or not 1 <= ticks <= 72000:
        fail("smeltTimeTicks must be an integer between 1 and 72000")

    if spec.get("conflictPolicy") not in VALID_POLICIES:
        fail("conflictPolicy must be one of %s" % (", ".join(VALID_POLICIES),))


def xp_bits(value):
    """IEEE-754 single-precision bits, matching Float.floatToIntBits in Java."""
    return struct.unpack(">i", struct.pack(">f", float(value)))[0]


def canonical(spec):
    return "v1|input=%s|inputCount=%d|output=%s|outputCount=%d|xpBits=%d|ticks=%d|policy=%s" % (
        spec["input"]["item"],
        spec["input"]["count"],
        spec["output"]["item"],
        spec["output"]["count"],
        xp_bits(spec["experience"]),
        spec["smeltTimeTicks"],
        spec["conflictPolicy"],
    )


def java_source(spec, fingerprint):
    return """package com.rottenleather.core;

/**
 * Generated from spec/recipe.json by tools/generate.py. Do not edit by hand.
 *
 * Every platform module compiles this class, so Forge, Bukkit and the proxy modules all describe
 * the same recipe. {@link RecipeSpec#fingerprint()} reproduces {@link #SPEC_FINGERPRINT}.
 */
public final class BuildDefaults {

    public static final String SPEC_VERSION = "%d";
    public static final String MOD_ID = "%s";
    public static final String INPUT_ITEM = "%s";
    public static final int INPUT_COUNT = %d;
    public static final String OUTPUT_ITEM = "%s";
    public static final int OUTPUT_COUNT = %d;
    public static final float EXPERIENCE = %sf;
    public static final int SMELTING_TICKS = %d;
    public static final String CONFLICT_POLICY = "%s";
    public static final String SPEC_FINGERPRINT = "%s";

    private BuildDefaults() {
    }
}
""" % (
        spec["specVersion"],
        spec["id"],
        spec["input"]["item"],
        spec["input"]["count"],
        spec["output"]["item"],
        spec["output"]["count"],
        repr(float(spec["experience"])),
        spec["smeltTimeTicks"],
        spec["conflictPolicy"],
        fingerprint,
    )


def bukkit_config(spec):
    return """# Generated from spec/recipe.json by tools/generate.py. Do not edit this copy by hand:
# run tools/generate.py after changing the spec, then rebuild the plugin.
#
# Every value below is validated at startup. An unreadable, missing or invalid config falls back to
# these defaults and logs a warning, so a bad edit cannot stop a server from starting.
recipe:
  # Namespaced item id of the furnace input. Must smelt one item per cook in a vanilla furnace.
  input-item: %s
  # Namespaced item id of the furnace output.
  output-item: %s
  # How many output items one smelt produces (1-64).
  output-count: %d
  # Experience granted per smelt (0-100).
  experience: %s
  # Furnace cook time in ticks (20 ticks = 1 second). 200 is vanilla.
  smelt-time-ticks: %d
  # skip: keep another plugin's/plugin-provided recipe for the same input.
  # replace: take the input slot over.
  conflict-policy: %s

logging:
  # Log the registered recipe and any conflict decision once, on enable.
  log-registration: true
""" % (
        spec["input"]["item"],
        spec["output"]["item"],
        spec["output"]["count"],
        repr(float(spec["experience"])),
        spec["smeltTimeTicks"],
        spec["conflictPolicy"],
    )


def main(argv):
    check = "--check" in argv
    spec = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
    validate(spec)
    fingerprint = hashlib.sha256(canonical(spec).encode("utf-8")).hexdigest()

    outputs = {
        JAVA_PATH: java_source(spec, fingerprint),
        BUKKIT_CONFIG: bukkit_config(spec),
    }

    stale = []
    for path, content in outputs.items():
        current = path.read_text(encoding="utf-8") if path.exists() else None
        if current != content:
            stale.append(path)
            if not check:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8", newline="\n")

    if check:
        if stale:
            for path in stale:
                sys.stderr.write("stale: %s\n" % path.relative_to(ROOT))
            raise SystemExit(1)
        print("spec check: up to date (fingerprint %s)" % fingerprint)
        return

    print("spec %s -> fingerprint %s" % (spec["id"], fingerprint))
    for path in outputs:
        print("wrote %s" % path.relative_to(ROOT))


if __name__ == "__main__":
    main(sys.argv[1:])
