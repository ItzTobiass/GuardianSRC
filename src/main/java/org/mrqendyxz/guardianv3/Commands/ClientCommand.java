package org.mrqendyxz.guardianv3.Commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.ClientBrandListener;

public class ClientCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("guardian.client")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /guardian client <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }

        String brand = ClientBrandListener.getFormattedBrand(target.getUniqueId());
        sender.sendMessage("§c§lGuardian §8» §f" + target.getName() + " §7is using: " + brand);

        return true;
    }
}