package com.rottenleather.forge;

import com.rottenleather.core.BuildDefaults;
import com.rottenleather.core.CookTimeTracker;
import com.rottenleather.core.FurnaceTarget;
import com.rottenleather.core.RecipeSpec;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Applies a non-vanilla cook time to furnaces that are cooking this recipe.
 *
 * <p>1.8-1.12.2 has no per-recipe cook time, so the furnace's own {@code totalCookTime} is adjusted
 * while this recipe's input is in the input slot, and restored when it is not. Only furnaces that
 * hold this recipe's input are touched, so every other recipe keeps vanilla timing. With the default
 * 200 ticks this listener does no work at all.
 */
public final class CookTimeController {

    private static final Logger LOG = LogManager.getLogger(BuildDefaults.MOD_ID);
    private static final CookTimeTracker TRACKER = new CookTimeTracker();
    private static boolean failureLogged;
    private static boolean fixedTimeLogged;

    private CookTimeController() {
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        RecipeSpec spec = RottenLeatherMod.spec();
        if (!VersionAdapterImpl.SUPPORTS_CUSTOM_COOK_TIME) {
            if (spec != null && !spec.isVanillaSmeltTime() && !fixedTimeLogged) {
                fixedTimeLogged = true;
                LOG.warn("Rotten Flesh to Leather: this Minecraft version's furnace is fixed at 200 "
                        + "ticks, so smeltTimeTicks={} cannot be applied; smelting uses vanilla timing",
                        spec.smeltTimeTicks());
            }
            return;
        }
        if (spec == null || spec.isVanillaSmeltTime() || !RottenLeatherMod.config().adjustCookTime()) {
            if (TRACKER.trackedCount() > 0) {
                TRACKER.releaseAll();
            }
            return;
        }
        World world = event.world;
        if (world == null || world.isRemote) {
            return;
        }
        // Furnace cook time is only read when it completes, so scanning every 8 ticks is enough even
        // for a 20-tick recipe; it also keeps large modpacks cheap.
        if ((world.getTotalWorldTime() & 7L) != 0L) {
            return;
        }
        try {
            for (FurnaceTarget target : VersionAdapterImpl.furnacesIn(world)) {
                TRACKER.adjust(target, spec.smeltTimeTicks());
            }
        } catch (Throwable t) {
            if (!failureLogged) {
                failureLogged = true;
                LOG.warn("Rotten Flesh to Leather: cook-time adjustment failed; smelting still works at "
                        + "vanilla timing", t);
            }
        }
    }

    public static int adjustedFurnaces() {
        return TRACKER.trackedCount();
    }

    public static void releaseAll() {
        TRACKER.releaseAll();
    }
}
