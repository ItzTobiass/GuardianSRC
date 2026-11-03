package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlinkCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        if (!packet.hasPositionChanged()) return;
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (PacketContext.duplicatePosition(p)) return;
        if (MovementGuards.secondary(p)) { cleanup(id); return; }
        Long before = last.put(id, PacketContext.now());
        if (before == null) return;
        long gap = PacketContext.now() - before;
        if (gap <= 350) return;
        double distance = Math.hypot(PacketContext.deltaX(p), PacketContext.deltaZ(p));
        boolean bad = gap < 5000 && distance > (gap / 50.0 + 2) * .8 * MovementGuards.speedMultiplier(p);
        if (bad) SetbackUtil.reject(p);
        var e = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        if (e.record(bad, distance > 4.0 * MovementGuards.speedMultiplier(p), 2)) {
            int buf = e.buffer();
            e.reset();
            SetbackUtil.flag(p, "Blink", buf);
        }
    }
    public void cleanup(UUID uuid) { evidence.remove(uuid); last.remove(uuid); }
}
