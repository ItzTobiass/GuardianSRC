package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LongJumpCheck {

    private final Map<UUID, LocationData> lastLoc = new HashMap<>();
    private final Map<UUID, Integer> buffer = new HashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();
        int ping = com.github.retrooper.packetevents.PacketEvents.getAPI().getPlayerManager().getPing(player);

        if (ping > 400) return;

        if (player.getAllowFlight() || player.isGliding() || player.getVehicle() != null) return;

        double x = wrapper.getLocation().getX();
        double z = wrapper.getLocation().getZ();

        LocationData last = lastLoc.get(uuid);
        if (last != null) {
            double deltaXZ = Math.hypot(x - last.x, z - last.z);

            double limit = 0.75 + (ping * 0.001);

            if (player.hasPotionEffect(PotionEffectType.SPEED)) {
                limit += (player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1) * 0.2;
            }

            if (!wrapper.isOnGround() && deltaXZ > limit) {
                int b = buffer.getOrDefault(uuid, 0) + 1;
                buffer.put(uuid, b);

                if (b > 3) {
                    AlertUtil.sendAlert(player, "LongJump", b);
                    buffer.put(uuid, 0);
                }
            } else {
                buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            }
        }
        lastLoc.put(uuid, new LocationData(x, z));
    }

    private static class LocationData {
        double x, z;
        LocationData(double x, double z) { this.x = x; this.z = z; }
    }
}