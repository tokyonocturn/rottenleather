package com.rottenleather.forge;

/**
 * Command body shared by every release. The {@code ICommand} interface itself was renamed in 1.12
 * ({@code getCommandName/processCommand} became {@code getName/execute}), so each version generates a
 * thin {@code VersionCommand} that forwards here.
 */
public final class CommandSupport {

    private CommandSupport() {
    }

    public static String usage() {
        return "/rottenleather <status|reload>";
    }

    /** Runs one subcommand and returns the line to send back to the sender. */
    public static String execute(String[] args) {
        String sub = args == null || args.length == 0 ? "status" : args[0].toLowerCase();
        if ("reload".equals(sub)) {
            RottenLeatherMod.reload();
            return "Rotten Flesh to Leather: reloaded - " + RottenLeatherMod.statusLine();
        }
        if (!"status".equals(sub)) {
            return "Usage: " + usage();
        }
        return "Rotten Flesh to Leather: " + RottenLeatherMod.statusLine();
    }
}
