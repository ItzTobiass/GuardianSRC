package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StepCheck {

    private final Map<UUID, Double> lastY = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferA = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.getGameMode() == GameMode.CREATIVE || player.getAllowFlight()
                || player.getVehicle() != null || player.isGliding()) {
            lastY.put(uuid, wrapper.getLocation().getY());
            return;
        }

        double currentY = wrapper.getLocation().getY();
        double prevY = lastY.getOrDefault(uuid, currentY);
        double deltaY = currentY - prevY;
        lastY.put(uuid, currentY);

        if (deltaY <= 0) {
            bufferA.put(uuid, 0);
            return;
        }

        if (deltaY > 0.6 && player.getVelocity().getY() <= 0) {
            if (!isClimbable(player)) {
                int b = bufferA.getOrDefault(uuid, 0) + 1;
                bufferA.put(uuid, b);

                if (b > 1) {
                    AlertUtil.sendAlert(player, "Step (A)", b);
                    bufferA.put(uuid, 0);
                }
            }
        } else {
            bufferA.put(uuid, Math.max(0, bufferA.getOrDefault(uuid, 0) - 1));
        }

        if (deltaY > 0 && wrapper.isOnGround() && deltaY > 0.5) {
            AlertUtil.sendAlert(player, "Step (B)", 1);
        }
    }

    private boolean isClimbable(Player p) {
        Material m = p.getLocation().getBlock().getType();
        return m == Material.LADDER || m == Material.VINE || m == Material.SCAFFOLDING;
    }
}