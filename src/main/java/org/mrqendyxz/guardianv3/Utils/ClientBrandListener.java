package org.mrqendyxz.guardianv3.Utils;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientBrandListener {
    private static final Map<UUID, String> playerBrands = new ConcurrentHashMap<>();

    public static void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLUGIN_MESSAGE) return;

        WrapperPlayClientPluginMessage wrapper = new WrapperPlayClientPluginMessage(event);
        String channel = wrapper.getChannelName();

        if (channel.equals("minecraft:brand") || channel.equals("MC|Brand")) {
            byte[] data = wrapper.getData();
            if (data == null || data.length < 1) return;

            try {
                String raw = new String(data, StandardCharsets.UTF_8);
                // Odstraní vše kromě písmen, čísel, mezer, teček a pomlček (vyhodí délkový prefix)
                String cleanBrand = raw.replaceAll("[^a-zA-Z0-9_\\-. ]", "").trim();

                if (!cleanBrand.isEmpty()) {
                    playerBrands.put(event.getUser().getUUID(), cleanBrand);
                }
            } catch (Exception ignored) {}
        }
    }

    public static String getFormattedBrand(UUID uuid) {
        String rawBrand = playerBrands.getOrDefault(uuid, "Vanilla");
        String lowerBrand = rawBrand.toLowerCase();

        if (lowerBrand.contains("fabric")) return "§bFabric";
        if (lowerBrand.contains("lunar")) return "§bLunarClient";
        if (lowerBrand.contains("badlion")) return "§bBadlion";
        if (lowerBrand.contains("feather")) return "§bFeatherClient";
        if (lowerBrand.contains("labymod")) return "§bLabyMod";
        if (lowerBrand.contains("cheatbreaker")) return "§bCheatBreaker";
        if (lowerBrand.contains("pvplounge")) return "§bPvpLounge";
        if (lowerBrand.contains("forge") || lowerBrand.contains("fml")) return "§cForge";
        if (lowerBrand.contains("vanilla")) return "§fVanilla";

        return "§7" + rawBrand;
    }

    public static void cleanup(UUID uuid) {
        playerBrands.remove(uuid);
    }
}