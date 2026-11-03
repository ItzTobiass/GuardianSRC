package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LongJumpCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();
    private final Map<UUID, Long> airborneSince = new ConcurrentHashMap<>();
    private final Map<UUID, Double> launchSpeed = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        if (!packet.hasPositionChanged()) return;
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (PacketContext.duplicatePosition(p)) return;
        if (MovementGuards.secondary(p) || MovementGuards.iceActive(p) || MovementGuards.ceilingActive(p)) {
            cleanup(id); return;
        }
        double horizontal = Math.hypot(PacketContext.deltaX(p), PacketContext.deltaZ(p));
        var e = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        var at = PacketContext.location(p);
        if (MovementEnvironment.supported(PacketContext.previous(p), at)
                || MovementGuards.supportedByPlacement(p, at)
                || MovementGuards.bridging(p)) {
            if (airborneSince.remove(id) != null) e.record(false, false, 3);
            launchSpeed.put(id, horizontal);
            return;
        }
        long start = airborneSince.computeIfAbsent(id, key -> PacketContext.now());
        double mult = MovementGuards.speedMultiplier(p);
        double limit = Math.max(.62 * mult, launchSpeed.getOrDefault(id, 0.0));
        long airTime = PacketContext.now() - start;
        boolean bad = (airTime > 450 && PacketContext.deltaY(p) > -.5 && horizontal > limit + .14)
                || horizontal > limit + .25 * mult;
        if (bad) {
            SetbackUtil.reject(p);
            if (e.record(true, horizontal > limit + .45 * mult, 3)) {
                int buf = e.buffer();
                e.reset();
                SetbackUtil.flag(p, "LongJump", buf);
            }
        }
    }
    public void cleanup(UUID uuid) { evidence.remove(uuid); airborneSince.remove(uuid); launchSpeed.remove(uuid); }
}
