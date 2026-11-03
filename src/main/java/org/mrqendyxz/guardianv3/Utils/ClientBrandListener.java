package org.mrqendyxz.guardianv3.Utils;

import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientBrandListener implements PacketListener, Listener {
    private record Brand(User owner, String value) {}
    private record Known(String key, String display) {}
    private static final Map<UUID, Brand> brands = new ConcurrentHashMap<>();
    private static final Known[] KNOWN = {
            new Known("lunarclient", "§bLunar Client"),
            new Known("lunar", "§bLunar Client"),
            new Known("badlion", "§bBadlion"),
            new Known("feather", "§bFeather"),
            new Known("labymod", "§bLabyMod"),
            new Known("pvplounge", "§bPvPLounge"),
            new Known("cheatbreaker", "§bCheatBreaker"),
            new Known("geyser", "§2Bedrock (Geyser)"),
            new Known("floodgate", "§2Bedrock (Geyser)"),
            new Known("meteor", "§dMeteor"),
            new Known("impact", "§dImpact"),
            new Known("aristois", "§dAristois"),
            new Known("salhack", "§dSalHack"),
            new Known("wurst", "§dWurst"),
            new Known("rusherhack", "§dRusherHack"),
            new Known("liquidbounce", "§dLiquidBounce"),
            new Known("vape", "§cVape"),
            new Known("quilt", "§bQuilt"),
            new Known("neoforge", "§cNeoForge"),
            new Known("forge", "§cForge"),
            new Known("fml", "§cForge"),
            new Known("fabric", "§bFabric"),
            new Known("optifine", "§aOptiFine"),
            new Known("vanilla", "§fVanilla")
    };

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
        if (channel == null || (!channel.equalsIgnoreCase("minecraft:brand") && !channel.equalsIgnoreCase("MC|Brand"))) return;
        User user = event.getUser();
        UUID id = user.getUUID();
        if (id == null) return;
        String value = BrandParser.parse(data);
        if (value != null) brands.put(id, new Brand(user, value));
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { brands.remove(event.getPlayer().getUniqueId()); }

    @Override public void onUserDisconnect(UserDisconnectEvent event) {
        UUID id = event.getUser().getUUID();
        if (id != null) brands.computeIfPresent(id, (key, brand) -> brand.owner() == event.getUser() ? null : brand);
    }

    public static String getBrand(UUID id) { Brand brand = brands.get(id); return brand == null ? null : brand.value(); }
    public static void clear() { brands.clear(); }

    public static String getFormattedBrand(UUID uuid) {
        String brand = getBrand(uuid);
        if (brand == null || brand.isEmpty()) return "§7Unknown";
        String lower = brand.toLowerCase(Locale.ROOT);
        for (Known known : KNOWN) {
            if (lower.contains(known.key())) {
                return lower.equals(known.key()) ? known.display() : known.display() + " §8(§7" + brand + "§8)";
            }
        }
        return "§7" + brand;
    }
}
