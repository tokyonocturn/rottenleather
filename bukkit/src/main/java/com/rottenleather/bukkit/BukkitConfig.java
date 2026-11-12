package com.rottenleather.bukkit;

import com.rottenleather.core.BuildDefaults;
import com.rottenleather.core.ConflictPolicy;
import com.rottenleather.core.RecipeSpec;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/** Reads the generated config.yml into a validated {@link RecipeSpec}. */
public final class BukkitConfig {

    private BukkitConfig() {
    }

    public static RecipeSpec read(FileConfiguration config, Logger log) {
        String input = config.getString("recipe.input-item", BuildDefaults.INPUT_ITEM);
        String output = config.getString("recipe.output-item", BuildDefaults.OUTPUT_ITEM);
        int count = config.getInt("recipe.output-count", BuildDefaults.OUTPUT_COUNT);
        double xp = config.getDouble("recipe.experience", BuildDefaults.EXPERIENCE);
        int ticks = config.getInt("recipe.smelt-time-ticks", BuildDefaults.SMELTING_TICKS);
        String policy = config.getString("recipe.conflict-policy", BuildDefaults.CONFLICT_POLICY);
        boolean logRegistration = config.getBoolean("logging.log-registration", true);
        try {
            return new RecipeSpec(input, output, count, (float) xp, ticks, ConflictPolicy.parse(policy));
        } catch (IllegalArgumentException e) {
            log.warning("Rotten Flesh to Leather: invalid configuration (" + e.getMessage()
                    + "); using built-in defaults");
            return RecipeSpec.defaults();
        }
    }

    public static boolean logRegistration(FileConfiguration config) {
        return config.getBoolean("logging.log-registration", true);
    }
}
