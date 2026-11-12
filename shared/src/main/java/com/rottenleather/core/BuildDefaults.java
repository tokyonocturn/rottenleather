package com.rottenleather.core;

/**
 * Generated from spec/recipe.json by tools/generate.py. Do not edit by hand.
 *
 * Every platform module compiles this class, so Forge, Bukkit and the proxy modules all describe
 * the same recipe. {@link RecipeSpec#fingerprint()} reproduces {@link #SPEC_FINGERPRINT}.
 */
public final class BuildDefaults {

    public static final String SPEC_VERSION = "1";
    public static final String MOD_ID = "rottenleather";
    public static final String INPUT_ITEM = "minecraft:rotten_flesh";
    public static final int INPUT_COUNT = 1;
    public static final String OUTPUT_ITEM = "minecraft:leather";
    public static final int OUTPUT_COUNT = 1;
    public static final float EXPERIENCE = 0.1f;
    public static final int SMELTING_TICKS = 200;
    public static final String CONFLICT_POLICY = "skip";
    public static final String SPEC_FINGERPRINT = "c82c791ac2c14fcf053eac4532d9387a50fbb173fcb7bf46fe1e4d4df9e3ad11";

    private BuildDefaults() {
    }
}
