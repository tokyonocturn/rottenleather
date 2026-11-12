package com.rottenleather.test;

import com.rottenleather.core.BuildDefaults;
import com.rottenleather.forge.RottenLeatherMod;
import com.rottenleather.forge.VersionAdapterImpl;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Compile-time and classpath contract for one Forge release. This does not boot Minecraft; the live
 * server probe does that. It proves the version's mapped classes really expose the fields and
 * methods the shared code uses, and that the {@code @Mod} entry is wired correctly.
 */
public final class ForgeSuite {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        String mc = System.getProperty("rottenleather.mc", "unknown");

        check("adapter.mcVersion", mc, VersionAdapterImpl.MC_VERSION);

        Class<?> entry = Class.forName("com.rottenleather.forge.VersionEntry");
        Mod mod = entry.getAnnotation(Mod.class);
        checkTrue("entry.annotation", mod != null);
        check("entry.modid", BuildDefaults.MOD_ID, mod.modid());
        check("entry.name", RottenLeatherMod.NAME, mod.name());
        check("entry.version", RottenLeatherMod.VERSION, mod.version());
        check("entry.acceptedMc", "[" + mc + "]", mod.acceptedMinecraftVersions());
        check("entry.remoteVersions", "*", mod.acceptableRemoteVersions());

        int handlers = 0;
        for (Method method : entry.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Mod.EventHandler.class)) {
                handlers++;
                checkTrue("entry.handlerParams." + method.getName(), method.getParameterCount() == 1);
            }
        }
        check("entry.handlers", 4, handlers);

        Field cookTime = TileEntityFurnace.class.getDeclaredField("cookTime");
        Field totalCookTime = TileEntityFurnace.class.getDeclaredField("totalCookTime");
        check("furnace.cookTimeType", int.class, cookTime.getType());
        check("furnace.totalCookTimeType", int.class, totalCookTime.getType());
        checkTrue("furnace.cookTimePrivate", java.lang.reflect.Modifier.isPrivate(cookTime.getModifiers()));
        checkTrue("furnace.totalCookTimePrivate",
                java.lang.reflect.Modifier.isPrivate(totalCookTime.getModifiers()));

        Field loaded = World.class.getDeclaredField("loadedTileEntityList");
        checkTrue("world.loadedTileEntityListPublic", java.lang.reflect.Modifier.isPublic(loaded.getModifiers()));

        checkTrue("recipes.smeltingList", FurnaceRecipes.class.getMethod("getSmeltingList") != null);
        checkTrue("recipes.experience",
                FurnaceRecipes.class.getMethod("getSmeltingExperience", ItemStack.class) != null);
        checkTrue("registry.addSmelting", GameRegistry.class.getMethod("addSmelting",
                ItemStack.class, ItemStack.class, float.class) != null);
        checkTrue("item.byNameOrId", net.minecraft.item.Item.class.getMethod("getByNameOrId", String.class) != null);

        checkTrue("reflection.get", ObfuscationReflectionHelper.class.getMethod("getPrivateValue",
                Class.class, Object.class, String[].class) != null);
        checkTrue("reflection.set", ObfuscationReflectionHelper.class.getMethod("setPrivateValue",
                Class.class, Object.class, Object.class, String[].class) != null);

        int subscribed = 0;
        for (Method method : com.rottenleather.forge.CookTimeController.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SubscribeEvent.class)) {
                subscribed++;
            }
        }
        check("controller.subscribeEvents", 1, subscribed);

        System.out.println("FORGE_SUITE mc=" + mc + " pass=" + passed + " fail=" + failed);
        if (failed > 0) {
            System.exit(1);
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

    private static void checkTrue(String name, boolean condition) {
        if (condition) {
            pass(name);
        } else {
            fail(name, "condition was false");
        }
    }
}
