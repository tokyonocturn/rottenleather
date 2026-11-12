package com.rottenleather.core;

/**
 * Computes the furnace {@code totalCookTime} value that makes this recipe finish at the configured
 * number of ticks.
 *
 * <p>Vanilla 1.8-1.12.2 has no per-recipe cook time: {@code TileEntityFurnace} increments
 * {@code cookTime} once per tick and finishes when {@code cookTime == totalCookTime}. The total is a
 * per-furnace value of 200. Adjusting it is safe while this recipe's input sits in the furnace,
 * because that furnace can only be cooking this recipe, and it is restored when the input changes.
 *
 * <p>The rule never returns a value that has already elapsed: if the configured time is shorter
 * than the progress already made (for example after a config reload), the total becomes
 * {@code cookTime + 1} so the furnace completes on the next tick instead of freezing forever.
 */
public final class CookTimeRule {

    /** Return value meaning "leave totalCookTime alone". */
    public static final int UNCHANGED = -1;

    private CookTimeRule() {
    }

    /**
     * @param configuredTicks desired cook time
     * @param cookTime progress already made in this furnace
     * @param currentTotal the furnace's current totalCookTime
     * @return the total to write, or {@link #UNCHANGED}
     */
    public static int totalFor(int configuredTicks, int cookTime, int currentTotal) {
        if (configuredTicks < 1) {
            return UNCHANGED;
        }
        int target = Math.max(configuredTicks, cookTime + 1);
        return target == currentTotal ? UNCHANGED : target;
    }
}
