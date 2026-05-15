package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NoSlowCheck {

    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastSpeed = new ConcurrentHashMap<>();

    private static final double BASE_WALK = 0.215;
    private static final double BASE_SPRINT = 0.29;
    private static final double ITEM_USE_MULTIPLIER = 0.4;
    private static final double JUMP_HORIZONTAL_MULTIPLIER = 1.2;

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        if (player == null) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (!player.isHandRaised()) return;

        UUID uuid = player.getUniqueId();

        double deltaX = wrapper.getLocation().getX() - player.getLocation().getX();
        double deltaZ = wrapper.getLocation().getZ() - player.getLocation().getZ();
        double speed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        double base = player.isSprinting() ? BASE_SPRINT : BASE_WALK;
        double limit = base * ITEM_USE_MULTIPLIER;

        if (!wrapper.isOnGround()) {
            limit *= JUMP_HORIZONTAL_MULTIPLIER;
        }

        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amp = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier();
            limit *= (1.0 + (amp + 1) * 0.2);
        }

        if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            int amp = player.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier();
            limit *= (1.0 - (amp + 1) * 0.15);
        }

        limit += 0.03;

        Double prev = lastSpeed.get(uuid);
        lastSpeed.put(uuid, speed);

        if (prev == null) return;

        if (speed > limit) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);

            if (b >= 6) {
                AlertUtil.sendAlert(player, "NoSlow", b);
                buffer.put(uuid, 0);
            }
        } else {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
        }
    }

    public void cleanup(UUID uuid) {
        buffer.remove(uuid);
        lastSpeed.remove(uuid);
    }
}