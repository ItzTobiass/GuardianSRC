package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BoatFlyCheck {

    private final Map<UUID, Double> lastX = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastY = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastZ = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferHover = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferSpeed = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> airTicks = new ConcurrentHashMap<>();

    private static final double MAX_HORIZONTAL_SPEED = 0.9;
    private static final double MAX_DELTA_Y = 0.12;

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();
        if (player == null) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof Boat)) {
            bufferHover.put(player.getUniqueId(), 0);
            bufferSpeed.put(player.getUniqueId(), 0);
            airTicks.put(player.getUniqueId(), 0);
            return;
        }

        if (!wrapper.hasPositionChanged()) return;

        UUID uuid = player.getUniqueId();
        double currentX = wrapper.getLocation().getX();
        double currentY = wrapper.getLocation().getY();
        double currentZ = wrapper.getLocation().getZ();

        double prevX = lastX.getOrDefault(uuid, currentX);
        double prevY = lastY.getOrDefault(uuid, currentY);
        double prevZ = lastZ.getOrDefault(uuid, currentZ);

        double deltaX = currentX - prevX;
        double deltaY = currentY - prevY;
        double deltaZ = currentZ - prevZ;
        double horizontalSpeed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        lastX.put(uuid, currentX);
        lastY.put(uuid, currentY);
        lastZ.put(uuid, currentZ);

        boolean onGround = wrapper.isOnGround();
        boolean aboveGround = isAboveGround(player);
        boolean inLiquid = player.getLocation().getBlock().isLiquid()
                || player.getLocation().clone().subtract(0, 0.1, 0).getBlock().isLiquid();

        if (onGround || inLiquid) {
            airTicks.put(uuid, 0);
            bufferHover.put(uuid, Math.max(0, bufferHover.getOrDefault(uuid, 0) - 2));
            bufferSpeed.put(uuid, Math.max(0, bufferSpeed.getOrDefault(uuid, 0) - 2));
            return;
        }

        int ticks = airTicks.getOrDefault(uuid, 0) + 1;
        airTicks.put(uuid, ticks);

        if (ticks < 4) return;

        if (aboveGround && Math.abs(deltaY) < 0.005 && horizontalSpeed > 0.05) {
            int b = bufferHover.getOrDefault(uuid, 0) + 1;
            bufferHover.put(uuid, b);
            if (b > 8) {
                AlertUtil.sendAlert(player, "BoatFly (Hover)", b);
                bufferHover.put(uuid, 0);
            }
        } else {
            bufferHover.put(uuid, Math.max(0, bufferHover.getOrDefault(uuid, 0) - 1));
        }

        if (aboveGround && horizontalSpeed > MAX_HORIZONTAL_SPEED) {
            int b = bufferSpeed.getOrDefault(uuid, 0) + 1;
            bufferSpeed.put(uuid, b);
            if (b > 5) {
                AlertUtil.sendAlert(player, "BoatFly (Speed)", b);
                bufferSpeed.put(uuid, 0);
            }
        } else {
            bufferSpeed.put(uuid, Math.max(0, bufferSpeed.getOrDefault(uuid, 0) - 1));
        }

        if (deltaY > MAX_DELTA_Y) {
            int b = bufferHover.getOrDefault(uuid, 0) + 1;
            bufferHover.put(uuid, b);
            if (b > 5) {
                AlertUtil.sendAlert(player, "BoatFly (Upwards)", b);
                bufferHover.put(uuid, 0);
            }
        }
    }

    private boolean isAboveGround(Player player) {
        for (double dy = 0.1; dy <= 3.0; dy += 0.2) {
            Material m = player.getLocation().clone().subtract(0, dy, 0).getBlock().getType();
            if (m == Material.WATER || m == Material.LAVA) return false;
            if (m.isSolid()) return true;
        }
        return false;
    }

    public void cleanup(UUID uuid) {
        lastX.remove(uuid);
        lastY.remove(uuid);
        lastZ.remove(uuid);
        bufferHover.remove(uuid);
        bufferSpeed.remove(uuid);
        airTicks.remove(uuid);
    }
}