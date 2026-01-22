package org.mrqendyxz.guardianv3.Commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Guardianv3;

public class FreezeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("guardian.freeze")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /guardian freeze <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }

        Guardianv3.getInstance().getFreezeManager().toggleFreeze(target);
        sender.sendMessage("§aFreeze status updated for " + target.getName());
        return true;
    }
}