package com.rottenleather.bukkit;

import org.bukkit.Material;

import java.util.Locale;

/** Resolves namespaced item ids to the legacy {@link Material} enum used by 1.8-1.12 Bukkit. */
public final class Materials {

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
        Material material = Material.matchMaterial(path);
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
