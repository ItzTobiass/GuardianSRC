package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StrafeCheck {
    private final Map<UUID, Float> lastYaw = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();

        if (player == null || player.getGameMode() == GameMode.CREATIVE) return;
        if (!wrapper.hasPositionChanged() || !wrapper.hasRotationChanged()) return;

        UUID uuid = player.getUniqueId();
        double deltaX = wrapper.getLocation().getX() - player.getLocation().getX();
        double deltaZ = wrapper.getLocation().getZ() - player.getLocation().getZ();
        double speed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = wrapper.getLocation().getYaw();
        float last = lastYaw.getOrDefault(uuid, yaw);
        lastYaw.put(uuid, yaw);

        if (!wrapper.isOnGround() && speed > 0.15) {
            float diff = Math.abs(yaw - last);

            if (diff > 360) diff %= 360;
            if (diff > 180) diff = 360 - diff;

            if (diff > 45.0f && diff < 175.0f) {
                int b = buffer.getOrDefault(uuid, 0) + 1;
                buffer.put(uuid, b);
                if (b > 3) {
                    AlertUtil.sendAlert(player, "Strafe", b);
                    buffer.put(uuid, 0);
                }
            } else {
                buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            }
        }
    }
}