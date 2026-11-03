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

    private static final java.util.concurrent.Semaphore webhookSlots = new java.util.concurrent.Semaphore(4);
    private static final ViolationLedger violationsByCheck = new ViolationLedger();
    private static final Map<UUID, Map<String, Long>> lastAlert = new ConcurrentHashMap<>();

    public static void sendAlert(Player player, String checkName, int buffer) {
        TaskUtil.run(() -> sendAlertSync(player, checkName, buffer));
    }

    private static void sendAlertSync(Player player, String checkName, int buffer) {
        if (!player.isOnline()) return;
        if (player.isOp() || player.hasPermission("guardian.bypass")) return;

        UUID uuid = player.getUniqueId();
        long now = System.nanoTime() / 1_000_000L;
        Map<String, Long> alerts = lastAlert.computeIfAbsent(uuid, id -> new ConcurrentHashMap<>());
        if (now - alerts.getOrDefault(checkName, Long.MIN_VALUE / 2) < 100) return;
        alerts.put(checkName, now);

        Guardianv3 plugin = Guardianv3.getInstance();
        FileConfiguration config = plugin.getConfig();

        long decayMillis = ConfigDurations.seconds(config.getLong("punishments.decay-seconds", 30)) * 1000L;
        int violations = violationsByCheck.increment(uuid, checkName, now, decayMillis);

        String safeCheck = checkName.replaceAll("[^a-zA-Z0-9 ()_-]", "");
        String safePlayer = player.getName().replaceAll("[^a-zA-Z0-9_.-]", "");
        String brandDisplay = ClientBrandListener.getFormattedBrand(uuid);
        String cleanBrand = ChatColor.stripColor(brandDisplay).replaceAll("[^a-zA-Z0-9 ()_-]", "");

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
                sendDiscordWebhook(url, safePlayer, safeCheck, buffer, cleanBrand, violations);
            }
        }

        String punishmentType = config.getString("punishments.type", "NONE").trim().toUpperCase(java.util.Locale.ROOT);
        if (punishmentType.equals("NONE")) return;

        int threshold = Math.max(1, config.getInt("punishments.threshold", 20));
        if (violations >= threshold) {
            violationsByCheck.cleanup(uuid);
            executePunishment(plugin, config, player, safeCheck, punishmentType);
        }
    }

    private static void executePunishment(Guardianv3 plugin, FileConfiguration config, Player player, String checkName, String type) {
        TaskUtil.run(() -> {
            String path;
            switch (type) {
                case "BAN": path = "punishments.ban-message"; break;
                case "KICK": path = "punishments.kick-message"; break;
                case "NONE": return;
                default:
                    plugin.getLogger().warning("Invalid punishments.type: " + type + "; no punishment executed.");
                    return;
            }
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

    private static void sendDiscordWebhook(String webhookUrl, String playerName, String check, int buffer, String brand, int checkVl) {
        if (!webhookSlots.tryAcquire()) return;
        try {
        Bukkit.getScheduler().runTaskAsynchronously(Guardianv3.getInstance(), () -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(webhookUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = "{\"embeds\":[{\"title\":\"⚠️ Guardian Alert\",\"color\":16733525,\"fields\":["
                        + "{\"name\":\"Player\",\"value\":\"`" + playerName + "`\",\"inline\":true},"
                        + "{\"name\":\"Check\",\"value\":\"`" + check + "`\",\"inline\":true},"
                        + "{\"name\":\"Buffer\",\"value\":\"`" + buffer + "`\",\"inline\":true},"
                        + "{\"name\":\"Client\",\"value\":\"`" + brand + "`\",\"inline\":true},"
                        + "{\"name\":\"Check VL\",\"value\":\"`" + checkVl + "`\",\"inline\":true}"
                        + "],\"footer\":{\"text\":\"Guardian Anticheat\"},\"timestamp\":\"" + java.time.OffsetDateTime.now() + "\"}]}";

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
                webhookSlots.release();
            }
        });
        } catch (RuntimeException ex) { webhookSlots.release(); throw ex; }
    }
    public static void cleanup(UUID id) { violationsByCheck.cleanup(id); lastAlert.remove(id); }
    public static void clear() { violationsByCheck.clear(); lastAlert.clear(); }
}
