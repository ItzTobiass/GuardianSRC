package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JesusCheck {

    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastY = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInWater = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        if (player == null) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight() || player.isFlying()) return;
        if (player.getVehicle() != null) return;
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        double currentY = wrapper.getLocation().getY();
        double prevY = lastY.getOrDefault(uuid, currentY);
        lastY.put(uuid, currentY);

        boolean onGround = wrapper.isOnGround();
        boolean aboveLiquid = isAboveLiquid(player);
        boolean inLiquid = player.isInWater() || checkWater(player);
        boolean nearSolid = isNearSolid(player);

        if (inLiquid) {
            lastInWater.put(uuid, now);
        }

        if (nearSolid || inLiquid || (now - lastInWater.getOrDefault(uuid, 0L) < 800)) {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            return;
        }

        if (aboveLiquid && onGround) {
            int b = buffer.getOrDefault(uuid, 0) + 2;
            buffer.put(uuid, b);

            if (b >= 5) {
                AlertUtil.sendAlert(player, "Jesus", b);
                buffer.put(uuid, 0);
            }
            return;
        }

        if (aboveLiquid && !onGround) {
            double deltaY = currentY - prevY;
            boolean validJump = deltaY > 0.05 && deltaY < 0.6;

            if (!validJump) {
                int b = buffer.getOrDefault(uuid, 0) + 1;
                buffer.put(uuid, b);

                if (b >= 5) {
                    AlertUtil.sendAlert(player, "Jesus", b);
                    buffer.put(uuid, 0);
                }
            } else {
                buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            }
            return;
        }

        buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
    }

    private boolean checkWater(Player player) {
        Material m = player.getLocation().getBlock().getType();
        Material mDown = player.getLocation().clone().subtract(0, 0.5, 0).getBlock().getType();
        return m == Material.WATER || m == Material.LAVA || mDown == Material.WATER || mDown == Material.LAVA;
    }

    private boolean isAboveLiquid(Player player) {
        for (double dy = 0.1; dy <= 1.2; dy += 0.1) {
            Block b = player.getLocation().clone().subtract(0, dy, 0).getBlock();
            if (b.getType() == Material.WATER || b.getType() == Material.LAVA) return true;
            if (b.getType().isSolid()) return false;
        }
        return false;
    }

    private boolean isNearSolid(Player player) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (double dy = 0; dy <= 0.7; dy += 0.35) {
                    Block b = player.getLocation().clone().add(x, -dy, z).getBlock();
                    if (b.getType().isSolid()) return true;
                }
            }
        }
        return false;
    }

    public void cleanup(UUID uuid) {
        buffer.remove(uuid);
        lastY.remove(uuid);
        lastInWater.remove(uuid);
    }
}