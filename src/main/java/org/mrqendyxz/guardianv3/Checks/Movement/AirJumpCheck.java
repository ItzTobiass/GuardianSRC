package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AirJumpCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();
    private final Map<UUID, Double> previous = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        if (!packet.hasPositionChanged()) return;
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (PacketContext.duplicatePosition(p)) return;
        if (MovementGuards.secondary(p)) { cleanup(id); return; }
        double dy = PacketContext.deltaY(p);
        var at = PacketContext.location(p);
        boolean wasSupported = PacketContext.wasSupported(p);
        boolean supported = MovementEnvironment.supported(PacketContext.previous(p), at)
                || MovementGuards.supportedByPlacement(p, at)
                || MovementGuards.bridging(p);
        Double last = previous.put(id, dy);
        var e = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        if (supported) {
            if (!wasSupported && last != null && last < 0) e.record(false, false, 2);
            return;
        }
        if (wasSupported || last == null) return;
        boolean reverseJump = last <= -.02 && dy > .08;
        boolean midAirBoost = dy > last + .08 && dy > .18;
        if (reverseJump || midAirBoost) {
            SetbackUtil.reject(p);
            if (e.record(true, dy > MovementGuards.jump(p) + .1, 2)) {
                int buf = e.buffer();
                e.reset();
                SetbackUtil.flag(p, "AirJump", buf);
            }
        }
    }
    public void cleanup(UUID uuid) { evidence.remove(uuid); previous.remove(uuid); }
}
