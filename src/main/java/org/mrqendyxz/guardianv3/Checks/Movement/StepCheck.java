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
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        double currentY = wrapper.getLocation().getY();
        double prevY = lastY.getOrDefault(uuid, currentY);
        double deltaY = currentY - prevY;
        lastY.put(uuid, currentY);

        if (player.getGameMode() == GameMode.CREATIVE || player.getAllowFlight() || isNearSpecialBlock(player)) {
            buffer.put(uuid, 0);
            return;
        }

        if (deltaY > 0.6 && player.getVelocity().getY() <= 0.0001) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);
            if (b > 2) {
                AlertUtil.sendAlert(player, "Step", b);
                buffer.put(uuid, 0);
            }
        } else {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
        }
    }

    private boolean isNearSpecialBlock(Player p) {
        for (double x = -0.5; x <= 0.5; x += 0.5) {
            for (double z = -0.5; z <= 0.5; z += 0.5) {
                Material m = p.getLocation().add(x, 0, z).getBlock().getType();
                String name = m.name();
                if (name.contains("BED") || name.contains("SLAB") || name.contains("STAIRS") || name.contains("FENCE")) return true;
            }
        }
        return false;
    }
}