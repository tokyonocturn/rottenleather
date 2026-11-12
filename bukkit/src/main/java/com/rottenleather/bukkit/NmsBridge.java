package com.rottenleather.bukkit;

import com.rottenleather.core.FurnaceTarget;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reflection bridge to the parts of CraftBukkit that the 1.8-1.12.2 Bukkit API does not expose:
 * the furnace's {@code cookTimeTotal} and the per-recipe experience map.
 *
 * <p>Only class and member <em>names</em> are version-specific and every lookup tries the known
 * candidates, so one jar covers Spigot, Paper and forks that keep the CraftBukkit layout. When a
 * lookup fails the plugin keeps working with vanilla furnace timing/experience and says so once in
 * the log - it never pretends the setting was applied.
 */
public final class NmsBridge {

    private static Logger log;
    private static String version = "";
    private static Class<?> furnaceTileClass;
    private static Class<?> nmsItemStackClass;
    private static Class<?> recipesClass;
    private static Method asNmsCopy;
    private static Method asBukkitCopy;
    private static Method craftWorldGetHandle;
    private static Field cookTimeField;
    private static Field cookTimeTotalField;

    private static Field tileEntityListField;
    private static Field experienceField;
    private static Object recipesInstance;
    private static Material inputMaterial;
    private static boolean cookTimeReady;
    private static boolean experienceReady;

    private NmsBridge() {
    }

    public static void init(Logger logger, Material input) {
        log = logger;
        inputMaterial = input;
        version = Bukkit.getServer().getClass().getPackage().getName();
        version = version.substring(version.lastIndexOf('.') + 1);
        try {
            furnaceTileClass = Class.forName("net.minecraft.server." + version + ".TileEntityFurnace");
            // Spigot/CraftBukkit keeps MCP-style member names on the relocated NMS classes:
            // cookTime and cookTimeTotal are the two fields the furnace ticks itself with.
            cookTimeField = findField(furnaceTileClass, "cookTime", "furnaceCookTime");
            cookTimeTotalField = findField(furnaceTileClass, "cookTimeTotal", "totalCookTime");
            Class<?> worldClass = Class.forName("net.minecraft.server." + version + ".World");
            tileEntityListField = findField(worldClass, "tileEntityList", "loadedTileEntityList");
            Class<?> craftWorld = Class.forName("org.bukkit.craftbukkit." + version + ".CraftWorld");
            craftWorldGetHandle = craftWorld.getMethod("getHandle");
            // 1.8 and 1.9 furnaces compare cookTime against a hard-coded 200 and ignore any total
            // field that happens to exist, so claiming support there would silently do nothing.
            cookTimeReady = supportsTotalCookTime(version) && cookTimeField != null
                    && cookTimeTotalField != null && tileEntityListField != null;
            if (!supportsTotalCookTime(version)) {
                log.warning("Rotten Flesh to Leather: this server's furnace is fixed at 200 ticks "
                        + "(Minecraft " + version + "); smelt-time-ticks cannot be applied and "
                        + "vanilla timing is used");
            }
        } catch (Throwable t) {
            cookTimeReady = false;
            log.warning("Rotten Flesh to Leather: furnace cook-time API not available on " + version
                    + " (" + t + "); smelt-time-ticks will stay at vanilla");
        }

        try {
            nmsItemStackClass = Class.forName("net.minecraft.server." + version + ".ItemStack");
            Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
            asBukkitCopy = craftItemStack.getMethod("asBukkitCopy", nmsItemStackClass);
            recipesClass = Class.forName("net.minecraft.server." + version + ".RecipesFurnace");
            recipesInstance = recipesClass.getMethod("getInstance").invoke(null);
            experienceField = findField(recipesClass, "experience", "experienceList");
            if (experienceField == null) {
                // Spigot renames members between releases; locate the map that holds Floats instead
                // of relying on a name, then prove it works with a round trip below.
                experienceField = findFloatMapField(recipesClass, recipesInstance);
            }
            experienceReady = experienceField != null;
            if (experienceReady && !roundTripExperience()) {
                experienceReady = false;
                log.warning("Rotten Flesh to Leather: recipe experience API did not respond on "
                        + version + "; experience will stay at the server default");
            }
            if (!experienceReady) {
                log.warning("Rotten Flesh to Leather: recipe experience API not available on "
                        + version + " (fields: " + fieldSummary(recipesClass) + ")");
            }
        } catch (Throwable t) {
            experienceReady = false;
            log.warning("Rotten Flesh to Leather: recipe experience API not available on " + version
                    + " (" + t + "); experience will stay at the server default");
        }
    }

    /** 1.8 and 1.9 hard-code the furnace's cook time; the total field only became real in 1.10. */
    private static boolean supportsTotalCookTime(String nmsVersion) {
        return !(nmsVersion.startsWith("v1_8") || nmsVersion.startsWith("v1_9"));
    }

