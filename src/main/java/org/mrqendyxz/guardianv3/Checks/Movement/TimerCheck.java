package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimerCheck {

    private static class PlayerData {
        long lastTime = 0;
        double balance = 0;
        int violations = 0;
        long joinTime = System.currentTimeMillis();
    }

    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        Player player = (Player) event.getPlayer();
        if (player == null) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight() || player.isFlying()) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        PlayerData data = playerData.computeIfAbsent(uuid, k -> new PlayerData());

        if (now - data.joinTime < 5000) {
            data.lastTime = now;
            return;
        }

        if (player.getPing() > 500) {
            data.lastTime = now;
            return;
        }

        if (data.lastTime == 0) {
            data.lastTime = now;
            return;
        }

        long elapsed = now - data.lastTime;
        data.lastTime = now;

        if (elapsed > 2000) {
            return;
        }

        data.balance += elapsed;
        data.balance -= 50.0;

        if (data.balance > 1000.0) {
            data.balance = 1000.0;
        }

        if (data.balance < -150.0) {
            int b = data.violations + 1;
            data.violations = b;
            if (b > 4) {
                AlertUtil.sendAlert(player, "Timer", b);
                data.violations = 0;
            }
            data.balance = 0;
        } else {
            data.violations = Math.max(0, data.violations - 1);
        }
    }

    public void cleanup(UUID uuid) {
        playerData.remove(uuid);
    }
}