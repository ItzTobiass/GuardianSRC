package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TimerCheck {

    private static final int WINDOW_MS = 2000;
    private static final double EXPECTED_PPS = 20.0;
    private static final double FLAG_THRESHOLD = 1.09;
    private static final int VL_TO_FLAG = 4;
    private static final long GRACE_PERIOD_MS = 5000;
    private static final long MAX_PING_LENIENCY_MS = 800;

    private static class PlayerData {
        final Deque<Long> packetTimes = new ArrayDeque<>();
        long joinTime = System.currentTimeMillis();
        int violations = 0;
        int consecutiveClean = 0;
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

        if (now - data.joinTime < GRACE_PERIOD_MS) return;

        int ping = player.getPing();
        if (ping > 500) return;

        long pingLeniency = Math.min((long) (ping * 1.5), MAX_PING_LENIENCY_MS);
        long effectiveWindow = WINDOW_MS + pingLeniency;

        data.packetTimes.addLast(now);

        while (!data.packetTimes.isEmpty() && data.packetTimes.peekFirst() < now - effectiveWindow) {
            data.packetTimes.pollFirst();
        }

        int count = data.packetTimes.size();
        double effectiveWindowSec = effectiveWindow / 1000.0;
        double pps = count / effectiveWindowSec;

        if (pps > EXPECTED_PPS * FLAG_THRESHOLD) {
            data.consecutiveClean = 0;
            data.violations++;

            if (data.violations >= VL_TO_FLAG) {
                AlertUtil.sendAlert(player, "Timer", data.violations);
            }
        } else {
            data.consecutiveClean++;

            if (data.consecutiveClean >= 20) {
                data.violations = Math.max(0, data.violations - 1);
                data.consecutiveClean = 0;
            }
        }
    }

    public void cleanup(UUID uuid) {
        playerData.remove(uuid);
    }
}