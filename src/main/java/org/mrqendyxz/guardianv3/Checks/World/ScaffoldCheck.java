package org.mrqendyxz.guardianv3.Checks.World;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScaffoldCheck {
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPlace = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        Player player = (Player) event.getPlayer();
        if (player == null || player.getGameMode() != org.bukkit.GameMode.SURVIVAL) return;

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            UUID uuid = player.getUniqueId();
            lastPlace.put(uuid, System.currentTimeMillis());

            float pitch = player.getLocation().getPitch();
            double motionY = player.getVelocity().getY();

            if (pitch > 70 && !player.isOnGround()) {
                if (motionY > 0.45 || motionY < 0.0) {
                    int b = buffer.getOrDefault(uuid, 0) + 1;
                    buffer.put(uuid, b);
                    if (b > 12) {
                        AlertUtil.sendAlert(player, "Scaffold", b);
                        buffer.put(uuid, 0);
                    }
                    return;
                }
            }

            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);
            if (b > 7) {
                AlertUtil.sendAlert(player, "Scaffold", b);
                buffer.put(uuid, 0);
            }
        }
    }

    public void handleMove(PacketReceiveEvent event) {
        Player player = (Player) event.getPlayer();
        if (player != null) {
            buffer.put(player.getUniqueId(), Math.max(0, buffer.getOrDefault(player.getUniqueId(), 0) - 1));
        }
    }
}