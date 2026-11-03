package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StepCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> supportedLast = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        if (!packet.hasPositionChanged()) return;
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (PacketContext.duplicatePosition(p)) return;
        if (MovementGuards.secondary(p)) { cleanup(id); return; }
        boolean supported = MovementEnvironment.supported(PacketContext.location(p));
        Boolean previous = supportedLast.put(id, supported);
        double dy = PacketContext.deltaY(p);
        if (!Boolean.TRUE.equals(previous) || !supported || dy <= .05) return;
        double stepAttr = MovementGuards.attribute(p, org.bukkit.attribute.Attribute.GENERIC_STEP_HEIGHT, .6);
        double allowed = Math.max(stepAttr, MovementGuards.jump(p)) + .05;
        boolean bad = dy > allowed;
        if (bad) SetbackUtil.reject(p);
        var e = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        if (e.record(bad, dy > allowed + .3, 2)) {
            int buf = e.buffer();
            e.reset();
            SetbackUtil.flag(p, "Step", buf);
        }
    }
    public void cleanup(UUID uuid) { evidence.remove(uuid); supportedLast.remove(uuid); }
}
