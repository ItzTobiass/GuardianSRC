package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StrafeCheck {

    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();
    private final Map<UUID, Vector2D> lastMove = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Ignorujeme, pokud hráč není ve vzduchu nebo má speciální režimy
        if (player.isOnGround() || player.getAllowFlight() || player.isGliding()) {
            buffer.put(uuid, 0);
            return;
        }

        double deltaX = wrapper.getLocation().getX() - player.getLocation().getX();
        double deltaZ = wrapper.getLocation().getZ() - player.getLocation().getZ();

        Vector2D currentMove = new Vector2D(deltaX, deltaZ);
        Vector2D last = lastMove.getOrDefault(uuid, currentMove);
        lastMove.put(uuid, currentMove);

        double angle = currentMove.getAngle(last);

        double speed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (speed > 0.15 && angle > 45.0) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);

            if (b > 4) {
                AlertUtil.sendAlert(player, "Strafe", b);
            }
        } else {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
        }
    }

    private static class Vector2D {
        final double x, z;

        Vector2D(double x, double z) {
            this.x = x;
            this.z = z;
        }

        double getAngle(Vector2D other) {
            double dot = x * other.x + z * other.z;
            double det = x * other.z - z * other.x;
            return Math.abs(Math.toDegrees(Math.atan2(det, dot)));
        }
    }
}