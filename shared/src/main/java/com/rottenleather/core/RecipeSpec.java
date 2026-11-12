package com.rottenleather.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One validated furnace recipe. Pure Java: no Minecraft, Forge or Bukkit types appear here, so the
 * same class is unit-tested once and compiled into every platform module.
 *
 * <p>All mutators return new instances. Invalid values are rejected with
 * {@link IllegalArgumentException} rather than clamped, because a silently clamped count (for
 * example 0 or 65) would either delete the recipe or overflow a stack slot.
 */
public final class RecipeSpec {

    /** Vanilla furnace cook time, in ticks. */
    public static final int VANILLA_SMELT_TICKS = 200;

    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final int MAX_TICKS = 72000;
    private static final float MAX_EXPERIENCE = 100.0F;

    private final String inputItem;
    private final String outputItem;
    private final int outputCount;
    private final float experience;
    private final int smeltTimeTicks;
    private final ConflictPolicy conflictPolicy;

    public RecipeSpec(String inputItem, String outputItem, int outputCount, float experience,
                      int smeltTimeTicks, ConflictPolicy conflictPolicy) {
        this.inputItem = requireItemId(inputItem, "inputItem");
        this.outputItem = requireItemId(outputItem, "outputItem");
        if (outputCount < 1 || outputCount > 64) {
            throw new IllegalArgumentException("outputCount must be between 1 and 64, got " + outputCount);
        }
        if (Float.isNaN(experience) || Float.isInfinite(experience) || experience < 0.0F
                || experience > MAX_EXPERIENCE) {
            throw new IllegalArgumentException("experience must be between 0 and " + MAX_EXPERIENCE
                    + ", got " + experience);
        }
        if (smeltTimeTicks < 1 || smeltTimeTicks > MAX_TICKS) {
            throw new IllegalArgumentException("smeltTimeTicks must be between 1 and " + MAX_TICKS
                    + ", got " + smeltTimeTicks);
        }
        if (conflictPolicy == null) {
            throw new IllegalArgumentException("conflictPolicy must not be null");
        }
        this.outputCount = outputCount;
        this.experience = experience;
        this.smeltTimeTicks = smeltTimeTicks;
        this.conflictPolicy = conflictPolicy;
    }

    /** The recipe every module ships and the generated defaults describe. */
    public static RecipeSpec defaults() {
        return new RecipeSpec(BuildDefaults.INPUT_ITEM, BuildDefaults.OUTPUT_ITEM,
                BuildDefaults.OUTPUT_COUNT, BuildDefaults.EXPERIENCE,
                BuildDefaults.SMELTING_TICKS, ConflictPolicy.parse(BuildDefaults.CONFLICT_POLICY));
    }

    private static String requireItemId(String value, String field) {
        if (value == null || !ITEM_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a namespaced item id, got " + value);
        }
        return value;
    }

    public String inputItem() {
        return inputItem;
    }

    public String outputItem() {
        return outputItem;
    }

    /** Always 1: a vanilla furnace consumes one input item per cook. */
    public int inputCount() {
        return 1;
    }

    public int outputCount() {
        return outputCount;
    }

    public float experience() {
        return experience;
    }

    public int smeltTimeTicks() {
        return smeltTimeTicks;
    }

    public boolean isVanillaSmeltTime() {
        return smeltTimeTicks == VANILLA_SMELT_TICKS;
    }

    public ConflictPolicy conflictPolicy() {
        return conflictPolicy;
    }

    /** True when this spec describes exactly the item pair, count, XP and time of {@code other}. */
    public boolean sameRecipe(RecipeSpec other) {
        return other != null
                && inputItem.equals(other.inputItem)
                && outputItem.equals(other.outputItem)
                && outputCount == other.outputCount
                && Float.floatToIntBits(experience) == Float.floatToIntBits(other.experience)
                && smeltTimeTicks == other.smeltTimeTicks;
    }

    /** Human-readable one-liner used in logs and in {@code /rottenleather status}. */
    public String summary() {
        return String.format(Locale.ROOT, "%s -> %s x%d, xp=%s, ticks=%d, policy=%s",
                inputItem, outputItem, outputCount, trimFloat(experience), smeltTimeTicks,
                conflictPolicy.key());
    }

    /**
     * Canonical text form shared with tools/generate.py. Experience is hashed as its IEEE-754
     * single-precision bits so Python and Java cannot disagree about float formatting.
     */
    public String canonical() {
        return "v1|input=" + inputItem
                + "|inputCount=1"
                + "|output=" + outputItem
                + "|outputCount=" + outputCount
                + "|xpBits=" + Float.floatToIntBits(experience)
                + "|ticks=" + smeltTimeTicks
                + "|policy=" + conflictPolicy.key();
    }

    /** SHA-256 of {@link #canonical()}, identifying this recipe across modules and servers. */
    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }

    private static String trimFloat(float value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Float.toString(value);
    }

    @Override
    public String toString() {
        return summary();
    }
}
