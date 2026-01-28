package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoClickerCheck {
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING && event.getPacketType() != PacketType.Play.Client.ANIMATION) return;

        Player player = (Player) event.getPlayer();
        if (player == null) return;

        long now = System.currentTimeMillis();
        long delay = now - lastClick.getOrDefault(player.getUniqueId(), now);
        lastClick.put(player.getUniqueId(), now);

        if (delay < 50) {
            int b = buffer.getOrDefault(player.getUniqueId(), 0) + 1;
            buffer.put(player.getUniqueId(), b);
            if (b > 15) {
                AlertUtil.sendAlert(player, "AutoClicker", b);
                buffer.put(player.getUniqueId(), 0);
            }
        } else {
            buffer.put(player.getUniqueId(), Math.max(0, buffer.getOrDefault(player.getUniqueId(), 0) - 1));
        }
    }
}