package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimerCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private final Map<UUID, SustainedRate> rates = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        Player p = event.getPlayer();
        if (PacketContext.duplicatePosition(p)) return;
        if (rates.computeIfAbsent(p.getUniqueId(), id -> new SustainedRate()).record(PacketContext.now(), 24, 3)) {
            SetbackUtil.flag(p, "Timer", 3);
        }
    }
    public void cleanup(UUID uuid) { rates.remove(uuid); }
}
