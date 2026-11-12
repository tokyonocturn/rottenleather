package com.rottenleather.forge;

import com.rottenleather.core.BuildDefaults;
import com.rottenleather.core.RecipeSpec;
import net.minecraftforge.common.config.Configuration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Forge configuration. Every value is validated through {@link RecipeSpec}; an invalid entry falls
 * back to the generated defaults with one warning instead of stopping a server from booting.
 */
public final class ForgeConfig {

    private static final Logger LOG = LogManager.getLogger(BuildDefaults.MOD_ID);

    private final Configuration configuration;

    private String inputItem = BuildDefaults.INPUT_ITEM;
    private String outputItem = BuildDefaults.OUTPUT_ITEM;
    private int outputCount = BuildDefaults.OUTPUT_COUNT;
    private float experience = BuildDefaults.EXPERIENCE;
    private int smeltTimeTicks = BuildDefaults.SMELTING_TICKS;
    private String conflictPolicy = BuildDefaults.CONFLICT_POLICY;
    private boolean adjustCookTime = true;
    private boolean logRegistration = true;

    private ForgeConfig(Configuration configuration) {
        this.configuration = configuration;
    }

    public static ForgeConfig defaults() {
        return new ForgeConfig(null);
    }

    public static ForgeConfig load(File file) {
        try {
            Configuration cfg = new Configuration(file);
            ForgeConfig config = new ForgeConfig(cfg);
            config.read();
            if (cfg.hasChanged()) {
                cfg.save();
            }
            return config;
        } catch (Throwable t) {
            LOG.warn("Rotten Flesh to Leather: could not read {} - using built-in defaults", file, t);
            return defaults();
        }
    }

    private void read() {
        if (configuration == null) {
            return;
        }
        inputItem = configuration.getString("inputItem", "recipe", BuildDefaults.INPUT_ITEM,
                "Furnace input item id. A vanilla furnace consumes exactly one item per cook.");
        outputItem = configuration.getString("outputItem", "recipe", BuildDefaults.OUTPUT_ITEM,
                "Furnace output item id.");
        outputCount = configuration.getInt("outputCount", "recipe", BuildDefaults.OUTPUT_COUNT, 1, 64,
                "Output stack size produced by one smelt.");
        experience = configuration.getFloat("experience", "recipe", BuildDefaults.EXPERIENCE, 0.0F, 100.0F,
                "Experience granted per smelt. 0.1 matches vanilla food smelting.");
        smeltTimeTicks = configuration.getInt("smeltTimeTicks", "recipe", BuildDefaults.SMELTING_TICKS, 1, 72000,
                "Cook time in ticks (20 = 1 second). 200 is vanilla. Other values are applied per furnace "
                        + "and restored as soon as the input changes, so other recipes keep vanilla timing.");
        conflictPolicy = configuration.getString("conflictPolicy", "recipe", BuildDefaults.CONFLICT_POLICY,
                "skip: keep another mod's recipe for the same input. replace: take the input slot over.");
        adjustCookTime = configuration.getBoolean("adjustCookTime", "furnace", true,
                "Allow non-vanilla smeltTimeTicks. When false the recipe always uses vanilla 200-tick smelting.");
        logRegistration = configuration.getBoolean("logRegistration", "logging", true,
                "Log the recipe once at load and any conflict decision.");
    }

    public void reload() {
        if (configuration == null) {
            return;
        }
        configuration.load();
        read();
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    /** Validated recipe; invalid user values fall back to defaults with a warning. */
    public RecipeSpec toSpec() {
        try {
            return new RecipeSpec(inputItem, outputItem, outputCount, experience, smeltTimeTicks,
                    com.rottenleather.core.ConflictPolicy.parse(conflictPolicy));
        } catch (IllegalArgumentException e) {
            LOG.warn("Rotten Flesh to Leather: invalid recipe configuration ({}); using defaults",
                    e.getMessage());
            return RecipeSpec.defaults();
        }
    }

    public boolean adjustCookTime() {
        return adjustCookTime;
    }

    public boolean logRegistration() {
        return logRegistration;
    }
}
