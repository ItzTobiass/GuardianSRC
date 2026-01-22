package org.mrqendyxz.guardianv3;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.mrqendyxz.guardianv3.Commands.ClientCommand;
import org.mrqendyxz.guardianv3.Commands.FreezeCommand;
import org.mrqendyxz.guardianv3.Commands.InfoCommand;
import org.mrqendyxz.guardianv3.Managers.CheckManager;
import org.mrqendyxz.guardianv3.Managers.FreezeManager;
import org.mrqendyxz.guardianv3.Utils.FreezeListener;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Guardianv3 extends JavaPlugin {

    private static Guardianv3 instance;
    private FreezeManager freezeManager;

    @Override
    public void onLoad() {
        instance = this;
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        updateConfig();
        PacketEvents.getAPI().init();

        this.freezeManager = new FreezeManager();
        getServer().getPluginManager().registerEvents(new FreezeListener(freezeManager), this);

        new CheckManager().register();

        InfoCommand infoCmd = new InfoCommand();
        ClientCommand clientCmd = new ClientCommand();
        FreezeCommand freezeCmd = new FreezeCommand();

        if (getCommand("guardian") != null) {
            getCommand("guardian").setExecutor((sender, command, label, args) -> {
                if (args.length > 0) {
                    String sub = args[0].toLowerCase();
                    switch (sub) {
                        case "client": return clientCmd.onCommand(sender, command, label, args);
                        case "info": return infoCmd.onCommand(sender, command, label, args);
                        case "freeze":
                            if (args.length > 1) {
                                Player target = Bukkit.getPlayer(args[1]);
                                if (target != null && target.isOp()) {
                                    sender.sendMessage("§c§lGuardian §8» §7You cannot freeze players with OP!");
                                    return true;
                                }
                            }
                            return freezeCmd.onCommand(sender, command, label, args);
                        case "reload":
                            if (!sender.hasPermission("guardian.admin")) {
                                sender.sendMessage("§c§lGuardian §8» §7No permission.");
                                return true;
                            }
                            reloadConfig();
                            sender.sendMessage("§c§lGuardian §8» §fConfiguration reloaded!");
                            return true;
                    }
                }
                sender.sendMessage("§c§lGuardian §8» §7Usage: §f/guardian <info|client|freeze|reload>");
                return true;
            });

            getCommand("guardian").setTabCompleter((sender, command, alias, args) -> {
                if (args.length == 1) {
                    List<String> options = Arrays.asList("info", "client", "freeze", "reload");
                    return options.stream()
                            .filter(s -> s.startsWith(args[0].toLowerCase()))
                            .collect(Collectors.toList());
                }
                return new ArrayList<>();
            });
        }

        getLogger().info("GuardianV3 enabled - developed by MrQendyxz");
    }

    private void updateConfig() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        InputStream is = getResource("config.yml");
        if (is == null) return;

        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
        boolean changed = false;

        for (String key : defaultConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defaultConfig.get(key));
                changed = true;
            }
        }

        if (changed) {
            saveConfig();
            getLogger().info("Config updated with new values.");
        }
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
    }

    public static Guardianv3 getInstance() { return instance; }
    public FreezeManager getFreezeManager() { return freezeManager; }
}