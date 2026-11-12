package com.rottenleather.bukkit;

import com.rottenleather.core.BuildDefaults;
import com.rottenleather.core.CookTimeTracker;
import com.rottenleather.core.FurnaceTarget;
import com.rottenleather.core.RecipeSpec;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Bukkit/Spigot/Paper entry point. Registers the vanilla furnace recipe, keeps a non-vanilla cook
 * time applied to furnaces holding this recipe, and reports the recipe fingerprint to a proxy.
 */
public final class RottenLeatherPlugin extends JavaPlugin implements Listener {

    public static final String CHANNEL = "rottenleather:hello";
    private static final String VERSION = "1.0.0";

    private final CookTimeTracker tracker = new CookTimeTracker();
    private RecipeSpec spec;
    private BukkitTask cookTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        spec = BukkitConfig.read(getConfig(), getLogger());
        NmsBridge.init(getLogger(), Materials.resolve(spec.inputItem()));
        BukkitRecipeInstaller.install(spec, getLogger(), BukkitConfig.logRegistration(getConfig()));
        if (NmsBridge.experienceReady()) {
            NmsBridge.setExperience(BukkitRecipeInstaller.resultFor(spec), spec.experience());
        }
        getCommand("rottenleather").setExecutor(new BukkitCommand(this));
        Bukkit.getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        startCookTask();
        getLogger().info("Rotten Flesh to Leather " + VERSION + " active for " + Bukkit.getBukkitVersion()
                + ": " + spec.summary() + " registered=" + BukkitRecipeInstaller.isRegistered());
    }

    @Override
    public void onDisable() {
        if (cookTask != null) {
            cookTask.cancel();
            cookTask = null;
        }
        tracker.releaseAll();
    }

    private void startCookTask() {
        if (cookTask != null) {
            cookTask.cancel();
        }
        if (spec.isVanillaSmeltTime() || !NmsBridge.cookTimeReady()) {
            cookTask = null;
            return;
        }
        cookTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                tickFurnaces();
            }
        }, 20L, 8L);
    }

    private void tickFurnaces() {
        RecipeSpec current = spec;
        if (current.isVanillaSmeltTime()) {
            tracker.releaseAll();
            return;
        }
        try {
            for (World world : Bukkit.getWorlds()) {
                List<FurnaceTarget> furnaces = NmsBridge.furnaces(world);
                for (FurnaceTarget target : furnaces) {
                    tracker.adjust(target, current.smeltTimeTicks());
                }
            }
        } catch (Throwable t) {
            getLogger().warning("Rotten Flesh to Leather: cook-time adjustment failed: " + t);
        }
    }

    /** Re-reads config.yml; cook time and experience apply live, item changes need a restart. */
    public void reloadPlugin() {
        RecipeSpec previous = spec;
        reloadConfig();
        spec = BukkitConfig.read(getConfig(), getLogger());
        getLogger().info("Rotten Flesh to Leather: reloaded - " + spec.summary());
        if (!previous.inputItem().equals(spec.inputItem())
                || !previous.outputItem().equals(spec.outputItem())
                || previous.outputCount() != spec.outputCount()) {
            getLogger().warning("Rotten Flesh to Leather: recipe item/count changes need a server restart "
                    + "on Bukkit 1.8-1.12.2 (no per-recipe removal API); keeping the registered item/count "
                    + "and applying the new experience and cook time only");
            spec = new RecipeSpec(previous.inputItem(), previous.outputItem(), previous.outputCount(),
                    spec.experience(), spec.smeltTimeTicks(), spec.conflictPolicy());
        }
        tracker.releaseAll();
        NmsBridge.setExperience(BukkitRecipeInstaller.resultFor(spec), spec.experience());
        startCookTask();
    }

    public RecipeSpec spec() {
        return spec;
    }

    public int adjustedFurnaces() {
        return tracker.trackedCount();
    }

    public String statusLine() {
        return "recipe=" + spec.inputItem() + "->" + spec.outputItem() + " x" + spec.outputCount()
                + " xp=" + spec.experience() + " ticks=" + spec.smeltTimeTicks()
                + " policy=" + spec.conflictPolicy().key()
                + " fingerprint=" + spec.fingerprint()
                + " platform=Bukkit/" + Bukkit.getBukkitVersion()
                + " nms=" + NmsBridge.serverVersion()
                + " registered=" + BukkitRecipeInstaller.isRegistered()
                + " install=" + BukkitRecipeInstaller.detail()
                + " nmsXp=" + experienceReport()
                + " customCookTime=" + NmsBridge.cookTimeReady()
                + " adjustedFurnaces=" + tracker.trackedCount();
    }

    private float experienceReport() {
        if (!NmsBridge.experienceReady()) {
            return -1.0F;
        }
        return NmsBridge.getExperience(BukkitRecipeInstaller.resultFor(spec));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sendFingerprint(event.getPlayer());
    }

    private void sendFingerprint(Player player) {
        if (!getConfig().getBoolean("network.report-to-proxy", true)) {
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("RFL1");
            out.writeUTF(getConfig().getString("network.server-name", "unknown"));
            out.writeUTF(spec.fingerprint());
            out.writeUTF(spec.summary());
            out.writeBoolean(BukkitRecipeInstaller.isRegistered());
            player.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            getLogger().warning("Rotten Flesh to Leather: could not report recipe to the proxy: " + e);
        }
    }
}
