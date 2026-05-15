package org.mrqendyxz.guardianv3.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class InfoCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            sender.sendMessage("§8§m---------------------------------------");
            sender.sendMessage("§c§lGuardian AntiCheat §7(v1.1.5)");
            sender.sendMessage("§7Status: §aRunning");
            sender.sendMessage("§7Developer: §fMrQendyxz");
            sender.sendMessage("§7Engine: §fPacketEvents 2.0");
            sender.sendMessage("§7Protection: §aActive");
            sender.sendMessage("§7Website: §fguardianac.netlify.app");
            sender.sendMessage("§7Support: §fdsc.gg/guardiananticheat");
            sender.sendMessage("§7Big Thanks to: §fYou for support!");
            sender.sendMessage("§8§m---------------------------------------");
            return true;
        }

        sender.sendMessage("§cUsage: /guardian info");
        return true;
    }
}