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

public class AirJumpCheck {

    private final Map<UUID, Double> lastY = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastDeltaY = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> wasOnGround = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> airTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        if (player == null) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight() || player.isFlying() || player.isGliding()) return;
        if (player.getVehicle() != null) return;

        UUID uuid = player.getUniqueId();
        double currentY = wrapper.getLocation().getY();
        double prevY = lastY.getOrDefault(uuid, currentY);
        double deltaY = currentY - prevY;
        double prevDeltaY = lastDeltaY.getOrDefault(uuid, 0.0);
        boolean currentOnGround = wrapper.isOnGround();
        boolean previousOnGround = wasOnGround.getOrDefault(uuid, true);

        lastY.put(uuid, currentY);
        lastDeltaY.put(uuid, deltaY);
        wasOnGround.put(uuid, currentOnGround);

        if (isInLiquid(player) || isClimbable(player) || isNearGround(player)) {
            airTicks.put(uuid, 0);
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            return;
        }

        if (currentOnGround) {
            airTicks.put(uuid, 0);
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            return;
        }

        int ticks = airTicks.getOrDefault(uuid, 0) + 1;
        airTicks.put(uuid, ticks);

        if (ticks < 5) return;

        boolean wasFalling = prevDeltaY < -0.05;
        boolean nowGoingUp = deltaY > 0.2;

        if (!previousOnGround && wasFalling && nowGoingUp) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);
            if (b >= 2) {
                AlertUtil.sendAlert(player, "AirJump", b);
                buffer.put(uuid, 0);
            }
        } else {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
        }
    }

    private boolean isInLiquid(Player p) {
        Material feet = p.getLocation().getBlock().getType();
        Material eye = p.getEyeLocation().getBlock().getType();
        return feet == Material.WATER || feet == Material.LAVA
                || eye == Material.WATER || eye == Material.LAVA;
    }

    private boolean isClimbable(Player p) {
        String name = p.getLocation().getBlock().getType().name();
        return name.contains("VINE") || name.contains("LADDER") || name.contains("SCAFFOLDING")
                || name.contains("TWISTING_VINES") || name.contains("WEEPING_VINES");
    }

    private boolean isNearGround(Player p) {
        for (double x = -0.4; x <= 0.4; x += 0.2) {
            for (double z = -0.4; z <= 0.4; z += 0.2) {
                Material m1 = p.getLocation().clone().add(x, -0.3, z).getBlock().getType();
                Material m2 = p.getLocation().clone().add(x, -0.6, z).getBlock().getType();
                if (m1.isSolid() || m2.isSolid()) return true;
            }
        }
        return false;
    }

    public void cleanup(UUID uuid) {
        lastY.remove(uuid);
        lastDeltaY.remove(uuid);
        wasOnGround.remove(uuid);
        airTicks.remove(uuid);
        buffer.remove(uuid);
    }
}