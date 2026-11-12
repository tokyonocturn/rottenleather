package com.rottenleather.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BungeeCord/Waterfall module.
 *
 * <p>A proxy has no world and no furnace, so it cannot smelt anything and this module does not
 * pretend to. What it can do is catch the failure mode that actually matters on a network: one
 * backend running a different recipe from the others, so players see rotten flesh turn into leather
 * on one server and stay rotten on another. Backend Bukkit servers running this project announce
 * their recipe fingerprint over {@code rottenleather:hello} when a player joins; this module collects
 * those announcements, flags mismatches in the console and exposes them through {@code /rflnet}.
 */
public final class RottenLeatherBungee extends Plugin implements Listener {

    public static final String CHANNEL = "rottenleather:hello";

    private final Map<String, Report> reports = new ConcurrentHashMap<String, Report>();
    private final Set<String> mismatches = new TreeSet<String>();

    @Override
    public void onEnable() {
        getProxy().registerChannel(CHANNEL);
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new NetworkCommand());
        getLogger().info("Rotten Flesh to Leather network bridge enabled on "
                + getProxy().getVersion() + "; waiting for backend recipe reports on " + CHANNEL);
    }

    @Override
    public void onDisable() {
        reports.clear();
        mismatches.clear();
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getTag())) {
            return;
        }
        // Only backend servers may report; a client must not be able to spoof a recipe fingerprint.
        if (!(event.getSender() instanceof Server)) {
            return;
        }
        Server backend = (Server) event.getSender();
        event.setCancelled(true);
        Report report = parse(event.getData());
        if (report == null) {
            getLogger().warning("Rotten Flesh to Leather: ignored a malformed recipe report from "
                    + backend.getInfo().getName());
            return;
        }
        Report previous = reports.put(report.server, report);
        if (previous == null) {
            getLogger().info("Rotten Flesh to Leather: " + report.server + " reports "
                    + report.fingerprint.substring(0, 12) + "... (" + report.summary + ")");
        }
        recomputeMismatches();
    }

    private Report parse(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            String magic = in.readUTF();
            if (!"RFL1".equals(magic)) {
                return null;
            }
            String server = in.readUTF();
            String fingerprint = in.readUTF();
            String summary = in.readUTF();
            boolean registered = in.readBoolean();
            if (server.isEmpty() || fingerprint.length() != 64) {
                return null;
            }
            return new Report(server, fingerprint, summary, registered);
        } catch (IOException e) {
            return null;
        }
    }

    private void recomputeMismatches() {
        mismatches.clear();
        String expected = null;
        for (Report report : reports.values()) {
            if (!report.registered) {
                continue;
            }
            if (expected == null) {
                expected = report.fingerprint;
            } else if (!expected.equals(report.fingerprint)) {
                mismatches.add(report.server);
            }
        }
        if (!mismatches.isEmpty()) {
            getLogger().warning("Rotten Flesh to Leather: recipe mismatch on " + mismatches
                    + " - players will see different smelting results between servers");
        }
    }

    private static final class Report {
        final String server;
        final String fingerprint;
        final String summary;
        final boolean registered;

        Report(String server, String fingerprint, String summary, boolean registered) {
            this.server = server;
            this.fingerprint = fingerprint;
            this.summary = summary;
            this.registered = registered;
        }
    }

    /** {@code /rflnet} - operator view of what each backend reported. */
    private final class NetworkCommand extends Command {

        NetworkCommand() {
            super("rflnet", "rottenleather.admin");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            sender.sendMessage("Rotten Flesh to Leather network report:");
            if (reports.isEmpty()) {
                sender.sendMessage("  no backend has reported yet (a player must join a backend)");
                return;
            }
            TreeMap<String, Report> sorted = new TreeMap<String, Report>(reports);
            for (Report report : sorted.values()) {
                sender.sendMessage("  " + report.server + ": "
                        + (report.registered ? report.fingerprint.substring(0, 12) + "..." : "not installed")
                        + " (" + report.summary + ")");
            }
            sender.sendMessage(mismatches.isEmpty()
                    ? "  consistent: every backend reports the same recipe"
                    : "  MISMATCH on " + mismatches);
        }
    }
}
