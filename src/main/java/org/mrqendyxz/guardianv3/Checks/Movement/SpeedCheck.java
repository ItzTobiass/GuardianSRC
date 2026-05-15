package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import org.mrqendyxz.guardianv3.Utils.TaskUtil;
import org.mrqendyxz.guardianv3.Utils.VelocityProtection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpeedCheck {
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastJoin = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastSafeLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGlide = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();
        if (player == null || player.getAllowFlight()) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (player.isGliding()) {
            lastGlide.put(uuid, now);
            return;
        }

        if (now - lastGlide.getOrDefault(uuid, 0L) < 2000) return;
        if (player.getLocation().getBlock().isLiquid()) return;

        if (VelocityProtection.shouldBypass(player)) {
            buffer.put(uuid, 0);
            return;
        }

        if (now - lastJoin.getOrDefault(uuid, now) < 5000) {
            lastJoin.putIfAbsent(uuid, now);
            return;
        }

        if (!wrapper.hasPositionChanged()) return;

        double deltaX = wrapper.getLocation().getX() - player.getLocation().getX();
        double deltaZ = wrapper.getLocation().getZ() - player.getLocation().getZ();
        double speed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        double max = 0.34;
        if (player.isSprinting()) max = 0.45;

        if (isOnIce(player)) {
            max = 0.65;
        }

        if (player.getWalkSpeed() > 0.2f) max += (player.getWalkSpeed() - 0.2f) * 2.5;

        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED)) {
            int amplifier = player.getPotionEffect(org.bukkit.potion.PotionEffectType.SPEED).getAmplifier() + 1;
            max += amplifier * 0.12;
        }

        if (player.getLocation().getY() > wrapper.getLocation().getY()) {
            max += 0.15;
        }

        if (speed > max) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);
            if (b > 10) {
                AlertUtil.sendAlert(player, "Speed", b);
                teleportBack(player);
                buffer.put(uuid, 0);
            }
        } else {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            if (player.isOnGround()) {
                lastSafeLocation.put(uuid, player.getLocation());
            }
        }
    }

    private void teleportBack(Player player) {
        Location safeLoc = lastSafeLocation.get(player.getUniqueId());
        if (safeLoc != null) {
            TaskUtil.run(() -> {
                player.teleport(safeLoc);
                player.setVelocity(new Vector(0, player.getVelocity().getY(), 0));
            });
        }
    }

    private boolean isOnIce(Player p) {
        Material m = p.getLocation().clone().subtract(0, 0.1, 0).getBlock().getType();
        return m.name().contains("ICE");
    }
}