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
        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            WrapperPlayClientPluginMessage wrapper = new WrapperPlayClientPluginMessage(event);
            String channel = wrapper.getChannelName();

            if (channel.toLowerCase().contains("brand")) {
                byte[] data = wrapper.getData();
                if (data == null || data.length < 2) return;

                try {
                    String raw = new String(data, StandardCharsets.UTF_8);
                    String cleanBrand = raw.replaceAll("[\\p{Cntrl}]", "").trim();

                    if (cleanBrand.length() > 0) {
                        playerBrands.put(event.getUser().getUUID(), cleanBrand);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public static String getFormattedBrand(UUID uuid) {
        String rawBrand = playerBrands.getOrDefault(uuid, "Vanilla").toLowerCase();

        if (rawBrand.contains("fabric")) return "§bFabric";
        if (rawBrand.contains("lunar")) return "§bLunarClient";
        if (rawBrand.contains("badlion")) return "§bBadlion";
        if (rawBrand.contains("feather")) return "§bFeatherClient";
        if (rawBrand.contains("labymod")) return "§bLabyMod";
        if (rawBrand.contains("forge") || rawBrand.contains("fml")) return "§cForge";
        if (rawBrand.contains("vanilla")) return "§fVanilla";

        return "§7" + rawBrand;
    }
}