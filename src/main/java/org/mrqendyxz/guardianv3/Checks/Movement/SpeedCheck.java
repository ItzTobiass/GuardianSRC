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

public class SpeedCheck {

    private final Map<UUID, Integer> bufferA = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferB = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.getGameMode() == GameMode.CREATIVE || player.getAllowFlight() || player.isGliding()) return;

        double deltaX = wrapper.getLocation().getX() - player.getLocation().getX();
        double deltaZ = wrapper.getLocation().getZ() - player.getLocation().getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        double maxSpeed = player.isSprinting() ? 0.38 : 0.28;
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int level = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            maxSpeed += (level * 0.06);
        }

        if (!wrapper.isOnGround()) maxSpeed += 0.05;

        if (distance > maxSpeed + 0.1) {
            int b = bufferA.getOrDefault(uuid, 0) + 1;
            bufferA.put(uuid, b);
            if (b > 5) {
                AlertUtil.sendAlert(player, "Speed (Type: A)", b);
                bufferA.put(uuid, 0);
            }
        } else {
            bufferA.put(uuid, Math.max(0, bufferA.getOrDefault(uuid, 0) - 1));
        }

        if (distance > 0.6 && wrapper.isOnGround()) {
            int b = bufferB.getOrDefault(uuid, 0) + 1;
            bufferB.put(uuid, b);
            if (b > 2) {
                AlertUtil.sendAlert(player, "Speed (Type: B)", b);
                bufferB.put(uuid, 0);
            }
        }
    }
}