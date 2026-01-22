package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClickTPCheck {

    private final Map<UUID, Long> lastTeleport = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastX = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastZ = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);

        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        double x = wrapper.getLocation().getX();
        double z = wrapper.getLocation().getZ();

        if (!lastX.containsKey(uuid)) {
            updatePlayer(uuid, x, z);
            return;
        }

        long timeSinceTeleport = System.currentTimeMillis() - lastTeleport.getOrDefault(uuid, 0L);
        if (timeSinceTeleport < 1500 || player.getNoDamageTicks() > 0) {
            updatePlayer(uuid, x, z);
            return;
        }

        double dist = Math.sqrt(Math.pow(x - lastX.get(uuid), 2) + Math.pow(z - lastZ.get(uuid), 2));

        if (dist > 10.0) {
            AlertUtil.sendAlert(player, "ClickTP", 1);
        }

        updatePlayer(uuid, x, z);
    }

    private void updatePlayer(UUID uuid, double x, double z) {
        lastX.put(uuid, x);
        lastZ.put(uuid, z);
    }

    public void handleTeleport(UUID uuid) {
        lastTeleport.put(uuid, System.currentTimeMillis());
    }
}