package com.rottenleather.core;

/** The decision a platform module makes after looking up the input's existing furnace recipe. */
public enum ConflictDecision {

    /** No recipe for this input existed; register ours. */
    REGISTER,

    /** Another recipe existed with the same output; ours would be identical, so leave it alone. */
    ALREADY_EQUIVALENT,

    /** Another recipe existed and the policy says to keep it. */
    KEEP_EXISTING,

    /** Another recipe existed and the policy says to take the input slot over. */
    REPLACE_EXISTING;

    /**
     * @param existingPresent true when the platform already maps the input to some output
     * @param existingEquivalent true when that output is already exactly what this mod would produce
     * @param policy configured conflict policy
     */
    public static ConflictDecision decide(boolean existingPresent, boolean existingEquivalent, ConflictPolicy policy) {
        if (!existingPresent) {
            return REGISTER;
        }
        if (existingEquivalent) {
            return ALREADY_EQUIVALENT;
        }
        return policy == ConflictPolicy.REPLACE ? REPLACE_EXISTING : KEEP_EXISTING;
    }

    /** True when the platform should call its recipe registration API. */
    public boolean shouldRegister() {
        return this == REGISTER || this == REPLACE_EXISTING;
    }
}
