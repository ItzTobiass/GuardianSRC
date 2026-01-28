package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NoSlowCheck {

    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || !player.isHandRaised()) return;

        double deltaX = wrapper.getLocation().getX() - player.getLocation().getX();
        double deltaZ = wrapper.getLocation().getZ() - player.getLocation().getZ();
        double speed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        double limit = 0.15;

        if (player.isSprinting()) limit = 0.25;
        if (!wrapper.isOnGround()) limit = 0.45;

        if (speed > limit) {
            UUID uuid = player.getUniqueId();
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);

            if (b > 10) {
                AlertUtil.sendAlert(player, "NoSlow", b);
                buffer.put(uuid, 0);
            }
        } else {
            buffer.put(player.getUniqueId(), Math.max(0, buffer.getOrDefault(player.getUniqueId(), 0) - 1));
        }
    }
}