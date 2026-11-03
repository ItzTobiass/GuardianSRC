package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NoClipCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> armed = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        if (!packet.hasPositionChanged()) return;
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (PacketContext.duplicatePosition(p)) return;
        var at = PacketContext.location(p);
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR
                || p.isInsideVehicle() || p.isGliding() || p.isSleeping()
                || !MovementEnvironment.loaded(at) || MovementGuards.exceptionalCollision(at)) {
            cleanup(id);
            return;
        }
        var prev = PacketContext.previous(p);
        boolean lowHitbox = p.isSwimming() || p.isRiptiding() || p.getHeight() <= 1.1
                || MovementGuards.touchesLiquidFootprint(at, -.3, .8)
                || MovementGuards.touchesLiquidFootprint(prev, -.3, .8);
        double minOffset = lowHitbox ? .08 : .25;
        double maxOffset = lowHitbox ? .38 : Math.max(.8, p.getHeight() - .15);
        boolean sweptBlocked = MovementEnvironment.solidBlocked(prev, at, .22, minOffset, maxOffset);
        boolean atBlocked = MovementEnvironment.solidBlocked(at, at, .22, minOffset, maxOffset);
        boolean clear = !sweptBlocked && !atBlocked;
        if (clear) {
            armed.put(id, true);
            var e = evidence.get(id);
            if (e != null) e.record(false, false, 2);
            return;
        }
        SetbackUtil.reject(p);
        double distance = Math.sqrt(Math.pow(PacketContext.deltaX(p), 2)
                + Math.pow(PacketContext.deltaY(p), 2)
                + Math.pow(PacketContext.deltaZ(p), 2));
        boolean prevClear = !MovementEnvironment.solidBlocked(prev, prev, .22, minOffset, maxOffset);
        if (!armed.getOrDefault(id, true) && !prevClear && distance <= .05) {
            return;
        }
        boolean bad = sweptBlocked || distance > .05;
        var e = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        if (e.record(bad, sweptBlocked && (prevClear || distance > .35), 2)) {
            int buf = e.buffer();
            e.reset();
            SetbackUtil.flag(p, "NoClip", buf);
        }
    }
    public void cleanup(UUID uuid) { evidence.remove(uuid); armed.remove(uuid); }
}
