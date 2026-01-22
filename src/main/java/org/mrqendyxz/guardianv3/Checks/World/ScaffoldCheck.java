package org.mrqendyxz.guardianv3.Checks.World;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScaffoldCheck {

    private final Map<UUID, Long> lastPlace = new HashMap<>();
    private final Map<UUID, Integer> buffer = new HashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            Player player = (Player) event.getPlayer();
            UUID uuid = player.getUniqueId();
            
            if (player.getInventory().getItemInMainHand().getType().isAir()) return;

            long now = System.currentTimeMillis();
            long delay = now - lastPlace.getOrDefault(uuid, now);

            if (player.isSprinting() && player.getLocation().getPitch() > 70) {
                if (delay < 150) {
                    int b = buffer.getOrDefault(uuid, 0) + 1;
                    buffer.put(uuid, b);
                    if (b > 3) {
                        AlertUtil.sendAlert(player, "Scaffold", b);
                    }
                }
            } else {
                buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            }
            lastPlace.put(uuid, now);
        }
    }
}