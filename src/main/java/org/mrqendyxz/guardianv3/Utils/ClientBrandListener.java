package org.mrqendyxz.guardianv3.Utils;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserConnectEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientBrandListener implements PacketListener, Listener {
    private static final Map<UUID, String> brandsByUUID = new ConcurrentHashMap<>();
    private static final Map<String, String> brandsByAddress = new ConcurrentHashMap<>();

    public static void handle(PacketReceiveEvent event) {}

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLUGIN_MESSAGE) return;

        WrapperPlayClientPluginMessage wrapper = new WrapperPlayClientPluginMessage(event);
        String channel = wrapper.getChannelName();

        if (!channel.equalsIgnoreCase("minecraft:brand") && !channel.equalsIgnoreCase("MC|Brand")) return;

        byte[] data = wrapper.getData();
        if (data == null || data.length == 0) return;

        String brand = parseBrand(data);
        if (brand == null || brand.isEmpty()) return;

        UUID uuid = event.getUser() != null ? event.getUser().getUUID() : null;
        String address = event.getUser() != null && event.getUser().getAddress() != null
                ? event.getUser().getAddress().getAddress().getHostAddress()
                : null;

        if (uuid != null) {
            brandsByUUID.put(uuid, brand);
        } else if (address != null) {
            brandsByAddress.put(address, brand);
        }
    }

    private String parseBrand(byte[] data) {
        try {
            int len = data[0] & 0xFF;
            if (len == data.length - 1 && len > 0) {
                return new String(data, 1, len, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception ignored) {}

        try {
            return new String(data, StandardCharsets.UTF_8).replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
        } catch (Exception ignored) {}

        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getAddress() == null) return;

        String address = player.getAddress().getAddress().getHostAddress();
        if (brandsByAddress.containsKey(address)) {
            brandsByUUID.put(player.getUniqueId(), brandsByAddress.remove(address));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        brandsByUUID.remove(event.getPlayer().getUniqueId());
        if (event.getPlayer().getAddress() != null) {
            brandsByAddress.remove(event.getPlayer().getAddress().getAddress().getHostAddress());
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {}
    @Override
    public void onUserConnect(UserConnectEvent event) {}
    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {}

    public static String getBrand(UUID uuid) {
        return brandsByUUID.get(uuid);
    }

    public static String getFormattedBrand(UUID uuid) {
        String brand = getBrand(uuid);
        if (brand == null || brand.isEmpty()) return "§7-";

        String lower = brand.toLowerCase();
        if (lower.contains("fabric")) return "§bFabric";
        if (lower.contains("lunar")) return "§bLunarClient";
        if (lower.contains("badlion")) return "§bBadlion";
        if (lower.contains("feather")) return "§bFeather";
        if (lower.contains("forge") || lower.contains("fml")) return "§cForge";
        if (lower.contains("vanilla")) return "§fVanilla";
        if (lower.contains("labymod")) return "§bLabyMod";
        if (lower.contains("meteor")) return "§dMeteor";
        if (lower.contains("impact")) return "§dImpact";
        if (lower.contains("aristois")) return "§dAristois";
        if (lower.contains("salhack")) return "§dSalHack";
        if (lower.contains("wurst")) return "§dWurst";
        if (lower.contains("rusherhack")) return "§dRusherHack";
        if (lower.contains("vape")) return "§cVape";
        if (lower.contains("liquidbounce")) return "§dLiquidBounce";
        if (lower.contains("optifine")) return "§aOptiFine";
        if (lower.contains("geyser")) return "§2Bedrock (Geyser)";
        if (lower.contains("pvplounge")) return "§bPvpLounge";
        if (lower.contains("cheatbreaker")) return "§bCheatBreaker";

        return "§7" + brand;
    }
}