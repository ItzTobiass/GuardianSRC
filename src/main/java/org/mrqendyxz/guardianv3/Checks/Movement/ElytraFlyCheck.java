package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ElytraFlyCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private static final class State {
        final MovementEvidence stationary = new MovementEvidence();
        final MovementEvidence boost = new MovementEvidence();
        double prevHorizontal = -1;
    }
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (PacketContext.duplicatePosition(p)) return;
        var at = PacketContext.location(p);
        if (!p.isGliding() || p.isInsideVehicle() || p.isRiptiding() || ItemUseTracker.boosted(p)
                || p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || !MovementEnvironment.loaded(at)
                || MovementEnvironment.supported(at)
                || !MovementEnvironment.clear(at, p.getHeight())
                || MovementGuards.touchesSpecialBlock(at, p.getHeight())
                || VelocityProtection.allowance(p) > .01
                || VelocityProtection.verticalAllowance(p) > .01) {
            cleanup(id); return;
        }
        State state = states.computeIfAbsent(id, key -> new State());
        double horizontal = Math.hypot(PacketContext.deltaX(p), PacketContext.deltaZ(p));
        double dy = PacketContext.deltaY(p);
        boolean hover = horizontal < .003 && Math.abs(dy) < .001;
        if (hover) SetbackUtil.reject(p);
        if (state.stationary.record(hover, false, 20)) {
            int buf = state.stationary.buffer();
            state.stationary.reset();
            SetbackUtil.flag(p, "ElytraFly (Stationary)", buf);
            return;
        }
        boolean unboostedGain = state.prevHorizontal >= 0 && dy >= -.005
                && horizontal > state.prevHorizontal + .03 && horizontal > .35;
        state.prevHorizontal = horizontal;
        if (unboostedGain) SetbackUtil.reject(p);
        if (state.boost.record(unboostedGain, false, 5)) {
            int buf = state.boost.buffer();
            state.boost.reset();
            SetbackUtil.flag(p, "ElytraFly (Boost)", buf);
        }
    }
    public void cleanup(UUID uuid) { states.remove(uuid); }
}
