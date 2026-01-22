package org.mrqendyxz.guardianv3.Checks.World;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FastPlaceCheck {
    private final Map<UUID, Long> lastPlace = new HashMap<>();
    private final Map<UUID, Integer> buffer = new HashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            Player player = (Player) event.getPlayer();
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();

            long last = lastPlace.getOrDefault(uuid, 0L);
            long diff = now - last;

            if (diff < 25) {
                int b = buffer.getOrDefault(uuid, 0) + 1;
                buffer.put(uuid, b);

                if (b > 3) {
                    AlertUtil.sendAlert(player, "FastPlace", b);
                }
            } else {
                buffer.put(uuid, 0);
            }

            lastPlace.put(uuid, now);
        }
    }
}