package org.mrqendyxz.guardianv3.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ChangelogCommand implements CommandExecutor {

    public static final String B_MOVEMENT_7_FIXED_SPEED = "§8» §bMovement: §7Fixed Speed .";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("guardian.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("changelog")) {
            sendChangelog(sender);
            return true;
        }

        sender.sendMessage("§c§lGuardian §8» §7Usage: §f/guardian changelog");
        return true;
    }

    private void sendChangelog(CommandSender sender) {
        sender.sendMessage("§8§m--------------------------------------");
        sender.sendMessage("§c§lGuardian Anticheat §7- §fChangelog v1.1.7-Alpha");
        sender.sendMessage("");
        sender.sendMessage("§8» §bCombat: §7Disabled KillAura Check.");
        sender.sendMessage("§8» §bMovement: §7FlyCheck bug fixed.");
        sender.sendMessage("§8» §bWorld: §7Recoded ScaffoldCheck.");
        sender.sendMessage("§8» §bCommands: §7None.");
        sender.sendMessage("§8» §bOther: §7None.");
        sender.sendMessage("");
        sender.sendMessage("§8§m--------------------------------------");
    }
}