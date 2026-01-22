package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoClickerCheck {

    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> clickCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastReset = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.ANIMATION) return;

        Player player = (Player) event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        int clicks = clickCount.getOrDefault(uuid, 0) + 1;
        clickCount.put(uuid, clicks);

        long lastResetTime = lastReset.getOrDefault(uuid, now);
        if (now - lastResetTime >= 1000L) {
            if (clicks > 18) {
                int b = buffer.getOrDefault(uuid, 0) + 1;
                buffer.put(uuid, b);
                if (b > 3) {
                    AlertUtil.sendAlert(player, "AutoClicker", clicks);
                    buffer.put(uuid, 0);
                }
            } else {
                buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            }
            clickCount.put(uuid, 0);
            lastReset.put(uuid, now);
        }
    }
}