package org.mrqendyxz.guardianv3.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class InfoCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            sender.sendMessage("§8§m---------------------------------------");
            sender.sendMessage("§c§lGuardian AntiCheat §7(v" + org.mrqendyxz.guardianv3.Guardianv3.getInstance().getDescription().getVersion() + ")");
            sender.sendMessage("§7Status: §aRunning");
            sender.sendMessage("§7Developer: §fItzTobiass");
            var engine = org.bukkit.Bukkit.getPluginManager().getPlugin("packetevents");
            sender.sendMessage("§7Engine: §fPacketEvents " + (engine == null ? "unavailable" : engine.getDescription().getVersion()));
            sender.sendMessage("§7Protection: §aActive");
            sender.sendMessage("§7Website: §fguardianac.netlify.app");
            sender.sendMessage("§7Support: §f.gg/EnMjPXb97Q");
            sender.sendMessage("§7Big Thanks to: §fHonzasikCZ - Biggest Donator!");
            sender.sendMessage("§8§m---------------------------------------");
            return true;
        }

        sender.sendMessage("§cUsage: /guardian info");
        return true;
    }
}