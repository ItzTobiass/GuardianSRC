package org.mrqendyxz.guardianv3.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.mrqendyxz.guardianv3.Guardianv3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ChangelogCommand implements CommandExecutor {
    private static final int PAGE_SIZE = 6;
    private final List<String> entries;

    public ChangelogCommand() {
        try (var stream = Guardianv3.getInstance().getResource("changelog.txt")) {
            if (stream == null) throw new IllegalStateException("Missing bundled changelog.txt");
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                entries = reader.lines().filter(line -> !line.isBlank()).toList();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read bundled changelog", ex);
        }
        if (entries.isEmpty()) throw new IllegalStateException("Bundled changelog is empty");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("guardian.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1 || args.length > 2 || !args[0].equalsIgnoreCase("changelog")) {
            sender.sendMessage("§c§lGuardian §8» §7Usage: §f/guardian changelog [page]");
            return true;
        }
        int pages = (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int page = 1;
        try {
            if (args.length == 2) page = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            page = 0;
        }
        if (page < 1 || page > pages) {
            sender.sendMessage("§cPlease choose a changelog page from 1 to " + pages + ".");
            return true;
        }
        sender.sendMessage("§8§m--------------------------------------");
        sender.sendMessage("§c§lGuardian Anticheat §7- §fChangelog v"
                + Guardianv3.getInstance().getDescription().getVersion() + " §8(§7" + page + "/" + pages + "§8)");
        int start = (page - 1) * PAGE_SIZE;
        for (String entry : entries.subList(start, Math.min(start + PAGE_SIZE, entries.size()))) {
            int colon = entry.indexOf(':');
            sender.sendMessage(colon < 0 ? "§8» §7" + entry
                    : "§8» §b" + entry.substring(0, colon + 1) + "§7" + entry.substring(colon + 1));
        }
        if (page < pages) sender.sendMessage("§7Next page: §f/guardian changelog " + (page + 1));
        sender.sendMessage("§7Usage: §f/guardian changelog [1-" + pages + "]");
        sender.sendMessage("§8§m--------------------------------------");
        return true;
    }
}
