package org.mrqendyxz.guardianv3.Utils;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerExplosion;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VelocityProtection {
    private record Impulse(double horizontal, double vertical, long time) {}
    private static final Map<UUID, Impulse> impulses = new HashMap<>();

    public static void handleSend(PacketSendEvent event) {
        long now = PacketContext.now();
        if (event.getPacketType() == PacketType.Play.Server.EXPLOSION) {
            UUID id = event.getUser().getUUID();
            if (id == null && event.getPlayer() instanceof Player p) id = p.getUniqueId();
            if (id == null) return;
            double horizontal = 1.6;
            double vertical = 1.8;
            try {
                var knockback = new WrapperPlayServerExplosion(event).getKnockback();
                if (knockback != null) {
                    horizontal = Math.max(horizontal, Math.hypot(knockback.x, knockback.z) + .6);
                    vertical = Math.max(vertical, Math.max(0, knockback.y) + .6);
                }
            } catch (RuntimeException ignored) {
            }
            record(id, horizontal, vertical, now);
            return;
        }
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_VELOCITY) return;
        var packet = new WrapperPlayServerEntityVelocity(event);
        Player player = event.getPlayer() instanceof Player p ? p : null;
        int entityId = packet.getEntityId();
        boolean matchesPlayer = (player != null && entityId == player.getEntityId())
                || entityId == event.getUser().getEntityId();
        if (!matchesPlayer) return;
        UUID id = player != null ? player.getUniqueId() : event.getUser().getUUID();
        if (id == null) return;
        double horizontal = Math.hypot(packet.getVelocity().x, packet.getVelocity().z);
        double vertical = packet.getVelocity().y;
        if (horizontal > .005 || Math.abs(vertical) > .005) {
            record(id, Math.max(.15, horizontal), Math.max(.42, vertical), now);
        }
    }

    public static void recordBukkitVelocity(Player player, Vector velocity) {
        if (player == null || velocity == null) return;
        long now = System.nanoTime() / 1_000_000L;
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        double vertical = Math.max(.42, velocity.getY());
        record(player.getUniqueId(), Math.max(.25, horizontal), vertical, now);
    }

    public static void recordDamage(Player player) {
        if (player == null) return;
        long now = System.nanoTime() / 1_000_000L;
        record(player.getUniqueId(), .45, .45, now);
    }

    public static void recordWindCharge(Player player) {
        if (player == null) return;
        long now = System.nanoTime() / 1_000_000L;
        record(player.getUniqueId(), 1.8, 1.9, now);
    }

    private static void record(UUID id, double horizontal, double vertical, long now) {
        Impulse old = impulses.get(id);
        if (old != null && now - old.time() < 1200) {
            horizontal = Math.max(horizontal, old.horizontal());
            vertical = Math.max(vertical, old.vertical());
        }
        impulses.put(id, new Impulse(horizontal, vertical, now));
    }

    public static boolean active(Player player) {
        Impulse impulse = impulses.get(player.getUniqueId());
        if (impulse == null) return false;
        long now = PacketContext.now();
        long age = Math.max(0, now - impulse.time());
        long maxWindow = 1400L + Math.min(500L, Math.max(0, player.getPing()));
        if (age > maxWindow) {
            impulses.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public static double allowance(Player player) {
        Impulse impulse = impulses.get(player.getUniqueId());
        if (impulse == null) return 0;
        long age = Math.max(0, PacketContext.now() - impulse.time());
        long transit = Math.min(500, Math.max(0, player.getPing()) + 100L);
        if (age >= 1600 + transit) {
            impulses.remove(player.getUniqueId());
            return 0;
        }
        if (age <= transit + 450) return Math.max(.35, impulse.horizontal());
        double ticks = Math.max(0, age - (transit + 450)) / 50.0;
        return Math.max(.35, impulse.horizontal()) * Math.pow(.91, ticks);
    }

    public static double verticalAllowance(Player player) {
        Impulse impulse = impulses.get(player.getUniqueId());
        if (impulse == null) return 0;
        long age = Math.max(0, PacketContext.now() - impulse.time());
        long transit = Math.min(500, Math.max(0, player.getPing()) + 100L);
        if (age >= 1600 + transit) return 0;
        double base = Math.max(.42, impulse.vertical());
        if (age <= transit + 550) return base;
        double ticks = Math.max(0, age - (transit + 550)) / 50.0;
        return base * Math.pow(.93, ticks);
    }

    public static void cleanup(UUID id) { impulses.remove(id); }
    public static void clear() { impulses.clear(); }
}
