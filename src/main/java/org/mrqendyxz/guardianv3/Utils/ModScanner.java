package org.mrqendyxz.guardianv3.Utils;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.mrqendyxz.guardianv3.Guardianv3;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class ModScanner implements PacketListener, Listener {
    public enum Action { NONE, ALERT, KICK, BAN }
    public record Mod(String id, int channels, Action action) {}
    private static final Map<UUID, Set<String>> channels = new ConcurrentHashMap<>();
    private static final Set<String> IGNORED = Set.of("minecraft", "bungeecord", "velocity", "c", "common");

    @Override public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled()) return;
        String channel;
        byte[] data;
        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            var packet = new WrapperPlayClientPluginMessage(event);
            channel = packet.getChannelName();
            data = packet.getData();
        } else if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
            var packet = new WrapperConfigClientPluginMessage(event);
            channel = packet.getChannelName();
            data = packet.getData();
        } else {
            return;
        }
        if (channel == null || data == null) return;
        UUID id = event.getUser().getUUID();
        if (id == null) return;
        Set<String> set = channels.computeIfAbsent(id, k -> new ConcurrentSkipListSet<>());
        if (channel.equalsIgnoreCase("minecraft:register") || channel.equals("REGISTER")) {
            if (data.length > 32768) return;
            for (String entry : new String(data, StandardCharsets.UTF_8).split("\0")) {
                String clean = sanitize(entry);
                if (!clean.isEmpty() && set.size() < 512) set.add(clean);
            }
        } else if (!channel.equalsIgnoreCase("minecraft:brand") && !channel.equalsIgnoreCase("MC|Brand")
                && !channel.equalsIgnoreCase("minecraft:unregister") && !channel.equals("UNREGISTER")) {
            String clean = sanitize(channel);
            if (!clean.isEmpty() && set.size() < 512) set.add(clean);
        }
    }

    private static String sanitize(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.:/|-]", "");
        return s.length() > 96 ? s.substring(0, 96) : s;
    }

    private static String namespace(String channel) {
        int i = channel.indexOf(':');
        if (i < 0) i = channel.indexOf('|');
        return i > 0 ? channel.substring(0, i) : channel;
    }

    public static List<Mod> scan(UUID id) {
        FileConfiguration config = Guardianv3.getInstance().getConfig();
        Map<String, Integer> counts = new TreeMap<>();
        for (String ch : channels.getOrDefault(id, Set.of())) {
            String ns = namespace(ch);
            if (IGNORED.contains(ns)) continue;
            counts.merge(ns, 1, Integer::sum);
        }
        String brand = ClientBrandListener.getBrand(id);
        if (brand != null) {
            String b = sanitize(brand.replace(' ', '-'));
            if (!b.isEmpty() && !b.equals("vanilla")) counts.putIfAbsent("brand:" + b, 0);
        }
        List<Mod> result = new ArrayList<>();
        for (var e : counts.entrySet()) result.add(new Mod(e.getKey(), e.getValue(), actionFor(config, e.getKey())));
        return result;
    }

    private static Action actionFor(FileConfiguration config, String id) {
        ConfigurationSection section = config.getConfigurationSection("scanner.mods");
        if (section == null) return Action.NONE;
        Action best = Action.NONE;
        for (String key : section.getKeys(false)) {
            if (!id.contains(key.toLowerCase(Locale.ROOT))) continue;
            Action a;
            try { a = Action.valueOf(section.getString(key, "NONE").trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { continue; }
            if (a.ordinal() > best.ordinal()) best = a;
        }
        return best;
    }

    public static List<String> format(Player target, List<Mod> mods) {
        List<String> lines = new ArrayList<>();
        lines.add("§c§lGuardian §8» §7Scan of §f" + target.getName() + " §8(§7Client: " + ClientBrandListener.getFormattedBrand(target.getUniqueId()) + "§8)");
        if (mods.isEmpty()) {
            lines.add("§8 - §7No mods detected.");
        } else {
            for (Mod m : mods) {
                String color = switch (m.action()) { case BAN -> "§4"; case KICK -> "§c"; case ALERT -> "§6"; default -> "§a"; };
                String tag = m.action() == Action.NONE ? "" : " §8[" + color + m.action() + "§8]";
                String count = m.channels() > 0 ? " §8(" + m.channels() + " ch)" : "";
                lines.add("§8 - " + color + m.id() + count + tag);
            }
        }
        lines.add("§8Detected via client brand and registered plugin channels. Mods without networking can stay hidden.");
        return lines;
    }

    public static void enforce(Player target, List<Mod> mods) {
        Mod worst = null;
        for (Mod m : mods) if (m.action() != Action.NONE && (worst == null || m.action().ordinal() > worst.action().ordinal())) worst = m;
        if (worst == null) return;
        FileConfiguration config = Guardianv3.getInstance().getConfig();
        String alert = "§c§lGuardian §8» §f" + target.getName() + " §7uses blocked mod §c" + worst.id() + " §8(§7" + worst.action() + "§8)";
        Bukkit.getConsoleSender().sendMessage(alert);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp() || online.hasPermission("guardian.scan") || online.hasPermission("guardian.alerts")) online.sendMessage(alert);
        }
        if (worst.action() == Action.ALERT || target.hasPermission("guardian.bypass")) return;
        String path = worst.action() == Action.BAN ? "scanner.ban-message" : "scanner.kick-message";
        String msg = ChatColor.translateAlternateColorCodes('&', config.getString(path, "§cBlocked mod: %mod%"))
                .replace("%mod%", worst.id()).replace("\\n", "\n");
        if (worst.action() == Action.BAN) {
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target.getName(), msg, null, "Guardian Scanner");
        }
        target.kickPlayer(msg);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        FileConfiguration config = Guardianv3.getInstance().getConfig();
        if (!config.getBoolean("scanner.enabled", true) || !config.getBoolean("scanner.scan-on-join", true)) return;
        Player p = event.getPlayer();
        long delay = Math.max(1, config.getLong("scanner.join-delay-seconds", 3)) * 20L;
        Bukkit.getScheduler().runTaskLater(Guardianv3.getInstance(), () -> {
            if (p.isOnline()) enforce(p, scan(p.getUniqueId()));
        }, delay);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { channels.remove(event.getPlayer().getUniqueId()); }
    @Override public void onUserDisconnect(UserDisconnectEvent event) {
        UUID id = event.getUser().getUUID();
        if (id != null && Bukkit.getPlayer(id) == null) channels.remove(id);
    }
    public static void clear() { channels.clear(); }
}
