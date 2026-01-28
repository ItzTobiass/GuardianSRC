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
        long lastPacketTime;
        double balance;
        long lastJoin;
    }

    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        Player player = (Player) event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE || player.getAllowFlight()) return;

        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        PlayerData data = playerData.computeIfAbsent(uuid, k -> {
            PlayerData pd = new PlayerData();
            pd.lastPacketTime = currentTime;
            pd.lastJoin = currentTime;
            pd.balance = -100.0;
            return pd;
        });

        if (currentTime - data.lastJoin < 5000) return;

        long diff = currentTime - data.lastPacketTime;
        data.lastPacketTime = currentTime;

        if (diff > 2000) return;

        data.balance += 50.0;
        data.balance -= diff;

        if (data.balance > 50.0) {
            AlertUtil.sendAlert(player, "Timer", (int) data.balance);
            data.balance = -50.0;
        }
    }

    public void cleanup(UUID uuid) {
        playerData.remove(uuid);
    }
}