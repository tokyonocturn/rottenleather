package com.rottenleather.bukkit;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** {@code /rottenleather status|reload}. */
public final class BukkitCommand implements CommandExecutor {

    private final RottenLeatherPlugin plugin;

    public BukkitCommand(RottenLeatherPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rottenleather.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        if ("reload".equals(sub)) {
            plugin.reloadPlugin();
            sender.sendMessage("Rotten Flesh to Leather: reloaded - " + plugin.statusLine());
            return true;
        }
        if (!"status".equals(sub)) {
            sender.sendMessage("Usage: /" + label + " <status|reload>");
            return true;
        }
        sender.sendMessage("Rotten Flesh to Leather: " + plugin.statusLine());
        return true;
    }
}
