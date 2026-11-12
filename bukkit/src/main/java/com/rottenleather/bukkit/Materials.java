package com.rottenleather.bukkit;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Resolves namespaced item ids to the legacy {@link Material} enum used by 1.8-1.12 Bukkit. */
public final class Materials {

    /** 1.13 flattened names that 1.8-1.12 Bukkit spells differently. */
    private static final Map<String, String> LEGACY_NAMES = new HashMap<String, String>();

    static {
        LEGACY_NAMES.put("porkchop", "PORK");
        LEGACY_NAMES.put("cooked_porkchop", "GRILLED_PORK");
        LEGACY_NAMES.put("beef", "RAW_BEEF");
        LEGACY_NAMES.put("chicken", "RAW_CHICKEN");
        LEGACY_NAMES.put("cooked_chicken", "COOKED_CHICKEN");
        LEGACY_NAMES.put("mutton", "MUTTON");
        LEGACY_NAMES.put("rabbit", "RABBIT");
        LEGACY_NAMES.put("cooked_rabbit", "COOKED_RABBIT");
        LEGACY_NAMES.put("cod", "RAW_FISH");
        LEGACY_NAMES.put("salmon", "RAW_FISH");
        LEGACY_NAMES.put("tropical_fish", "RAW_FISH");
        LEGACY_NAMES.put("cooked_cod", "COOKED_FISH");
        LEGACY_NAMES.put("cooked_salmon", "COOKED_FISH");
        LEGACY_NAMES.put("potato", "POTATO_ITEM");
        LEGACY_NAMES.put("baked_potato", "BAKED_POTATO");
        LEGACY_NAMES.put("poisonous_potato", "POISONOUS_POTATO");
    }

    private Materials() {
    }

    public static Material resolve(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String path = id;
        int colon = path.indexOf(':');
        if (colon >= 0) {
            path = path.substring(colon + 1);
        }
        String legacy = LEGACY_NAMES.get(path.toLowerCase(Locale.ROOT));
        Material material = legacy == null ? null : Material.matchMaterial(legacy);
        if (material == null) {
            material = Material.matchMaterial(path);
        }
        if (material == null) {
            material = Material.matchMaterial(path.toUpperCase(Locale.ROOT));
        }
        if (material == null) {
            material = Material.matchMaterial(id);
        }
        return material;
    }

    public static String idOf(Material material) {
        if (material == null) {
            return "minecraft:air";
        }
        return "minecraft:" + material.name().toLowerCase(Locale.ROOT);
    }
}