    /** The field on the recipes class that maps a result stack to its experience, found by type. */
    @SuppressWarnings("unchecked")
    private static Field findFloatMapField(Class<?> owner, Object instance) {
        for (Field field : owner.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(instance);
                if (!(value instanceof Map)) {
                    continue;
                }
                Map<Object, Object> map = (Map<Object, Object>) value;
                for (Object entryValue : map.values()) {
                    if (entryValue instanceof Float) {
                        return field;
                    }
                }
            } catch (Throwable ignored) {
                // not the map we are after
            }
        }
        return null;
    }

    /** Proves the experience map really stores and returns a value before the plugin relies on it. */
    private static boolean roundTripExperience() {
        ItemStack probe = new ItemStack(Material.STICK, 1);
        try {
            if (!setExperience(probe, 0.5F)) {
                return false;
            }
            return Math.abs(getExperience(probe) - 0.5F) < 0.0001F;
        } catch (Throwable t) {
            return false;
        } finally {
            removeExperience(probe);
        }
    }

    private static String fieldSummary(Class<?> owner) {
        StringBuilder out = new StringBuilder();
        for (Field field : owner.getDeclaredFields()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(field.getName()).append(':').append(field.getType().getSimpleName());
        }
        return out.toString();
    }

    private static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // try the next known name
            }
        }
        return null;
    }

    public static boolean cookTimeReady() {
        return cookTimeReady;
    }

    public static boolean experienceReady() {
        return experienceReady;
    }

    public static String serverVersion() {
        return version;
    }

    /** Every loaded furnace in this world, or an empty list when the bridge is unavailable. */
    @SuppressWarnings("unchecked")
    public static List<FurnaceTarget> furnaces(World world) {
        if (!cookTimeReady || world == null) {
            return Collections.emptyList();
        }
        try {
            Object handle = craftWorldGetHandle.invoke(world);
            Object listObject = tileEntityListField.get(handle);
            if (!(listObject instanceof List)) {
                return Collections.emptyList();
            }
            List<Object> tiles = (List<Object>) listObject;
            List<FurnaceTarget> targets = new ArrayList<FurnaceTarget>();
            for (Object tile : tiles) {
                if (furnaceTileClass.isInstance(tile)) {
                    targets.add(new NmsFurnace(tile, world.getName()));
                }
            }
            return targets;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    /** Sets the experience granted for this result stack. Returns false when unsupported. */
    @SuppressWarnings("unchecked")
    public static boolean setExperience(ItemStack result, float experience) {
        if (!experienceReady) {
            return false;
        }
        try {
            Object nms = asNmsCopy.invoke(null, result);
            Map<Object, Float> map = (Map<Object, Float>) experienceField.get(recipesInstance);
            // CraftBukkit keys the map by the full result stack (stack size included), so a reload
            // that changes output-count would otherwise leave the old entry behind and make the
            // granted XP depend on which stack size the furnace happens to produce. Keep exactly
            // one entry per result item.
            java.util.Iterator<Map.Entry<Object, Float>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Object key = it.next().getKey();
                if (key == null) {
                    continue;
                }
                ItemStack asBukkit = (ItemStack) asBukkitCopy.invoke(null, key);
                if (asBukkit != null && asBukkit.getType() == result.getType()) {
                    it.remove();
                }
            }
            map.put(nms, Float.valueOf(experience));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Removes every experience entry for this result item. */
    @SuppressWarnings("unchecked")
    private static void removeExperience(ItemStack result) {
        if (!experienceReady) {
            return;
        }
        try {
            Map<Object, Float> map = (Map<Object, Float>) experienceField.get(recipesInstance);
            java.util.Iterator<Map.Entry<Object, Float>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Object key = it.next().getKey();
                if (key == null) {
                    continue;
                }
                ItemStack asBukkit = (ItemStack) asBukkitCopy.invoke(null, key);
                if (asBukkit != null && asBukkit.getType() == result.getType()) {
                    it.remove();
                }
            }
        } catch (Throwable ignored) {
            // nothing to clean up
        }
    }

    /** Experience the server currently holds for this result stack, or -1 when unsupported. */
    @SuppressWarnings("unchecked")
    public static float getExperience(ItemStack result) {
        if (!experienceReady) {
            return -1.0F;
        }
        try {
            Object nms = asNmsCopy.invoke(null, result);
            Map<Object, Float> map = (Map<Object, Float>) experienceField.get(recipesInstance);
            for (Map.Entry<Object, Float> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (key == null) {
                    continue;
                }
                ItemStack asBukkit = (ItemStack) asBukkitCopy.invoke(null, key);
                if (asBukkit != null && asBukkit.getType() == result.getType()) {
                    return entry.getValue().floatValue();
                }
            }
            return 0.0F;
        } catch (Throwable t) {
            return -1.0F;
        }
    }

    /** One loaded furnace tile entity, wrapped for the shared {@code CookTimeTracker}. */
    private static final class NmsFurnace implements FurnaceTarget {

        private final Object tile;
        private final String where;

        NmsFurnace(Object tile, String world) {
            this.tile = tile;
            this.where = world + "@" + describePosition();
        }

        private String describePosition() {
            try {
                Method getPos = tile.getClass().getMethod("getPosition");
                return String.valueOf(getPos.invoke(tile));
            } catch (Throwable t) {
                return "?";
            }
        }

        @Override
        public Object identity() {
            return tile;
        }

        @Override
        public boolean inputIsRecipeInput() {
            try {
                Method getItem = tile.getClass().getMethod("getItem", int.class);
                Object nmsStack = getItem.invoke(tile, Integer.valueOf(0));
                if (nmsStack == null) {
                    return false;
                }
                Object bukkit = asBukkitCopy.invoke(null, nmsStack);
                return bukkit instanceof ItemStack && ((ItemStack) bukkit).getType() == inputMaterial;
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public int cookTime() {
            try {
                return cookTimeField.getInt(tile);
            } catch (Throwable t) {
                return 0;
            }
        }

        @Override
        public int totalCookTime() {
            try {
                int value = cookTimeTotalField.getInt(tile);
                return value <= 0 ? 200 : value;
            } catch (Throwable t) {
                return 200;
            }
        }

        @Override
        public void setTotalCookTime(int ticks) {
            try {
                cookTimeTotalField.setInt(tile, ticks);
            } catch (Throwable ignored) {
                // bridge already reported unsupported; vanilla timing remains
            }
        }

        @Override
        public String describe() {
            return where;
        }
    }
}
