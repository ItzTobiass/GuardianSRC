package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WallClimbCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private static final double PLAYER_WIDTH = .3;
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
        Double last = previous.put(id, dy);
        boolean supported = MovementEnvironment.supported(PacketContext.location(p));
        boolean wasSupported = PacketContext.wasSupported(p);
        if (supported || wasSupported) return;
        boolean nearWall = isNearWall(p);
        if (!nearWall) return;
        boolean bad = last != null && dy > .02 && dy > (last - .08) * .98 + .07;
        if (bad) SetbackUtil.reject(p);
        var e = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        if (e.record(bad, false, 3)) {
            int buf = e.buffer();
            e.reset();
            SetbackUtil.flag(p, "WallClimb", buf);
        }
    }
    public void cleanup(UUID uuid) { evidence.remove(uuid); previous.remove(uuid); }
    private boolean isNearWall(Player p) {
        double bx = PacketContext.location(p).getX();
        double by = PacketContext.location(p).getY();
        double bz = PacketContext.location(p).getZ();
        double height = Math.max(1.0, p.getHeight());

        double[] offsets = { -(PLAYER_WIDTH + 0.05), 0, PLAYER_WIDTH + 0.05 };

        for (double ox : offsets) {
            for (double oz : offsets) {
                if (ox == 0 && oz == 0) continue;

                for (double oy = 0.1; oy <= height - 0.1; oy += 0.5) {
                    Material b = WorldCache.type(p.getWorld(),
                            (int) Math.floor(bx + ox),
                            (int) Math.floor(by + oy),
                            (int) Math.floor(bz + oz)
                    );
                    if (b.isSolid() && !Tag.CLIMBABLE.isTagged(b)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
