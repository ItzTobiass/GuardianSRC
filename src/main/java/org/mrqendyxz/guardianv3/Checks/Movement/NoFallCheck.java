package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NoFallCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();
    private final Map<UUID, Double> fallen = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (PacketContext.duplicatePosition(p)) return;
        if (MovementGuards.secondary(p)) { cleanup(id); return; }
        var at = PacketContext.location(p);
        boolean supported = MovementEnvironment.supported(PacketContext.previous(p), at)
                || MovementGuards.supportedByPlacement(p, at)
                || MovementGuards.bridging(p);
        boolean wasSupported = PacketContext.wasSupported(p);
        var e = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        if (supported || wasSupported) {
            Double previousFall = fallen.remove(id);
            if (previousFall != null && previousFall > 3.2) {
                e.record(false, false, 2);
            }
            return;
        }
        double dy = PacketContext.deltaY(p);
        double distance = fallen.getOrDefault(id, 0.0);
        if (packet.hasPositionChanged()) distance = dy < 0 ? distance - dy : 0;
        if (packet.isOnGround() && (distance > 2.0 || (dy < -.15 && distance > 1.0))) {
            fallen.put(id, 0.0);
            SetbackUtil.reject(p);
            if (e.record(true, distance > 5.0, 2)) {
                int buf = e.buffer();
                e.reset();
                SetbackUtil.flag(p, "NoFall", buf);
            }
        } else {
            fallen.put(id, distance);
        }
    }
    public void cleanup(UUID uuid) { evidence.remove(uuid); fallen.remove(uuid); }
}
