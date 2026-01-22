package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BoatFlyCheck {

    private final Map<UUID, Double> lastY = new HashMap<>();
    private final Map<UUID, Integer> buffer = new HashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof Boat)) {
            buffer.put(player.getUniqueId(), 0);
            return;
        }

        if (!wrapper.hasPositionChanged()) return;

        UUID uuid = player.getUniqueId();
        double currentY = wrapper.getLocation().getY();
        double prevY = lastY.getOrDefault(uuid, currentY);
        double deltaY = currentY - prevY;

        if (deltaY > 0.1) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);

            if (b > 3) {
                AlertUtil.sendAlert(player, "BoatFly (Upwards)", b);
                buffer.put(uuid, 0);
            }
        } else if (deltaY == 0) {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
        }

        lastY.put(uuid, currentY);
    }
}