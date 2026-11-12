package com.rottenleather.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Velocity module. Like the BungeeCord/Waterfall module, a proxy cannot smelt, so this provides the
 * integration a proxy can actually honour: it collects each backend's recipe fingerprint and warns
 * when a network is serving two different recipes.
 *
 * <p>Plugin messages from backends are only delivered when the channel is registered here.
 */
public final class RottenLeatherVelocity {

    private static final String CHANNEL_NAME = "hello";
    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create("rottenleather", CHANNEL_NAME);

    private final ProxyServer server;
    private final Logger logger;
    private final Map<String, Report> reports = new ConcurrentHashMap<String, Report>();
    private final Set<String> mismatches = new TreeSet<String>();

    @Inject
    public RottenLeatherVelocity(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(CHANNEL);
        // The plugin main instance is registered with the event manager automatically.
        CommandMeta meta = server.getCommandManager().metaBuilder("rflnet")
                .plugin(this)
                .build();
        server.getCommandManager().register(meta, new NetworkCommand());
        logger.info("Rotten Flesh to Leather network bridge enabled; waiting for backend recipe "
                + "reports on " + CHANNEL.getId());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }
        ServerConnection connection = (ServerConnection) event.getSource();
        Report report = parse(event.getData());
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (report == null) {
            logger.warn("Rotten Flesh to Leather: ignored a malformed recipe report from {}",
                    connection.getServerInfo().getName());
            return;
        }
        if (reports.put(report.server, report) == null) {
            logger.info("Rotten Flesh to Leather: {} reports {}... ({})", report.server,
                    report.fingerprint.substring(0, 12), report.summary);
        }
        recomputeMismatches();
    }

    private Report parse(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            if (!"RFL1".equals(in.readUTF())) {
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
            logger.warn("Rotten Flesh to Leather: recipe mismatch on {} - players will see different "
                    + "smelting results between servers", mismatches);
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

    private final class NetworkCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            source.sendPlainMessage("Rotten Flesh to Leather network report:");
            if (reports.isEmpty()) {
                source.sendPlainMessage("  no backend has reported yet (a player must join a backend)");
                return;
            }
            for (Report report : new TreeMap<String, Report>(reports).values()) {
                source.sendPlainMessage("  " + report.server + ": "
                        + (report.registered ? report.fingerprint.substring(0, 12) + "..." : "not installed")
                        + " (" + report.summary + ")");
            }
            source.sendPlainMessage(mismatches.isEmpty()
                    ? "  consistent: every backend reports the same recipe"
                    : "  MISMATCH on " + mismatches);
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("rottenleather.admin");
        }
    }
}
