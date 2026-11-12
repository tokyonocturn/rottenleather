package com.rottenleather.core;

/**
 * What to do when the input item already has a furnace recipe provided by another mod, plugin or
 * the server itself.
 *
 * <p>{@link #SKIP} is the default because silently replacing a pack's own rotten-flesh recipe can
 * change an established progression. {@link #REPLACE} is for packs that install this mod on
 * purpose and want its output.
 */
public enum ConflictPolicy {

    /** Leave the existing recipe untouched and log what was found. */
    SKIP("skip"),

    /** Register over the existing recipe for the same input. */
    REPLACE("replace");

    private final String key;

    ConflictPolicy(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** Parses a configured value, falling back to {@link #SKIP} for anything unrecognised. */
    public static ConflictPolicy parse(String value) {
        if (value != null) {
            String trimmed = value.trim();
            for (ConflictPolicy policy : values()) {
                if (policy.key.equalsIgnoreCase(trimmed)) {
                    return policy;
                }
            }
        }
        return SKIP;
    }
}
