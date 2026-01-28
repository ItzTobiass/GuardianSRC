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

public class FlyCheck {

    private final Map<UUID, Double> lastY = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferA = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferStuck = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMove = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (player.getGameMode() == GameMode.CREATIVE || player.getAllowFlight() || player.getVehicle() != null || isNearGround(player)) {
            lastY.put(uuid, wrapper.getLocation().getY());
            bufferA.put(uuid, 0);
            bufferStuck.put(uuid, 0);
            lastMove.put(uuid, now);
            return;
        }

        if (wrapper.hasPositionChanged()) {
            lastMove.put(uuid, now);
            bufferStuck.put(uuid, 0);

            double currentY = wrapper.getLocation().getY();
            double prevY = lastY.getOrDefault(uuid, currentY);
            double deltaY = currentY - prevY;
            lastY.put(uuid, currentY);

            if (Math.abs(deltaY) < 0.001 && !wrapper.isOnGround()) {
                int b = bufferA.getOrDefault(uuid, 0) + 1;
                bufferA.put(uuid, b);
                if (b > 8) {
                    AlertUtil.sendAlert(player, "Fly (Type: A)", b);
                    bufferA.put(uuid, 0);
                }
            } else {
                bufferA.put(uuid, Math.max(0, bufferA.getOrDefault(uuid, 0) - 1));
            }
        } else if (!wrapper.isOnGround()) {
            long last = lastMove.getOrDefault(uuid, now);
            if (now - last > 500L) {
                int b = bufferStuck.getOrDefault(uuid, 0) + 1;
                bufferStuck.put(uuid, b);
                if (b > 5) {
                    AlertUtil.sendAlert(player, "Fly (Type: AirStuck)", b);
                    lastMove.put(uuid, now);
                }
            }
        }
    }

    private boolean isNearGround(Player p) {
        for (double x = -0.3; x <= 0.3; x += 0.3) {
            for (double z = -0.3; z <= 0.3; z += 0.3) {
                Material m = p.getLocation().add(x, -0.5, z).getBlock().getType();
                if (m.isSolid()) return true;
            }
        }
        return false;
    }
}