package com.rottenleather.forge;

import com.rottenleather.core.BuildDefaults;
import com.rottenleather.core.RecipeSpec;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Platform-independent Forge logic. The per-version {@code VersionEntry} holds the {@code @Mod}
 * annotation and forwards Forge lifecycle events here, which keeps all 1.8.8-1.12.2 modules running
 * the same code.
 */
public final class RottenLeatherMod {

    public static final String MOD_ID = BuildDefaults.MOD_ID;
    public static final String NAME = "Rotten Flesh to Leather";
    public static final String VERSION = "1.0.0";

    private static final Logger LOG = LogManager.getLogger(MOD_ID);

    private static ForgeConfig config = ForgeConfig.defaults();
    private static RecipeSpec spec = RecipeSpec.defaults();
    private static String lastInstallDetail = "not attempted";
    private static Item inputItem;

    private RottenLeatherMod() {
    }

    public static void preInit(File configFile) {
        config = ForgeConfig.load(configFile);
        spec = config.toSpec();
        MinecraftForge.EVENT_BUS.register(CookTimeController.class);
        LOG.info("Rotten Flesh to Leather {} for MC {} starting: {}", VERSION,
                VersionAdapterImpl.MC_VERSION, spec.summary());
    }

    public static void loadComplete() {
        ForgeRecipeInstaller.Result result = ForgeRecipeInstaller.install(spec, config.logRegistration());
        lastInstallDetail = result.detail;
    }

    public static void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new VersionCommand());
        LOG.info("Rotten Flesh to Leather: /rottenleather registered (status|reload)");
    }

    public static void serverStopping() {
        CookTimeController.releaseAll();
    }

    /** Re-reads the config file and re-registers the recipe without a restart. */
    public static void reload() {
        config.reload();
        RecipeSpec previous = spec;
        spec = config.toSpec();
        CookTimeController.releaseAll();
        ForgeRecipeInstaller.Result result = ForgeRecipeInstaller.install(spec, true);
        lastInstallDetail = result.detail;
        if (!previous.sameRecipe(spec)) {
            LOG.info("Rotten Flesh to Leather: recipe changed from {} to {}", previous.summary(), spec.summary());
        }
    }

    public static RecipeSpec spec() {
        return spec;
    }

    public static ForgeConfig config() {
        return config;
    }

    /** The resolved input item of the active recipe, or null when this mod's recipe is not installed. */
    public static Item inputItem() {
        return inputItem;
    }

    static void setInputItem(Item item) {
        inputItem = item;
    }

    /** Single parseable line used by the runtime probes and by {@code /rottenleather status}. */
    public static String statusLine() {
        return "recipe=" + spec.inputItem() + "->" + spec.outputItem() + " x" + spec.outputCount()
                + " xp=" + spec.experience() + " ticks=" + spec.smeltTimeTicks()
                + " policy=" + spec.conflictPolicy().key()
                + " fingerprint=" + spec.fingerprint()
                + " mc=" + VersionAdapterImpl.MC_VERSION
                + " mod=" + MOD_ID
                + " registered=" + ForgeRecipeInstaller.isRegistered()
                + " install=" + lastInstallDetail
                + " runtimeXp=" + ForgeRecipeInstaller.runtimeExperience(spec)
                + " active=" + ForgeRecipeInstaller.activeRecipe()
                + " customCookTime=" + VersionAdapterImpl.SUPPORTS_CUSTOM_COOK_TIME
                + " replaceRecipe=" + VersionAdapterImpl.SUPPORTS_RECIPE_REPLACE
                + " adjustedFurnaces=" + CookTimeController.adjustedFurnaces();
    }
}
