package com.rottenleather.core;

/**
 * One furnace as seen by {@link CookTimeTracker}. Platform modules implement this over their own
 * furnace type, so the tracker's bookkeeping is shared and unit-tested once.
 */
public interface FurnaceTarget {

    /**
     * Stable identity for this furnace (the tile entity itself). Adapters may create a fresh
     * wrapper for every scan, so the tracker must never key on the wrapper.
     */
    Object identity();

    /** True when the input slot holds this recipe's input item. */
    boolean inputIsRecipeInput();

    int cookTime();

    int totalCookTime();

    void setTotalCookTime(int ticks);

    /** Short description for logs, for example {@code (12,64,-3)}. */
    String describe();
}
