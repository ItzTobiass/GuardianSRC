package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ElytraFlyCheck {

    private final Map<UUID, Double> lastY = new HashMap<>();
    private final Map<UUID, Integer> buffer = new HashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        if (!player.isGliding()) {
            buffer.put(player.getUniqueId(), 0);
            return;
        }

        UUID uuid = player.getUniqueId();
        double currentY = wrapper.getLocation().getY();
        double prevY = lastY.getOrDefault(uuid, currentY);
        double deltaY = currentY - prevY;

        // Legitimní raketa může vystřelit hráče velmi vysoko.
        // Cheaty obvykle stoupají konstantně (např. +0.5 každý paket).
        if (deltaY > 0.15 && player.getVelocity().getY() < 0.01) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);

            if (b > 15) { // Zvýšený buffer na 15 paketů pro eliminaci raketek
                AlertUtil.sendAlert(player, "ElytraFly (Upwards)", b);
                buffer.put(uuid, 0);
            }
        } else {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
        }

        lastY.put(uuid, currentY);
    }
}