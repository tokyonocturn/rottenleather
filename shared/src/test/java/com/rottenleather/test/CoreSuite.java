package com.rottenleather.test;

import com.rottenleather.core.BuildDefaults;
import com.rottenleather.core.ConflictDecision;
import com.rottenleather.core.ConflictPolicy;
import com.rottenleather.core.CookTimeRule;
import com.rottenleather.core.CookTimeTracker;
import com.rottenleather.core.FurnaceTarget;
import com.rottenleather.core.RecipeSpec;


/**
 * Plain-Java suite for the shared core. No test framework is used because the 1.8-era Gradle
 * distributions cannot resolve modern JUnit artifacts reliably offline, and because this suite has
 * to run against the exact Java 8 class files that ship.
 */
public final class CoreSuite {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        specDefaults();
        fingerprint();
        validation();
        conflictPolicy();
        conflictDecision();
        cookTimeRule();
        cookTimeTracker();

        System.out.println("CORE_SUITE pass=" + passed + " fail=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ RecipeSpec

    private static void specDefaults() {
        RecipeSpec spec = RecipeSpec.defaults();
        check("defaults.input", "minecraft:rotten_flesh", spec.inputItem());
        check("defaults.output", "minecraft:leather", spec.outputItem());
        check("defaults.inputCount", 1, spec.inputCount());
        check("defaults.outputCount", BuildDefaults.OUTPUT_COUNT, spec.outputCount());
        check("defaults.experience", BuildDefaults.EXPERIENCE, spec.experience());
        check("defaults.ticks", BuildDefaults.SMELTING_TICKS, spec.smeltTimeTicks());
        check("defaults.policy", ConflictPolicy.SKIP, spec.conflictPolicy());
        check("defaults.vanillaTime", true, spec.isVanillaSmeltTime());
        checkTrue("defaults.summary contains input", spec.summary().contains("minecraft:rotten_flesh"));
        checkTrue("defaults.summary contains ticks", spec.summary().contains("ticks=200"));

        RecipeSpec slow = new RecipeSpec("minecraft:rotten_flesh", "minecraft:leather", 1, 0.1F, 300,
                ConflictPolicy.SKIP);
        check("spec.notVanillaTime", false, slow.isVanillaSmeltTime());
        checkTrue("spec.sameRecipe", spec.sameRecipe(RecipeSpec.defaults()));
        checkTrue("spec.differentTicksIsDifferent", !spec.sameRecipe(slow));
        checkTrue("spec.differentTime", !spec.sameRecipe(new RecipeSpec("minecraft:rotten_flesh",
                "minecraft:leather", 1, 0.1F, 201, ConflictPolicy.SKIP)));
        checkTrue("spec.differentCount", !spec.sameRecipe(new RecipeSpec("minecraft:rotten_flesh",
                "minecraft:leather", 2, 0.1F, 200, ConflictPolicy.SKIP)));
        checkTrue("spec.differentXp", !spec.sameRecipe(new RecipeSpec("minecraft:rotten_flesh",
                "minecraft:leather", 1, 0.2F, 200, ConflictPolicy.SKIP)));
        checkTrue("spec.differentItem", !spec.sameRecipe(new RecipeSpec("minecraft:beef",
                "minecraft:leather", 1, 0.1F, 200, ConflictPolicy.SKIP)));
        checkTrue("spec.sameRecipeNull", !spec.sameRecipe(null));
    }

    private static void fingerprint() {
        RecipeSpec spec = RecipeSpec.defaults();
        check("fingerprint.matchesGenerated", BuildDefaults.SPEC_FINGERPRINT, spec.fingerprint());
        check("fingerprint.length", 64, spec.fingerprint().length());
        checkTrue("fingerprint.lowercaseHex", spec.fingerprint().matches("[0-9a-f]{64}"));
        checkTrue("fingerprint.canonicalHasBits", spec.canonical().contains("xpBits="
                + Float.floatToIntBits(spec.experience())));
        checkTrue("fingerprint.stable", spec.fingerprint().equals(RecipeSpec.defaults().fingerprint()));
        checkTrue("fingerprint.changesWithCount", !spec.fingerprint().equals(
                new RecipeSpec(spec.inputItem(), spec.outputItem(), 2, spec.experience(),
                        spec.smeltTimeTicks(), spec.conflictPolicy()).fingerprint()));
        checkTrue("fingerprint.changesWithTicks", !spec.fingerprint().equals(
                new RecipeSpec(spec.inputItem(), spec.outputItem(), 1, spec.experience(), 201,
                        spec.conflictPolicy()).fingerprint()));
    }

    private static void validation() {
        expectReject("reject.inputNull", null, "minecraft:leather", 1, 0.1F, 200);
        expectReject("reject.inputNoNamespace", "rotten_flesh", "minecraft:leather", 1, 0.1F, 200);
        expectReject("reject.inputSpace", "minecraft:rotten flesh", "minecraft:leather", 1, 0.1F, 200);
        expectReject("reject.outputUpper", "minecraft:rotten_flesh", "Minecraft:Leather", 1, 0.1F, 200);
        expectReject("reject.countZero", "minecraft:rotten_flesh", "minecraft:leather", 0, 0.1F, 200);
        expectReject("reject.countTooBig", "minecraft:rotten_flesh", "minecraft:leather", 65, 0.1F, 200);
        expectReject("reject.xpNegative", "minecraft:rotten_flesh", "minecraft:leather", 1, -0.1F, 200);
        expectReject("reject.xpNaN", "minecraft:rotten_flesh", "minecraft:leather", 1, Float.NaN, 200);
        expectReject("reject.xpInfinite", "minecraft:rotten_flesh", "minecraft:leather", 1,
                Float.POSITIVE_INFINITY, 200);
        expectReject("reject.xpTooBig", "minecraft:rotten_flesh", "minecraft:leather", 1, 100.5F, 200);
        expectReject("reject.ticksZero", "minecraft:rotten_flesh", "minecraft:leather", 1, 0.1F, 0);
        expectReject("reject.ticksTooBig", "minecraft:rotten_flesh", "minecraft:leather", 1, 0.1F, 72001);

        RecipeSpec boundary = new RecipeSpec("modid:item.name", "modid:item/path", 64, 100.0F, 72000,
                ConflictPolicy.REPLACE);
        check("accept.boundaries.count", 64, boundary.outputCount());
        check("accept.boundaries.ticks", 72000, boundary.smeltTimeTicks());

        try {
            new RecipeSpec("minecraft:rotten_flesh", "minecraft:leather", 1, 0.1F, 200, null);
            fail("reject.nullPolicy", "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            pass("reject.nullPolicy");
        }
    }

    private static void expectReject(String name, String input, String output, int count, float xp, int ticks) {
        try {
            new RecipeSpec(input, output, count, xp, ticks, ConflictPolicy.SKIP);
            fail(name, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            pass(name);
        }
    }

    private static void conflictPolicy() {
        check("policy.skip", ConflictPolicy.SKIP, ConflictPolicy.parse("skip"));
        check("policy.trim", ConflictPolicy.SKIP, ConflictPolicy.parse("  SKIP "));
        check("policy.replace", ConflictPolicy.REPLACE, ConflictPolicy.parse("Replace"));
        check("policy.garbage", ConflictPolicy.SKIP, ConflictPolicy.parse("merge"));
        check("policy.null", ConflictPolicy.SKIP, ConflictPolicy.parse(null));
        check("policy.empty", ConflictPolicy.SKIP, ConflictPolicy.parse(""));
    }

    private static void conflictDecision() {
        check("decision.absent", ConflictDecision.REGISTER,
                ConflictDecision.decide(false, false, ConflictPolicy.SKIP));
        check("decision.absentFlagIrrelevant", ConflictDecision.REGISTER,
                ConflictDecision.decide(false, true, ConflictPolicy.REPLACE));
        check("decision.equivalent", ConflictDecision.ALREADY_EQUIVALENT,
                ConflictDecision.decide(true, true, ConflictPolicy.SKIP));
        check("decision.keep", ConflictDecision.KEEP_EXISTING,
                ConflictDecision.decide(true, false, ConflictPolicy.SKIP));
        check("decision.replace", ConflictDecision.REPLACE_EXISTING,
                ConflictDecision.decide(true, false, ConflictPolicy.REPLACE));
        check("decision.registerFlag", true, ConflictDecision.REGISTER.shouldRegister());
        check("decision.replaceFlag", true, ConflictDecision.REPLACE_EXISTING.shouldRegister());
        check("decision.keepFlag", false, ConflictDecision.KEEP_EXISTING.shouldRegister());
        check("decision.equivalentFlag", false, ConflictDecision.ALREADY_EQUIVALENT.shouldRegister());
    }

    private static void cookTimeRule() {
        check("cook.vanillaNoop", CookTimeRule.UNCHANGED, CookTimeRule.totalFor(200, 0, 200));
        check("cook.freshTarget", 100, CookTimeRule.totalFor(100, 0, 200));
        check("cook.shortens", 100, CookTimeRule.totalFor(100, 50, 200));
        check("cook.alreadyElapsed", 151, CookTimeRule.totalFor(100, 150, 200));
        check("cook.alreadyMatches", CookTimeRule.UNCHANGED, CookTimeRule.totalFor(100, 10, 100));
        check("cook.zeroProgressLonger", 400, CookTimeRule.totalFor(400, 0, 200));
        check("cook.oneTick", 1, CookTimeRule.totalFor(1, 0, 200));
        check("cook.invalidConfig", CookTimeRule.UNCHANGED, CookTimeRule.totalFor(0, 5, 200));
        check("cook.negativeProgressTolerated", 100, CookTimeRule.totalFor(100, -3, 200));
    }

    private static void cookTimeTracker() {
        FakeFurnace a = new FakeFurnace(true, 0, 200);
        FakeFurnace b = new FakeFurnace(true, 20, 200);
        CookTimeTracker tracker = new CookTimeTracker();

        check("tracker.adjustReturn", 100, tracker.adjust(a, 100));
        check("tracker.applied", 100, a.totalCookTime());
        check("tracker.count", 1, tracker.trackedCount());

        check("tracker.secondAdjust", 120, tracker.adjust(b, 120));
        check("tracker.count2", 2, tracker.trackedCount());

        a.cookTime = 60;
        check("tracker.reAdjustNoopSame", CookTimeRule.UNCHANGED, tracker.adjust(a, 100));

        a.inputIsRecipe = false;
        tracker.adjust(a, 100);
        check("tracker.restore", 200, a.totalCookTime());
        check("tracker.restoreCount", 1, tracker.trackedCount());

        FakeFurnace c = new FakeFurnace(true, 0, 200);
        tracker.adjust(c, 50);
        c.total = 250; // another mod changed it after us
        c.inputIsRecipe = false;
        tracker.adjust(c, 50);
        check("tracker.externalChangeWins", 250, c.totalCookTime());

        FakeFurnace d = new FakeFurnace(false, 0, 200);
        check("tracker.nonMatchingNoop", CookTimeRule.UNCHANGED, tracker.adjust(d, 60));
        check("tracker.nonMatchingUntouched", 200, d.totalCookTime());

        tracker.releaseAll();
        check("tracker.releaseAllRestores", 200, b.totalCookTime());
        check("tracker.releaseAllEmpty", 0, tracker.trackedCount());
    }

    // ------------------------------------------------------------------ helpers

    private static final class FakeFurnace implements FurnaceTarget {
        boolean inputIsRecipe;
        int cookTime;
        int total;
        String name = "test";

        FakeFurnace(boolean inputIsRecipe, int cookTime, int total) {
            this.inputIsRecipe = inputIsRecipe;
            this.cookTime = cookTime;
            this.total = total;
        }

        @Override
        public Object identity() {
            return this;
        }

        @Override
        public boolean inputIsRecipeInput() {
            return inputIsRecipe;
        }

        @Override
        public int cookTime() {
            return cookTime;
        }

        @Override
        public int totalCookTime() {
            return total;
        }

        @Override
        public void setTotalCookTime(int ticks) {
            total = ticks;
        }

        @Override
        public String describe() {
            return name;
        }
    }

    private static void pass(String name) {
        passed++;
        System.out.println("PASS " + name);
    }

    private static void fail(String name, String detail) {
        failed++;
        System.out.println("FAIL " + name + ": " + detail);
    }

    private static void check(String name, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            pass(name);
        } else {
            fail(name, "expected " + expected + ", got " + actual);
        }
    }

    private static void check(String name, int expected, int actual) {
        check(name, Integer.valueOf(expected), Integer.valueOf(actual));
    }

    private static void check(String name, float expected, float actual) {
        check(name, Float.valueOf(expected), Float.valueOf(actual));
    }

    private static void checkTrue(String name, boolean condition) {
        if (condition) {
            pass(name);
        } else {
            fail(name, "condition was false");
        }
    }
}
