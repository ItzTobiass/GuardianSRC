package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WaterSpeedCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private static final class State {
        final MovementEvidence evidence = new MovementEvidence();
        double previousSpeed;
        int waterTicks;
    }
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        if (!packet.hasPositionChanged()) return;
        Player p = event.getPlayer();
        if (PacketContext.duplicatePosition(p)) return;
        UUID id = p.getUniqueId();
        Location at = PacketContext.location(p);
        if (MovementGuards.mode(p) || !MovementEnvironment.loaded(at)
                || MovementGuards.nearBubbleColumn(at) || MovementGuards.exceptionalCollision(at)
                || MovementGuards.iceActive(p) || VelocityProtection.active(p)) {
            cleanup(id);
            return;
        }
        State state = states.computeIfAbsent(id, k -> new State());
        double horizontal = Math.hypot(PacketContext.deltaX(p), PacketContext.deltaZ(p));
        boolean inLiquid = MovementGuards.touchesLiquidFootprint(at, -.05, .8);
        if (!inLiquid) {
            state.previousSpeed = horizontal;
            state.waterTicks = 0;
            state.evidence.record(false, false, 4);
            return;
        }
        state.waterTicks++;
        boolean supported = MovementEnvironment.nearGround(PacketContext.previous(p), at)
                || PacketContext.wasSupported(p)
                || MovementGuards.supportedByPlacement(p, at)
                || MovementGuards.bridging(p);
        double mult = MovementGuards.speedMultiplier(p);
        int ds = MovementGuards.depthStrider(p);
        double base = (.265 + ds * .055) * mult;
        if (state.waterTicks <= 4 || supported) base = Math.max(base, .38 * mult);
        double max = Math.max(base, state.previousSpeed * .91 + .025) + VelocityProtection.allowance(p);
        double dy = PacketContext.deltaY(p);
        double maxUpward = MovementGuards.jump(p) + .08 + VelocityProtection.verticalAllowance(p);
        boolean verticalBad = state.waterTicks >= 5 && !supported && dy > maxUpward;
        boolean bad = horizontal > max || verticalBad;
        if (bad || !state.evidence.clean()) SetbackUtil.reject(p);
        if (!bad) state.previousSpeed = horizontal;
        else state.previousSpeed = Math.min(horizontal, max);
        if (state.evidence.record(bad, horizontal > max + .30, 4)) {
            int buf = state.evidence.buffer();
            state.evidence.reset();
            SetbackUtil.flag(p, "WaterSpeed", buf);
        }
    }

    public void cleanup(UUID uuid) { states.remove(uuid); }
}
