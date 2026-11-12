package com.rottenleather.forge;

import com.rottenleather.core.BuildDefaults;
import com.rottenleather.core.ConflictDecision;
import com.rottenleather.core.RecipeSpec;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraftforge.fml.common.registry.GameRegistry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Map;

/**
 * Registers the furnace recipe in Forge's own {@code FurnaceRecipes} map.
 *
 * <p>Using the vanilla map (rather than a custom furnace) is what makes fuel, experience, hopper
 * insertion and extraction, furnace minecarts and every mod that reads vanilla smelting behave
 * exactly as they do for a vanilla recipe.
 */
public final class ForgeRecipeInstaller {

    private static final Logger LOG = LogManager.getLogger(BuildDefaults.MOD_ID);

    private static ItemStack activeInput;
    private static ItemStack activeOutput;
    /** The recipe this mod displaced under conflictPolicy=replace, restored on reload/stop. */
    private static ItemStack replacedOutput;
    private static float replacedExperience;
    private static boolean registered;

    private ForgeRecipeInstaller() {
    }

    /** Result of one install attempt, reported by {@code /rottenleather status}. */
    public static final class Result {
        public final boolean registered;
        public final String detail;

        Result(boolean registered, String detail) {
            this.registered = registered;
            this.detail = detail;
        }
    }

    public static synchronized Result install(RecipeSpec spec, boolean log) {
        Item input = VersionAdapterImpl.resolveItem(spec.inputItem());
        Item output = VersionAdapterImpl.resolveItem(spec.outputItem());
        if (input == null || output == null) {
            registered = false;
            RottenLeatherMod.setInputItem(null);
            String missing = input == null ? spec.inputItem() : spec.outputItem();
            LOG.error("Rotten Flesh to Leather: unknown item id '{}'; no furnace recipe was registered",
                    missing);
            return new Result(false, "unknown item " + missing);
        }

        // A reload may change the recipe; drop only the entry this mod itself created.
        removeActiveMapping();

        ItemStack inputStack = new ItemStack(input);
        ItemStack outputStack = new ItemStack(output, spec.outputCount());

        ItemStack existing = FurnaceRecipes.instance().getSmeltingResult(inputStack);
        boolean present = !VersionAdapterImpl.isEmpty(existing);
        boolean equivalent = present && VersionAdapterImpl.sameItemAndCount(existing, outputStack);
        ConflictDecision decision = ConflictDecision.decide(present, equivalent, spec.conflictPolicy());

        if (!decision.shouldRegister()) {
            registered = false;
            RottenLeatherMod.setInputItem(null);
            if (log) {
                LOG.info("Rotten Flesh to Leather: kept the existing recipe for {} -> {} ({}); "
                                + "set conflictPolicy=replace to take over",
                        spec.inputItem(), VersionAdapterImpl.describe(existing), decision);
            }
            return new Result(false, decision.name());
        }

        ItemStack displaced = null;
        float displacedExperience = 0.0F;
        if (present) {
            displaced = existing.copy();
            // Forge keys smelting experience by the *result* stack in every 1.8-1.12.2 release
            // (vanilla reads it that way when a player takes the output), not by the input.
            displacedExperience = FurnaceRecipes.instance().getSmeltingExperience(displaced);
        }
        if (decision == ConflictDecision.REPLACE_EXISTING && !VersionAdapterImpl.SUPPORTS_RECIPE_REPLACE) {
            registered = false;
            RottenLeatherMod.setInputItem(null);
            LOG.warn("Rotten Flesh to Leather: conflictPolicy=replace is not available on MC {}; "
                    + "kept the existing recipe for {} -> {}", VersionAdapterImpl.MC_VERSION,
                    spec.inputItem(), VersionAdapterImpl.describe(existing));
            return new Result(false, "replace-unsupported");
        }
        if (decision == ConflictDecision.REPLACE_EXISTING) {
            // Forge refuses a registration whose input already maps to something else
            // ("Ignored smelting recipe with conflicting input"), so remove the entry first.
            removeMappingsFor(inputStack);
        }

        GameRegistry.addSmelting(inputStack, outputStack, spec.experience());
        activeInput = inputStack;
        activeOutput = outputStack;
        replacedOutput = displaced;
        replacedExperience = displacedExperience;
        registered = true;
        RottenLeatherMod.setInputItem(input);
        if (log) {
            LOG.info("Rotten Flesh to Leather: registered {}", spec.summary());
        }
        return new Result(true, "registered");
    }

    /** Removes this mod's own earlier entry, leaving any other mod's recipe for the same input alone. */
    private static void removeActiveMapping() {
        if (activeInput == null || activeOutput == null) {
            return;
        }
        try {
            Map<ItemStack, ItemStack> map = FurnaceRecipes.instance().getSmeltingList();
            Iterator<Map.Entry<ItemStack, ItemStack>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ItemStack, ItemStack> entry = it.next();
                if (VersionAdapterImpl.sameItem(entry.getKey(), activeInput)
                        && VersionAdapterImpl.sameItem(entry.getValue(), activeOutput)) {
                    it.remove();
                    break;
                }
            }
        } catch (Throwable t) {
            LOG.warn("Rotten Flesh to Leather: could not remove the previous recipe entry", t);            } finally {
            if (activeInput != null && replacedOutput != null) {
                // Put back the recipe this mod displaced, so a reload to conflictPolicy=skip or a
                // changed input does not silently delete another mod's or vanilla's recipe.
                GameRegistry.addSmelting(activeInput, replacedOutput, replacedExperience);
            }
            activeInput = null;
            activeOutput = null;
            replacedOutput = null;
            replacedExperience = 0.0F;
            registered = false;
            RottenLeatherMod.setInputItem(null);
        }
    }

    public static synchronized boolean isRegistered() {
        return registered;
    }

    public static synchronized String activeRecipe() {
        if (activeInput == null) {
            return "none";
        }
        return VersionAdapterImpl.describe(activeInput) + " -> " + VersionAdapterImpl.describe(activeOutput);
    }

    /** Experience the server will actually grant, read back from Forge keyed by the result stack. */
    public static float runtimeExperience(RecipeSpec spec) {
        Item output = VersionAdapterImpl.resolveItem(spec.outputItem());
        if (output == null) {
            return -1.0F;
        }
        return FurnaceRecipes.instance().getSmeltingExperience(new ItemStack(output));
    }

    /** Removes every furnace mapping for this exact input item, regardless of who added it. */
    private static void removeMappingsFor(ItemStack inputStack) {
        try {
            Iterator<Map.Entry<ItemStack, ItemStack>> it =
                    FurnaceRecipes.instance().getSmeltingList().entrySet().iterator();
            while (it.hasNext()) {
                if (VersionAdapterImpl.sameItem(it.next().getKey(), inputStack)) {
                    it.remove();
                }
            }
        } catch (Throwable t) {
            LOG.warn("Rotten Flesh to Leather: could not clear the existing recipe for {}",
                    VersionAdapterImpl.describe(inputStack), t);
        }
    }
}
