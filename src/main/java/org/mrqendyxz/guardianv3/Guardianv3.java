package org.mrqendyxz.guardianv3;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.mrqendyxz.guardianv3.Commands.ClientCommand;
import org.mrqendyxz.guardianv3.Commands.FreezeCommand;
import org.mrqendyxz.guardianv3.Commands.InfoCommand;
import org.mrqendyxz.guardianv3.Commands.ChangelogCommand;
import org.mrqendyxz.guardianv3.Commands.ScanCommand;
import org.mrqendyxz.guardianv3.Utils.ModScanner;
import org.mrqendyxz.guardianv3.Managers.CheckManager;
import org.mrqendyxz.guardianv3.Managers.FreezeManager;
import org.mrqendyxz.guardianv3.Utils.ClientBrandListener;
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
    private CheckManager checkManager;
    private boolean ownsPacketEvents;
    private Metrics metrics;

    @Override
    public void onLoad() {
        instance = this;
        if (PacketEvents.getAPI() == null) {
            PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
            PacketEvents.getAPI().load();
            ownsPacketEvents = true;
        }
    }

    @Override
    public void onEnable() {
        updateConfig();
        if (ownsPacketEvents) {
            PacketEvents.getAPI().init();
        }

        ClientBrandListener brands = new ClientBrandListener();
        Bukkit.getPluginManager().registerEvents(brands, this);
        PacketEvents.getAPI().getEventManager().registerListener(brands, PacketListenerPriority.LOWEST);
        ModScanner scanner = new ModScanner();
        Bukkit.getPluginManager().registerEvents(scanner, this);
        PacketEvents.getAPI().getEventManager().registerListener(scanner, PacketListenerPriority.LOWEST);

        this.freezeManager = new FreezeManager();
        getServer().getPluginManager().registerEvents(new FreezeListener(freezeManager), this);

        checkManager = new CheckManager();
        checkManager.register();

        InfoCommand infoCmd = new InfoCommand();
        ClientCommand clientCmd = new ClientCommand();
        FreezeCommand freezeCmd = new FreezeCommand();
        ChangelogCommand changelogCmd = new ChangelogCommand();
        ScanCommand scanCmd = new ScanCommand();

        if (getCommand("guardian") != null) {
            getCommand("guardian").setExecutor((sender, command, label, args) -> {
                if (args.length > 0) {
                    String sub = args[0].toLowerCase(java.util.Locale.ROOT);
                    switch (sub) {
                        case "scan": return scanCmd.onCommand(sender, command, label, args);
                        case "client": return clientCmd.onCommand(sender, command, label, args);
                        case "info": return infoCmd.onCommand(sender, command, label, args);
                        case "changelog": return changelogCmd.onCommand(sender, command, label, args);
                        case "freeze":
                            return freezeCmd.onCommand(sender, command, label, args);
                        case "reload":
                            if (!sender.hasPermission("guardian.admin")) {
                                sender.sendMessage("§c§lGuardian §8» §7No permission.");
                                return true;
                            }
                            reloadConfig();
                            updateConfig();
                            sender.sendMessage("§c§lGuardian §8» §fConfiguration reloaded!");
                            return true;
                    }
                }
                sender.sendMessage("§c§lGuardian §8» §7Usage: §f/guardian <info|client|scan|freeze|reload|changelog>");
                return true;
            });

            getCommand("guardian").setTabCompleter((sender, command, alias, args) -> {
                if (args.length == 1) {
                    List<String> options = Arrays.asList("info", "client", "scan", "freeze", "reload", "changelog");
                    return options.stream()
                            .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                            .collect(Collectors.toList());
                }
                if (args.length == 2 && (args[0].equalsIgnoreCase("freeze") || args[0].equalsIgnoreCase("scan"))) {
                    return null;
                }
                return new ArrayList<>();
            });
        }

        try {
            metrics = new Metrics(this, 35058);
        } catch (RuntimeException ex) {
            getLogger().log(java.util.logging.Level.WARNING, "Could not initialize bStats metrics", ex);
        }

        getLogger().info("GuardianV3 enabled - developed by MrQendyxz");
    }

    private void updateConfig() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        InputStream is = getResource("config.yml");
        if (is == null) return;

        FileConfiguration defaultConfig;
        try (var reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            defaultConfig = YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException ex) {
            getLogger().log(java.util.logging.Level.WARNING, "Cannot read bundled config defaults", ex);
            return;
        }
        boolean changed = false;

        for (String key : defaultConfig.getKeys(true)) {
            if (!config.contains(key, true)) {
                config.set(key, defaultConfig.get(key));
                changed = true;
            }
        }

        String type = config.getString("punishments.type", "NONE").trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("NONE", "KICK", "BAN").contains(type)) {
            getLogger().warning("Invalid punishments.type: " + type + "; punishments disabled until corrected.");
            config.set("punishments.type", "NONE");
        }
        String scanAction;
        if (config.getConfigurationSection("scanner.mods") != null) {
            for (String key : config.getConfigurationSection("scanner.mods").getKeys(false)) {
                scanAction = config.getString("scanner.mods." + key, "NONE").trim().toUpperCase(java.util.Locale.ROOT);
                if (!java.util.Set.of("NONE", "ALERT", "KICK", "BAN").contains(scanAction)) {
                    getLogger().warning("Invalid scanner action for mod " + key + ": " + scanAction + " (use NONE, ALERT, KICK, BAN)");
                }
            }
        }
        if (changed) {
            saveConfig();
            getLogger().info("Config updated with new values.");
        }
    }

    @Override
    public void onDisable() {
        if (metrics != null) metrics.shutdown();
        if (checkManager != null) checkManager.close();
        if (freezeManager != null) freezeManager.clear();
        ClientBrandListener.clear();
        ModScanner.clear();
        if (ownsPacketEvents && PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().terminate();
        }
    }

    public static Guardianv3 getInstance() { return instance; }
    public FreezeManager getFreezeManager() { return freezeManager; }
}
