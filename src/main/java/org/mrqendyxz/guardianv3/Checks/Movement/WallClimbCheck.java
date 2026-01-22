package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WallClimbCheck {

    private final Map<UUID, Double> lastY = new HashMap<>();
    private final Map<UUID, Integer> buffer = new HashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);

        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.isOp() || player.getAllowFlight() || player.getVehicle() != null) return;

        double currentY = wrapper.getLocation().getY();
        double prevY = lastY.getOrDefault(uuid, currentY);
        double deltaY = currentY - prevY;

        if (deltaY > 0 && !wrapper.isOnGround()) {
            Material m = player.getLocation().getBlock().getType();

            if (isClimbable(m)) {
                buffer.put(uuid, 0);
                lastY.put(uuid, currentY);
                return;
            }

            if (!player.getLocation().clone().subtract(0, 0.1, 0).getBlock().getType().isSolid()) {
                if (isTouchingWall(player)) {
                    int b = buffer.getOrDefault(uuid, 0) + 1;
                    buffer.put(uuid, b);

                    if (b > 8) {
                        AlertUtil.sendAlert(player, "WallClimb (Spider)", b);
                        buffer.put(uuid, 0);
                    }
                }
            } else {
                buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            }
        } else {
            buffer.put(uuid, 0);
        }

        lastY.put(uuid, currentY);
    }

    private boolean isClimbable(Material m) {
        String name = m.name();
        return name.contains("LADDER") || name.contains("VINE") || name.contains("SCAFFOLD") ||
                name.contains("WATER") || name.contains("LAVA") || m == Material.COBWEB;
    }

    private boolean isTouchingWall(Player player) {
        double x = player.getLocation().getX();
        double y = player.getLocation().getY();
        double z = player.getLocation().getZ();

        // Kontrola okolí hráče v malém poloměru
        for (double ox = -0.4; ox <= 0.4; ox += 0.4) {
            for (double oz = -0.4; oz <= 0.4; oz += 0.4) {
                if (ox == 0 && oz == 0) continue;
                Block b = player.getWorld().getBlockAt(
                        (int) Math.floor(x + ox),
                        (int) Math.floor(y),
                        (int) Math.floor(z + oz)
                );
                if (b.getType().isSolid()) return true;
            }
        }
        return false;
    }
}