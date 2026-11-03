package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NoSlowCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private static final class State {
        final MovementEvidence evidence = new MovementEvidence();
        double previousSpeed;
        int useTicks;
        int groundTicks;
        int lastUseId = -1;
    }
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        if (!packet.hasPositionChanged()) return;
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (PacketContext.duplicatePosition(p)) return;
        if (MovementGuards.secondary(p) || MovementGuards.iceActive(p)) { cleanup(id); return; }
        State state = states.computeIfAbsent(id, k -> new State());
        double speed = Math.hypot(PacketContext.deltaX(p), PacketContext.deltaZ(p));
        boolean using = ItemUseTracker.using(p);
        boolean grimBypass = ItemUseTracker.grimBypass(p);
        if (!using && !grimBypass) {
            state.previousSpeed = speed;
            state.useTicks = 0;
            state.groundTicks = 0;
            state.lastUseId = ItemUseTracker.useId(p);
            state.evidence.record(false, false, 5);
            return;
        }
        int currentUseId = ItemUseTracker.useId(p);
        if (!grimBypass && currentUseId != state.lastUseId) {
            state.lastUseId = currentUseId;
            state.useTicks = 0;
            state.previousSpeed = Math.max(state.previousSpeed, speed);
        }
        state.useTicks++;
        var at = PacketContext.location(p);
        double dy = PacketContext.deltaY(p);
        boolean supported = MovementEnvironment.supported(at);
        boolean wasSupported = PacketContext.wasSupported(p);
        if (supported && wasSupported && Math.abs(dy) < .001) {
            state.groundTicks++;
        } else {
            state.groundTicks = 0;
        }
        double mult = MovementGuards.speedMultiplier(p);
        double kb = VelocityProtection.allowance(p);
        boolean jumpTakeoff = !grimBypass && dy > .25 && (wasSupported || state.useTicks <= 6);
        double frictionLimit;
        if (jumpTakeoff) {
            frictionLimit = state.useTicks <= 6
                    ? .62 * mult
                    : Math.min(.42 * mult, state.previousSpeed * .91 + .235 * mult);
        } else if (state.groundTicks >= 2) {
            frictionLimit = state.previousSpeed * .65 + .035 * mult;
        } else {
            frictionLimit = state.previousSpeed * .91 + .028 * mult;
        }
        double steadyLimit = (state.groundTicks >= 2 ? .155 : .19) * mult;
        double limit = (grimBypass && state.useTicks >= 4 ? steadyLimit : Math.max(steadyLimit, frictionLimit)) + kb;
        boolean bad = state.useTicks >= 4 && speed > limit;
        if (bad) {
            SetbackUtil.reject(p);
            state.previousSpeed = state.groundTicks >= 2 ? Math.min(speed, limit) : state.previousSpeed * .91;
        } else {
            state.previousSpeed = speed;
        }
        if (state.evidence.record(bad, state.groundTicks >= 4 && state.useTicks >= 8 && speed > limit + .12, 5)) {
            int buf = state.evidence.buffer();
            state.evidence.reset();
            SetbackUtil.flag(p, "NoSlow", buf);
        }
    }
    public void cleanup(UUID uuid) { states.remove(uuid); }
}
