package org.mrqendyxz.guardianv3.Commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Guardianv3;
import org.mrqendyxz.guardianv3.Utils.ModScanner;

public class ScanCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("guardian.scan")) {
            sender.sendMessage("§c§lGuardian §8» §7No permission.");
            return true;
        }
        if (!Guardianv3.getInstance().getConfig().getBoolean("scanner.enabled", true)) {
            sender.sendMessage("§c§lGuardian §8» §7Scanner is disabled in config.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§c§lGuardian §8» §7Usage: §f/guardian scan <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§c§lGuardian §8» §7Player not found.");
            return true;
        }
        var mods = ModScanner.scan(target.getUniqueId());
        for (String line : ModScanner.format(target, mods)) sender.sendMessage(line);
        if (Guardianv3.getInstance().getConfig().getBoolean("scanner.enforce-on-command", false)) {
            ModScanner.enforce(target, mods);
        }
        return true;
    }
}
