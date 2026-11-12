package com.rottenleather.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Remembers which furnaces this mod adjusted and what their cook time was before, so it can restore
 * it when the input slot changes and other recipes are not slowed down or sped up.
 *
 * <p>Furnaces are held weakly: an unloaded chunk must not keep tile entities alive. Restoration is
 * defensive - if another mod changed {@code totalCookTime} after this mod did, the other value wins.
 */
public final class CookTimeTracker {

    private static final class Entry {
        final int original;
        int applied;
        FurnaceTarget target;

        Entry(int original, int applied, FurnaceTarget target) {
            this.original = original;
            this.applied = applied;
            this.target = target;
        }
    }

    private final Map<Object, Entry> entries = Collections.synchronizedMap(new WeakHashMap<Object, Entry>());

    /**
     * Applies the configured cook time to one furnace.
     *
     * @return the value written to totalCookTime, or {@link CookTimeRule#UNCHANGED}
     */
    public int adjust(FurnaceTarget target, int configuredTicks) {
        if (!target.inputIsRecipeInput()) {
            release(target);
            return CookTimeRule.UNCHANGED;
        }
        int currentTotal = target.totalCookTime();
        int next = CookTimeRule.totalFor(configuredTicks, target.cookTime(), currentTotal);
        if (next == CookTimeRule.UNCHANGED) {
            return CookTimeRule.UNCHANGED;
        }
        synchronized (entries) {
            Entry entry = entries.get(target.identity());
            if (entry == null) {
                entry = new Entry(currentTotal, next, target);
                entries.put(target.identity(), entry);
            } else {
                entry.applied = next;
                entry.target = target;
            }
        }
        target.setTotalCookTime(next);
        return next;
    }

    /** Restores a furnace's original cook time when this mod is the one that changed it. */
    public void release(FurnaceTarget target) {
        Entry entry;
        synchronized (entries) {
            entry = entries.remove(target.identity());
        }
        if (entry != null && target.totalCookTime() == entry.applied) {
            target.setTotalCookTime(entry.original);
        }
    }

    /** Restores every tracked furnace; used when the feature is switched off or the server stops. */
    public void releaseAll() {
        List<Entry> snapshot;
        synchronized (entries) {
            snapshot = new ArrayList<Entry>(entries.values());
            entries.clear();
        }
        for (Entry entry : snapshot) {
            if (entry.target != null && entry.target.totalCookTime() == entry.applied) {
                entry.target.setTotalCookTime(entry.original);
            }
        }
    }

    public int trackedCount() {
        return entries.size();
    }
}
