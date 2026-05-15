package org.mrqendyxz.guardianv3.Utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Guardianv3;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AlertUtil {

    private static final Map<UUID, Integer> totalViolations = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastAlert = new ConcurrentHashMap<>();

    public static void sendAlert(Player player, String checkName, int buffer) {
        if (player.isOp() || player.hasPermission("guardian.bypass")) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - lastAlert.getOrDefault(uuid, 0L) < 100) return;
        lastAlert.put(uuid, now);

        Guardianv3 plugin = Guardianv3.getInstance();
        FileConfiguration config = plugin.getConfig();

        int violations = totalViolations.getOrDefault(uuid, 0) + 1;
        totalViolations.put(uuid, violations);

        String safeCheck = checkName.replaceAll("[^a-zA-Z0-9 _-]", "");
        String brandDisplay = ClientBrandListener.getFormattedBrand(uuid);
        String cleanBrand = ChatColor.stripColor(brandDisplay).replaceAll("[^a-zA-Z0-9 _-]", "");

        String alertFormat = config.getString("alert-message", "§c§lGuardian §8» §f%player% §7failed §f%check% §8(§7x%buffer%§8) §8[§7Client: %brand%§8]");
        String alertMessage = alertFormat
                .replace("%player%", player.getName())
                .replace("%check%", safeCheck)
                .replace("%buffer%", String.valueOf(buffer))
                .replace("%brand%", brandDisplay)
                .replace("%vl%", String.valueOf(violations))
                .replace("&", "§");

        Bukkit.getConsoleSender().sendMessage(alertMessage);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp() || online.hasPermission("guardian.alerts")) {
                online.sendMessage(alertMessage);
            }
        }

        if (config.getBoolean("webhook.enabled", false)) {
            String url = config.getString("webhook.url", "none");
            if (!url.equalsIgnoreCase("none") && !url.isEmpty()) {
                sendDiscordWebhook(url, player.getName(), safeCheck, buffer, cleanBrand, violations);
            }
        }

        String punishmentType = config.getString("punishments.type", "NONE").toUpperCase();
        if (punishmentType.equals("NONE")) return;

        int threshold = config.getInt("punishments.threshold", 20);
        if (violations >= threshold) {
            totalViolations.put(uuid, 0);
            executePunishment(plugin, config, player, safeCheck, punishmentType);
        }
    }

    private static void executePunishment(Guardianv3 plugin, FileConfiguration config, Player player, String checkName, String type) {
        TaskUtil.run(() -> {
            String path = type.equals("BAN") ? "punishments.ban-message" : "punishments.kick-message";
            String rawMsg = config.getString(path, "§cFlagged for %check%");
            String finalMsg = ChatColor.translateAlternateColorCodes('&', rawMsg)
                    .replace("%check%", checkName)
                    .replace("\\n", "\n");

            if (type.equals("BAN")) {
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(player.getName(), finalMsg, null, "Guardian");
            }
            player.kickPlayer(finalMsg);
        });
    }

    private static void sendDiscordWebhook(String webhookUrl, String playerName, String check, int buffer, String brand, int totalVl) {
        Bukkit.getScheduler().runTaskAsynchronously(Guardianv3.getInstance(), () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = "{\"embeds\":[{\"title\":\"⚠️ Guardian Alert\",\"color\":16733525,\"fields\":["
                        + "{\"name\":\"Player\",\"value\":\"`" + playerName + "`\",\"inline\":true},"
                        + "{\"name\":\"Check\",\"value\":\"`" + check + "`\",\"inline\":true},"
                        + "{\"name\":\"Buffer\",\"value\":\"`" + buffer + "`\",\"inline\":true},"
                        + "{\"name\":\"Client\",\"value\":\"`" + brand + "`\",\"inline\":true},"
                        + "{\"name\":\"Total VL\",\"value\":\"`" + totalVl + "`\",\"inline\":true}"
                        + "],\"footer\":{\"text\":\"Guardian Anticheat\"},\"timestamp\":\"" + java.time.OffsetDateTime.now() + "\"}]}";

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }
}