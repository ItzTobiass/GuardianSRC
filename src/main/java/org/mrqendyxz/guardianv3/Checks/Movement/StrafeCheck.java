package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StrafeCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();
    private record Motion(double x, double z, int airTicks) {}
    private final Map<UUID, Motion> previous = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        if (!packet.hasPositionChanged()) return;
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (PacketContext.duplicatePosition(p)) return;
        if (MovementGuards.secondary(p)) { cleanup(id); return; }
        double x = PacketContext.deltaX(p), z = PacketContext.deltaZ(p);
        Motion last = previous.get(id);
        var at = PacketContext.location(p);
        boolean supported = MovementEnvironment.supported(PacketContext.previous(p), at)
                || MovementGuards.supportedByPlacement(p, at)
                || MovementGuards.bridging(p);
        int air = supported ? 0 : (last == null ? 1 : last.airTicks() + 1);
        previous.put(id, new Motion(x, z, air));
        boolean bad = last != null && air > 4 && AirStrafeModel.excessive(x, z, last.x(), last.z(), .1 * MovementGuards.speedMultiplier(p));
        if (bad) SetbackUtil.reject(p);
        var e = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        if (e.record(bad, false, 4)) {
            int buf = e.buffer();
            e.reset();
            SetbackUtil.flag(p, "Strafe", buf);
        }
    }
    public void cleanup(UUID uuid) { evidence.remove(uuid); previous.remove(uuid); }
}
