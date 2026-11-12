package com.rottenleather.bukkit;

import com.rottenleather.core.ConflictDecision;
import com.rottenleather.core.RecipeSpec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Iterator;
import java.util.logging.Logger;

/**
 * Registers a vanilla {@link FurnaceRecipe}. Bukkit's recipe map is keyed by input material, so a
 * second plugin recipe for the same input can only replace the first; the conflict policy decides
 * whether that is allowed.
 */
public final class BukkitRecipeInstaller {

    private static boolean registered;
    private static String detail = "not attempted";
    private static Material inputMaterial;
    private static Material outputMaterial;

    private BukkitRecipeInstaller() {
    }

    public static synchronized void install(RecipeSpec spec, Logger log, boolean logRegistration) {
        inputMaterial = Materials.resolve(spec.inputItem());
        outputMaterial = Materials.resolve(spec.outputItem());
        if (inputMaterial == null || outputMaterial == null) {
            registered = false;
            detail = "unknown material";
            log.severe("Rotten Flesh to Leather: unknown item/materal id (input=" + spec.inputItem()
                    + ", output=" + spec.outputItem() + "); no furnace recipe registered");
            return;
        }
        if (inputMaterial != Material.ROTTEN_FLESH) {
            log.warning("Rotten Flesh to Leather: input is " + inputMaterial
                    + " (configured); the default recipe uses ROTTEN_FLESH");
        }

        FurnaceRecipe existing = findFurnaceRecipe(inputMaterial);
        boolean equivalent = existing != null && existing.getResult() != null
                && existing.getResult().getType() == outputMaterial
                && existing.getResult().getAmount() == spec.outputCount();
        ConflictDecision decision = ConflictDecision.decide(existing != null, equivalent, spec.conflictPolicy());
        detail = decision.name();

        if (decision == ConflictDecision.REPLACE_EXISTING) {
            // The 1.8-1.12.2 Bukkit API has no removeRecipe for a single recipe; removing every
            // recipe on the server to take one input slot over would destroy other plugins'
            // recipes, so this platform keeps the existing recipe instead.
            registered = false;
            detail = "REPLACE_UNSUPPORTED_ON_BUKKIT";
            log.warning("Rotten Flesh to Leather: conflictPolicy=replace is not supported on Bukkit "
                    + "1.8-1.12.2 (no per-recipe removal API); keeping the existing recipe for "
                    + inputMaterial + ". Use conflictPolicy=skip on this platform.");
            return;
        }

        if (!decision.shouldRegister()) {
            registered = false;
            if (logRegistration) {
                log.info("Rotten Flesh to Leather: kept the existing recipe for " + inputMaterial
                        + " -> " + (existing == null ? "?" : existing.getResult().getType()) + " (" + decision + ")");
            }
            return;
        }

        ItemStack result = new ItemStack(outputMaterial, spec.outputCount());
        Bukkit.addRecipe(new FurnaceRecipe(result, inputMaterial));
        boolean xp = NmsBridge.setExperience(result, spec.experience());
        registered = true;
        detail = "registered";
        if (logRegistration) {
            log.info("Rotten Flesh to Leather: registered " + spec.summary()
                    + (xp ? "" : " (experience left at server default: no experience API)"));
        }
    }

    private static FurnaceRecipe findFurnaceRecipe(Material input) {
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe instanceof FurnaceRecipe) {
                FurnaceRecipe furnace = (FurnaceRecipe) recipe;
                ItemStack ingredient = furnace.getInput();
                if (ingredient != null && ingredient.getType() == input) {
                    return furnace;
                }
            }
        }
        return null;
    }

    public static synchronized boolean isRegistered() {
        return registered;
    }

    public static synchronized String detail() {
        return detail;
    }

    public static Material inputMaterial() {
        return inputMaterial;
    }

    public static ItemStack resultFor(RecipeSpec spec) {
        Material material = Materials.resolve(spec.outputItem());
        return material == null ? null : new ItemStack(material, spec.outputCount());
    }
}
