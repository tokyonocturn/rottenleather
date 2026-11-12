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

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
    private static boolean experienceMapResolved;
    private static Map<ItemStack, Float> experienceMap;
    private static boolean experienceMapWarned;

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
        // Forge looks smelting experience up by item and metadata, not by stack size, so a second
        // registration for the same result (a reload that changes outputCount) leaves two entries and
        // the value the player receives depends on map iteration order. Keep exactly one.
        setSingleExperience(outputStack, spec.experience());
        // Forge silently refuses a registration whose input still maps to something; verify that the
        // map really holds the configured result and experience instead of trusting the call.
        ItemStack installed = FurnaceRecipes.instance().getSmeltingResult(inputStack);
        float installedXp = FurnaceRecipes.instance().getSmeltingExperience(outputStack);
        if (!VersionAdapterImpl.sameItemAndCount(installed, outputStack)
                || Math.abs(installedXp - spec.experience()) > 0.0001F) {
            registered = false;
            RottenLeatherMod.setInputItem(null);
            LOG.error("Rotten Flesh to Leather: the server refused the recipe for {} (installed {} with "
                            + "xp {}, wanted {} with xp {})", spec.inputItem(),
                    VersionAdapterImpl.describe(installed), installedXp,
                    VersionAdapterImpl.describe(outputStack), spec.experience());
            Map<ItemStack, Float> xpMap = experienceEntries();
            if (xpMap != null) {
                for (Map.Entry<ItemStack, Float> e : xpMap.entrySet()) {
                    if (e.getKey().getItem() == output) {
                        LOG.error("  experience entry {} = {}",
                                VersionAdapterImpl.describe(e.getKey()), e.getValue());
                    }
                }
            }
            return new Result(false, "registration-refused");
        }
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
            // Remove by input key only: the stored result stack may have a different stack size than
            // this mod's last registration (a reload can change outputCount), and matching on the
            // result too would leave the old entry behind, after which Forge refuses the new one.
            Iterator<Map.Entry<ItemStack, ItemStack>> it =
                    FurnaceRecipes.instance().getSmeltingList().entrySet().iterator();
            while (it.hasNext()) {
                if (matchesRecipeKey(it.next().getKey(), activeInput)) {
                    it.remove();
                }
            }
        } catch (Throwable t) {
            LOG.warn("Rotten Flesh to Leather: could not remove the previous recipe entry", t);
        } finally {
            if (activeInput != null && replacedOutput != null) {
                // Put back the recipe this mod displaced, so a reload to conflictPolicy=skip or a
                // changed input does not silently delete another mod's or vanilla's recipe.
                GameRegistry.addSmelting(activeInput, replacedOutput, replacedExperience);
                setSingleExperience(replacedOutput, replacedExperience);
            } else if (activeOutput != null) {
                // Our own entry is gone; drop the experience this mod attached to its result so the
                // item does not keep granting XP for a recipe that is no longer installed.
                removeExperienceFor(activeOutput);
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

    /**
     * Experience the server will actually grant, read straight from the map vanilla reads when a
     * player takes the output. Forge matches experience by item and metadata, so the count does not
     * matter here; {@link #setSingleExperience} guarantees there is only one entry per result item.
     */
    public static float runtimeExperience(RecipeSpec spec) {
        Item output = VersionAdapterImpl.resolveItem(spec.outputItem());
        if (output == null) {
            return -1.0F;
        }
        return FurnaceRecipes.instance().getSmeltingExperience(new ItemStack(output));
    }

    /**
     * The private {@code Map<ItemStack, Float>} that backs {@code getSmeltingExperience}.
     *
     * <p>Forge 1.8-1.12.2 has no API to read or replace individual experience entries, and its
     * lookup ignores stack size, so the only way to keep one authoritative value per result is to
     * touch the map itself. The field is located by its generic type, not by name, because names are
     * remapped differently in every one of those releases.
     */
    @SuppressWarnings("unchecked")
    private static Map<ItemStack, Float> experienceEntries() {
        if (experienceMapResolved) {
            return experienceMap;
        }
        experienceMapResolved = true;
        try {
            for (Field field : FurnaceRecipes.class.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                Type generic = field.getGenericType();
                if (!(generic instanceof ParameterizedType)) {
                    continue;
                }
                Type[] arguments = ((ParameterizedType) generic).getActualTypeArguments();
                if (arguments.length == 2 && arguments[1] == Float.class) {
                    field.setAccessible(true);
                    experienceMap = (Map<ItemStack, Float>) field.get(FurnaceRecipes.instance());
                    return experienceMap;
                }
            }
        } catch (Throwable t) {
            LOG.warn("Rotten Flesh to Leather: could not reach Forge's smelting experience map", t);
        }
        if (!experienceMapWarned) {
            experienceMapWarned = true;
            LOG.warn("Rotten Flesh to Leather: Forge's smelting experience map is not reachable; "
                    + "changing outputCount with the same output item may keep an older experience value");
        }
        return null;
    }

    /** Leaves exactly one experience entry for this result item, with the given value. */
    private static void setSingleExperience(ItemStack result, float experience) {
        Map<ItemStack, Float> map = experienceEntries();
        if (map == null) {
            return;
        }
        try {
            removeExperienceFor(result);
            map.put(result.copy(), experience);
        } catch (Throwable t) {
            LOG.warn("Rotten Flesh to Leather: could not store the smelting experience for {}",
                    VersionAdapterImpl.describe(result), t);
        }
    }

    /** Drops every experience entry Forge would match against this result item. */
    private static void removeExperienceFor(ItemStack result) {
        Map<ItemStack, Float> map = experienceEntries();
        if (map == null) {
            return;
        }
        try {
            Iterator<Map.Entry<ItemStack, Float>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                ItemStack key = it.next().getKey();
                if (key != null && key.getItem() == result.getItem()) {
                    it.remove();
                }
            }
        } catch (Throwable t) {
            LOG.warn("Rotten Flesh to Leather: could not clear the smelting experience for {}",
                    VersionAdapterImpl.describe(result), t);
        }
    }

    /**
     * True when a map key describes the same furnace input as {@code wanted}.
     *
     * <p>Vanilla registers its own recipes with metadata 32767 ("any variant"), so strict metadata
     * equality never finds a vanilla entry and a replace would silently leave it in place; Forge's
     * own lookup treats 32767 as a wildcard, and removal has to use the same rule.
     */
    private static boolean matchesRecipeKey(ItemStack key, ItemStack wanted) {
        if (VersionAdapterImpl.isEmpty(key) || VersionAdapterImpl.isEmpty(wanted)
                || key.getItem() != wanted.getItem()) {
            return false;
        }
        return key.getMetadata() == 32767 || wanted.getMetadata() == 32767
                || key.getMetadata() == wanted.getMetadata();
    }

    /** Removes every furnace mapping for this exact input item, regardless of who added it. */
    private static void removeMappingsFor(ItemStack inputStack) {
        try {
            Iterator<Map.Entry<ItemStack, ItemStack>> it =
                    FurnaceRecipes.instance().getSmeltingList().entrySet().iterator();
            while (it.hasNext()) {
                if (matchesRecipeKey(it.next().getKey(), inputStack)) {
                    it.remove();
                }
            }
        } catch (Throwable t) {
            LOG.warn("Rotten Flesh to Leather: could not clear the existing recipe for {}",
                    VersionAdapterImpl.describe(inputStack), t);
        }
    }
}
