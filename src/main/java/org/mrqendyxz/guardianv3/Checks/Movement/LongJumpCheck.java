package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import org.mrqendyxz.guardianv3.Utils.VelocityProtection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LongJumpCheck {
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastY = new ConcurrentHashMap<>();
    private final Map<UUID, Long> groundTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGlide = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();

        if (player == null || player.getGameMode() == GameMode.CREATIVE || player.getAllowFlight()) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (player.isGliding()) {
            lastGlide.put(uuid, now);
            return;
        }

        if (now - lastGlide.getOrDefault(uuid, 0L) < 2000) return;

        if (VelocityProtection.shouldBypass(player)) {
            buffer.put(uuid, 0);
            return;
        }

        if (player.getVehicle() != null || player.isInsideVehicle()) return;

        double deltaY = wrapper.getLocation().getY() - lastY.getOrDefault(uuid, wrapper.getLocation().getY());
        lastY.put(uuid, wrapper.getLocation().getY());

        double deltaX = wrapper.getLocation().getX() - player.getLocation().getX();
        double deltaZ = wrapper.getLocation().getZ() - player.getLocation().getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (wrapper.isOnGround()) {
            groundTicks.put(uuid, now);
        }

        long timeSinceGround = now - groundTicks.getOrDefault(uuid, now);

        if (!wrapper.isOnGround() && timeSinceGround > 500 && deltaY > -0.5) {
            double limit = 0.62;
            if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED)) {
                limit += (player.getPotionEffect(org.bukkit.potion.PotionEffectType.SPEED).getAmplifier() + 1) * 0.12;
            }

            if (distance > limit) {
                int b = buffer.getOrDefault(uuid, 0) + 1;
                buffer.put(uuid, b);
                if (b > 3) {
                    AlertUtil.sendAlert(player, "LongJump", b);
                    buffer.put(uuid, 0);
                }
            } else {
                buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            }
        }
    }
}