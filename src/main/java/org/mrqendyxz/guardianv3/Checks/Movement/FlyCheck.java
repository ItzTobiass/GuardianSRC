package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import org.mrqendyxz.guardianv3.Utils.TaskUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlyCheck {

    private final Map<UUID, Double> lastY = new ConcurrentHashMap<>();
    private final Map<UUID, Double> predictedVelocityY = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferA = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferB = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferStuck = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMove = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastSafeLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> airTicks = new ConcurrentHashMap<>();

    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                || player.getAllowFlight() || player.isFlying()
                || player.getVehicle() != null || player.isGliding()
                || player.getLocation().getBlock().isLiquid()
                || isNearGround(player) || isClimbable(player)) {

            if (player.isOnGround()) {
                lastSafeLocation.put(uuid, player.getLocation());
            }

            lastY.put(uuid, wrapper.getLocation().getY());
            predictedVelocityY.remove(uuid);
            bufferA.put(uuid, 0);
            bufferB.put(uuid, 0);
            bufferStuck.put(uuid, 0);
            airTicks.put(uuid, 0);
            lastMove.put(uuid, now);
            return;
        }

        if (player.getNoDamageTicks() > 0 && player.getNoDamageTicks() < 15) {
            lastY.put(uuid, wrapper.getLocation().getY());
            predictedVelocityY.remove(uuid);
            return;
        }

        if (!wrapper.hasPositionChanged()) {
            if (!wrapper.isOnGround()) {
                long last = lastMove.getOrDefault(uuid, now);
                if (now - last > 800L) {
                    int b = bufferStuck.getOrDefault(uuid, 0) + 1;
                    bufferStuck.put(uuid, b);
                    if (b > 5) {
                        AlertUtil.sendAlert(player, "Fly (AirStuck)", b);
                        teleportBack(player);
                        lastMove.put(uuid, now);
                    }
                }
            }
            return;
        }

        lastMove.put(uuid, now);
        bufferStuck.put(uuid, 0);

        double currentY = wrapper.getLocation().getY();
        double prevY = lastY.getOrDefault(uuid, currentY);
        double deltaY = currentY - prevY;
        lastY.put(uuid, currentY);

        boolean onGround = wrapper.isOnGround();

        if (onGround) {
            predictedVelocityY.remove(uuid);
            airTicks.put(uuid, 0);
            bufferA.put(uuid, Math.max(0, bufferA.getOrDefault(uuid, 0) - 2));
            bufferB.put(uuid, Math.max(0, bufferB.getOrDefault(uuid, 0) - 2));
            lastSafeLocation.put(uuid, player.getLocation());
            return;
        }

        int ticks = airTicks.getOrDefault(uuid, 0) + 1;
        airTicks.put(uuid, ticks);

        double predictedVY = predictedVelocityY.getOrDefault(uuid, deltaY);
        double expectedDeltaY = (predictedVY - GRAVITY) * DRAG;
        predictedVelocityY.put(uuid, expectedDeltaY);

        double diff = deltaY - expectedDeltaY;
        if (ticks > 3 && diff > 0.08) {
            int b = bufferA.getOrDefault(uuid, 0) + 1;
            bufferA.put(uuid, b);
            if (b > 8) {
                AlertUtil.sendAlert(player, "Fly (Type: A)", b);
                teleportBack(player);
                bufferA.put(uuid, 0);
            }
        } else {
            bufferA.put(uuid, Math.max(0, bufferA.getOrDefault(uuid, 0) - 1));
        }

        if (ticks > 5 && Math.abs(deltaY) < 0.005) {
            int b = bufferB.getOrDefault(uuid, 0) + 1;
            bufferB.put(uuid, b);
            if (b > 6) {
                AlertUtil.sendAlert(player, "Fly (Type: B)", b);
                teleportBack(player);
                bufferB.put(uuid, 0);
            }
        } else {
            bufferB.put(uuid, Math.max(0, bufferB.getOrDefault(uuid, 0) - 1));
        }
    }

    private void teleportBack(Player player) {
        Location loc = lastSafeLocation.get(player.getUniqueId());
        if (loc != null) {
            TaskUtil.run(() -> {
                player.teleport(loc);
                player.setVelocity(new Vector(0, -0.1, 0));
            });
        }
    }

    private boolean isClimbable(Player p) {
        Material m = p.getLocation().getBlock().getType();
        String name = m.name();
        return name.contains("VINE") || name.contains("LADDER") || name.contains("SCAFFOLDING")
                || name.contains("TWISTING_VINES") || name.contains("WEEPING_VINES");
    }

    private boolean isNearGround(Player p) {
        for (double x = -0.4; x <= 0.4; x += 0.2) {
            for (double z = -0.4; z <= 0.4; z += 0.2) {
                if (p.getLocation().add(x, -0.3, z).getBlock().getType().isSolid()) return true;
                if (p.getLocation().add(x, -0.8, z).getBlock().getType().isSolid()) return true;
            }
        }
        return false;
    }

    public void cleanup(UUID uuid) {
        lastY.remove(uuid);
        predictedVelocityY.remove(uuid);
        bufferA.remove(uuid);
        bufferB.remove(uuid);
        bufferStuck.remove(uuid);
        lastMove.remove(uuid);
        lastSafeLocation.remove(uuid);
        airTicks.remove(uuid);
    }
}