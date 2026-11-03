package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.mrqendyxz.guardianv3.Guardianv3;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlyCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private static final class State {
        final FlightModel model = new FlightModel();
        long glideUntil;
        int stationaryAirPackets;
    }
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        Player player = event.getPlayer();
        if (PacketContext.duplicatePosition(player)) return;
        State state = states.computeIfAbsent(player.getUniqueId(), id -> new State());
        long now = PacketContext.now();
        Location at = PacketContext.location(player);
        Location prev = PacketContext.previous(player);
        if (player.isGliding()) state.glideUntil = now + 500;
        if (MovementGuards.ordinary(player) || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                || player.getAllowFlight() || player.isFlying() || player.isInsideVehicle()
                || player.isGliding() || player.isRiptiding() || now < state.glideUntil
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || special(at, player.getHeight()) || !MovementEnvironment.loaded(at)) {
            state.model.reset();
            state.stationaryAirPackets = 0;
            SetbackUtil.reject(player);
            return;
        }
        double dy = PacketContext.deltaY(player);
        double jump = MovementGuards.jump(player);
        double impulse = VelocityProtection.verticalAllowance(player);
        boolean velocityActive = VelocityProtection.active(player);
        boolean supported = MovementEnvironment.supported(prev, at)
                || MovementGuards.supportedByPlacement(player, at)
                || MovementGuards.supportedByEntity(player, at)
                || (MovementGuards.bridging(player) && dy <= jump + .08)
                || (packet.isOnGround() && MovementEnvironment.nearGround(prev, at));
        boolean takeoffFromGround = !supported && dy > 0 && dy <= jump + .06
                && (PacketContext.wasSupported(player) || MovementEnvironment.supported(prev) || MovementGuards.supportedByEntity(player, prev));
        if (!packet.hasPositionChanged()) {
            if (supported || velocityActive) {
                state.stationaryAirPackets = 0;
                state.model.record(0, true, 0, jump, impulse, now, 3);
                return;
            }
            if (++state.stationaryAirPackets <= 2) return;
        } else {
            state.stationaryAirPackets = 0;
        }
        if (takeoffFromGround) {
            state.model.record(0, true, 0, jump, impulse, now, 3);
        }
        if (velocityActive && dy <= jump + impulse + .15) {
            state.model.record(Math.min(0, dy), true, 0, jump, impulse + jump, now, 3);
            SetbackUtil.reject(player);
            return;
        }
        if (!supported) SetbackUtil.reject(player);
        double bounceFactor = (supported && !player.isSneaking()) ? MovementGuards.footprintBounceFactor(at) : 0;
        int threshold = Math.max(3, Guardianv3.getInstance().getConfig().getInt("checks.fly.setback-buffer", 4));
        boolean flag = state.model.record(dy, supported, bounceFactor, jump, impulse, now, threshold);
        if (!state.model.clean()) SetbackUtil.reject(player);
        if (flag) {
            int buf = state.model.buffer();
            state.model.reset();
            SetbackUtil.flag(player, "Fly", buf);
        }
    }
    private boolean special(Location at, double height) {
        return MovementGuards.touchesSpecialBlock(at, height);
    }
    public void cleanup(UUID uuid) { states.remove(uuid); }
}
