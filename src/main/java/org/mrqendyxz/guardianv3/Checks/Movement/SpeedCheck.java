package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Guardianv3;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpeedCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private static final class State {
        final MovementEvidence evidence = new MovementEvidence();
        long glideUntil;
        double previousSpeed;
    }
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        if (!packet.hasPositionChanged()) return;
        Player player = event.getPlayer();
        if (PacketContext.duplicatePosition(player)) return;
        State state = states.computeIfAbsent(player.getUniqueId(), id -> new State());
        long now = PacketContext.now();
        Location at = PacketContext.location(player);
        if (player.isGliding()) state.glideUntil = now + 500;
        if (MovementGuards.ordinary(player) || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                || player.getAllowFlight() || player.isInsideVehicle() || player.isRiptiding()
                || player.isGliding() || now < state.glideUntil
                || WorldCache.liquid(at)
                || !MovementEnvironment.loaded(at)) {
            state.evidence.reset();
            state.previousSpeed = 0;
            SetbackUtil.reject(player);
            return;
        }
        double speed = Math.hypot(PacketContext.deltaX(player), PacketContext.deltaZ(player));
        double mult = MovementGuards.speedMultiplier(player);
        double max = .42 * mult;
        if (MovementGuards.iceActive(player)) max = Math.max(max, 1.6);
        if (MovementGuards.ceilingActive(player)) max += .22 * mult;
        if (!MovementEnvironment.supported(PacketContext.previous(player), at) || Math.abs(PacketContext.deltaY(player)) > 1e-4) {
            max += .18 * mult;
        }
        max = Math.max(max, state.previousSpeed * .91 + .03);
        max += VelocityProtection.allowance(player);
        boolean suspicious = speed > max;
        int threshold = Guardianv3.getInstance().getConfig().getInt("checks.speed.setback-buffer", 3);
        boolean flag = state.evidence.record(suspicious, speed > max + .4, threshold);
        if (suspicious || !state.evidence.clean()) SetbackUtil.reject(player);
        else state.previousSpeed = speed;
        if (flag) {
            int buf = state.evidence.buffer();
            state.evidence.reset();
            SetbackUtil.flag(player, "Speed", buf);
        }
    }
    public void cleanup(UUID uuid) { states.remove(uuid); }
}
