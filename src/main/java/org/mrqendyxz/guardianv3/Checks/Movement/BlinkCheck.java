package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlinkCheck {

    private final Map<UUID, Long> lastPacketTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocation = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long lastTime = lastPacketTime.getOrDefault(uuid, now);
        long diff = now - lastTime;

        if (wrapper.hasPositionChanged()) {
            Location currentLoc = new Location(player.getWorld(),
                    wrapper.getLocation().getX(),
                    wrapper.getLocation().getY(),
                    wrapper.getLocation().getZ());

            Location prevLoc = lastLocation.get(uuid);

            if (prevLoc != null && prevLoc.getWorld().equals(currentLoc.getWorld())) {
                double distance = prevLoc.distance(currentLoc);

                if (diff > 500 && distance > 1.5) {
                    int b = buffer.getOrDefault(uuid, 0) + 1;
                    buffer.put(uuid, b);

                    if (b > 1) {
                        AlertUtil.sendAlert(player, "Blink", (int) diff);
                        buffer.put(uuid, 0);
                    }
                } else {
                    buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
                }
            }
            lastLocation.put(uuid, currentLoc);
        }

        lastPacketTime.put(uuid, now);
    }
}