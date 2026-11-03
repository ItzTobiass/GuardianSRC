package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HighJumpCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        if (!packet.hasPositionChanged()) return;
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (PacketContext.duplicatePosition(p)) return;
        if (MovementGuards.secondary(p)) { cleanup(id); return; }
        double dy = PacketContext.deltaY(p);
        if (dy <= 0 || MovementEnvironment.supported(PacketContext.location(p))) return;
        double limit = MovementGuards.jump(p) + .05;
        boolean bad = dy > limit;
        var e = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        if (bad) {
            SetbackUtil.reject(p);
            if (e.record(true, dy > limit + .25, 2)) {
                int buf = e.buffer();
                e.reset();
                SetbackUtil.flag(p, "HighJump", buf);
            }
        } else if (PacketContext.wasSupported(p) && dy > .1) {
            e.record(false, false, 2);
        }
    }
    public void cleanup(UUID uuid) { evidence.remove(uuid); }
}
