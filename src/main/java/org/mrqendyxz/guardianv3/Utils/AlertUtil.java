package org.mrqendyxz.guardianv3.Utils;

import org.bukkit.Bukkit;
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

    public static void sendAlert(Player player, String checkName, int buffer) {
        if (player.isOp() || player.hasPermission("guardian.bypass")) return;

        Guardianv3 plugin = Guardianv3.getInstance();
        FileConfiguration config = plugin.getConfig();

        UUID uuid = player.getUniqueId();
        int violations = totalViolations.getOrDefault(uuid, 0) + 1;
        totalViolations.put(uuid, violations);

        String brandDisplay = ClientBrandListener.getFormattedBrand(uuid);
        String cleanBrand = brandDisplay.replaceAll("§[0-9a-fk-or]", "");

        // In-game alert
        String message = "§c§lGuardian §8» §f" + player.getName() + " §7failed §f" + checkName +
                " §8(§7x" + buffer + "§8) §8[§7Client: " + brandDisplay + "§8]";

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp() || online.hasPermission("guardian.alerts")) {
                online.sendMessage(message);
            }
        }

        // Webhook logic - Strictly checks if enabled and URL is changed
        boolean webhookEnabled = config.getBoolean("webhook.enabled", false);
        String webhookUrl = config.getString("webhook.url", "none");

        if (webhookEnabled && !webhookUrl.equalsIgnoreCase("none") && !webhookUrl.isEmpty()) {
            sendDiscordWebhook(webhookUrl, player.getName(), checkName, buffer, cleanBrand, violations);
        }

        // Punishments
        int threshold = config.getInt("punishments.threshold", 20);
        if (violations >= threshold) {
            totalViolations.remove(uuid);
            executePunishment(plugin, config, player, checkName);
        }
    }

    private static void sendDiscordWebhook(String webhookUrl, String playerName, String check, int buffer, String brand, int totalVl) {
        Bukkit.getScheduler().runTaskAsynchronously(Guardianv3.getInstance(), () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = "{"
                        + "\"embeds\": [{"
                        + "\"title\": \"⚠️ Guardian Alert\","
                        + "\"color\": 16733525,"
                        + "\"fields\": ["
                        + "{\"name\": \"Player\", \"value\": \"`" + playerName + "`\", \"inline\": true},"
                        + "{\"name\": \"Check\", \"value\": \"`" + check + "`\", \"inline\": true},"
                        + "{\"name\": \"Buffer\", \"value\": \"`" + buffer + "`\", \"inline\": true},"
                        + "{\"name\": \"Client\", \"value\": \"`" + brand + "`\", \"inline\": true},"
                        + "{\"name\": \"Total VL\", \"value\": \"`" + totalVl + "`\", \"inline\": true}"
                        + "],"
                        + "\"footer\": {\"text\": \"Guardian Anticheat • Alerts\"},"
                        + "\"timestamp\": \"" + java.time.OffsetDateTime.now() + "\""
                        + "}]"
                        + "}";

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    private static void executePunishment(Guardianv3 plugin, FileConfiguration config, Player player, String checkName) {
        String punishmentType = config.getString("punishments.type", "KICK").toUpperCase();
        Bukkit.getScheduler().runTask(plugin, () -> {
            String msgPath = punishmentType.equals("BAN") ? "punishments.ban-message" : "punishments.kick-message";
            String rawMsg = config.getString(msgPath, "Flagged for %check%");
            String finalMsg = rawMsg.replace("%check%", checkName).replace("&", "§");

            if (punishmentType.equals("BAN")) {
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(player.getName(), finalMsg, null, "Guardian");
            }
            player.kickPlayer(finalMsg);
        });
    }
}