package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JesusCheck {
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        if (player.getAllowFlight() || player.isSwimming() || player.isInsideVehicle()) return;

        Material in = player.getLocation().getBlock().getType();
        Material below = player.getLocation().clone().subtract(0, 0.1, 0).getBlock().getType();

        if (in == Material.WATER) {
            buffer.put(player.getUniqueId(), 0);
            return;
        }

        if (below == Material.WATER && in == Material.AIR) {
            if (Math.abs(player.getVelocity().getY()) < 0.1) {
                int vl = buffer.getOrDefault(player.getUniqueId(), 0) + 1;
                buffer.put(player.getUniqueId(), vl);

                if (vl > 5) {
                    AlertUtil.sendAlert(player, "Jesus", vl);
                }
                return;
            }
        }

        buffer.put(player.getUniqueId(), Math.max(0, buffer.getOrDefault(player.getUniqueId(), 0) - 1));
    }
}