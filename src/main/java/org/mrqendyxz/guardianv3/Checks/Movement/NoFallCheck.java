package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NoFallCheck {

    private final Map<UUID, Double> lastY = new HashMap<>();
    private final Map<UUID, Integer> buffer = new HashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.getGameMode() == GameMode.CREATIVE || player.getAllowFlight() || player.getVehicle() != null) {
            return;
        }

        if (wrapper.hasPositionChanged()) {
            double currentY = wrapper.getLocation().getY();

            double previousY = lastY.getOrDefault(uuid, currentY);
            double deltaY = previousY - currentY;

            if (wrapper.isOnGround()) {
                if (deltaY > 0.5) {
                    Material m = player.getLocation().getBlock().getType();
                    Material below = player.getLocation().clone().subtract(0, 0.1, 0).getBlock().getType();

                    if (m == Material.AIR && below == Material.AIR && !isSafe(below)) {
                        int b = buffer.getOrDefault(uuid, 0) + 1;
                        buffer.put(uuid, b);

                        if (b > 1) {
                            AlertUtil.sendAlert(player, "NoFall", b);
                        }
                    }
                } else {
                    buffer.put(uuid, 0);
                }
            }
            lastY.put(uuid, currentY);
        }
    }

    private boolean isSafe(Material m) {
        String name = m.name();
        return m == Material.SLIME_BLOCK || m == Material.WATER || m == Material.LAVA ||
                m == Material.COBWEB || name.contains("BED") || name.contains("SCAFFOLD");
    }
}