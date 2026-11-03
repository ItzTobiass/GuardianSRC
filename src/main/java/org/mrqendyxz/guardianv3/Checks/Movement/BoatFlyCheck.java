package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BoatFlyCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private static final class State {
        UUID vehicle;
        double x, y, z, expected, prevHorizontal;
        boolean sampled;
        long grace;
        long iceGrace;
        final MovementEvidence flyEvidence = new MovementEvidence();
        final MovementEvidence speedEvidence = new MovementEvidence();
        final MovementEvidence clipEvidence = new MovementEvidence();
    }
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.VEHICLE_MOVE) return;
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        Entity vehicle = p.getVehicle();
        if (vehicle == null || vehicle.getPassengers().isEmpty() || vehicle.getPassengers().getFirst() != p) {
            cleanup(id);
            return;
        }
        SetbackUtil.begin(p);
        var pos = new WrapperPlayClientVehicleMove(event).getPosition();
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)
                || Math.abs(pos.x) > 30_000_000 || Math.abs(pos.y) > 30_000_000 || Math.abs(pos.z) > 30_000_000) {
            cleanup(id);
            SetbackUtil.flag(p, "BoatFly", 1);
            return;
        }
        Location at = new Location(p.getWorld(), pos.x, pos.y, pos.z);
        if (!MovementEnvironment.loaded(at)) {
            cleanup(id);
            return;
        }
        State state = states.computeIfAbsent(id, key -> new State());
        if (!vehicle.getUniqueId().equals(state.vehicle)) {
            state = new State();
            state.vehicle = vehicle.getUniqueId();
            states.put(id, state);
        }
        Location prev = state.sampled ? new Location(p.getWorld(), state.x, state.y, state.z) : at;
        double dx = state.sampled ? pos.x - state.x : 0;
        double dy = state.sampled ? pos.y - state.y : 0;
        double dz = state.sampled ? pos.z - state.z : 0;
        double horizontal = Math.hypot(dx, dz);
        boolean first = !state.sampled;
        state.sampled = true;
        state.x = pos.x;
        state.y = pos.y;
        state.z = pos.z;

        long now = PacketContext.now();
        boolean contact = false;
        boolean hasIce = false;
        double[] offsets = {-.6, 0.0, .6};
        for (double ox : offsets) {
            for (double oz : offsets) {
                Location edge = at.clone().add(ox, 0, oz);
                if (!MovementEnvironment.loaded(edge)) {
                    cleanup(id);
                    return;
                }
                Location below = edge.clone().subtract(0, .35, 0);
                Material edgeType = WorldCache.type(edge);
                Material belowType = WorldCache.type(below);
                if (edgeType.name().contains("ICE") || belowType.name().contains("ICE")) {
                    hasIce = true;
                    state.iceGrace = now + 1200;
                }
                if (WorldCache.liquid(edge) || WorldCache.liquid(below)
                        || edgeType == Material.BUBBLE_COLUMN || belowType == Material.BUBBLE_COLUMN
                        || belowType == Material.SLIME_BLOCK || belowType == Material.MOVING_PISTON) {
                    contact = true;
                    if (edgeType == Material.BUBBLE_COLUMN || belowType == Material.BUBBLE_COLUMN
                            || belowType == Material.SLIME_BLOCK || belowType == Material.MOVING_PISTON) {
                        state.grace = now + 1500;
                    }
                }
                if (MovementEnvironment.supported(edge)) contact = true;
            }
        }

        if (!first) {
            boolean blocked = MovementEnvironment.solidBlocked(prev, at, .24, .45, 1.45);
            if (blocked) {
                SetbackUtil.reject(p);
                if (state.clipEvidence.record(true, horizontal > .35 || !MovementEnvironment.solidBlocked(null, prev, .24, .45, 1.45), 2)) {
                    int buf = state.clipEvidence.buffer();
                    state.clipEvidence.reset();
                    state.sampled = false;
                    SetbackUtil.flag(p, "NoClip", buf);
                    return;
                }
            } else {
                state.clipEvidence.record(false, false, 2);
            }

            boolean iceActive = hasIce || now < state.iceGrace;
            double kb = VelocityProtection.allowance(p);
            double maxSpeed = (iceActive ? Math.min(2.1, Math.max(.85, state.prevHorizontal + .28))
                    : Math.max(.60, state.prevHorizontal * .92 + .04)) + kb;
            boolean badSpeed = horizontal > maxSpeed;
            if (badSpeed) SetbackUtil.reject(p);
            else state.prevHorizontal = horizontal;
            if (state.speedEvidence.record(badSpeed, horizontal > maxSpeed + .35, 2)) {
                int buf = state.speedEvidence.buffer();
                state.speedEvidence.reset();
                state.sampled = false;
                SetbackUtil.flag(p, "Speed", buf);
                return;
            }
        }

        if (first || now < state.grace) {
            state.expected = dy;
            state.flyEvidence.reset();
            return;
        }
        if (contact) {
            boolean badUpward = dy > .26 + VelocityProtection.verticalAllowance(p);
            if (badUpward) {
                SetbackUtil.reject(p);
                if (state.flyEvidence.record(true, dy > .55, 2)) {
                    int buf = state.flyEvidence.buffer();
                    state.flyEvidence.reset();
                    state.sampled = false;
                    SetbackUtil.flag(p, "BoatFly", buf);
                }
            } else {
                state.expected = dy;
                state.flyEvidence.record(false, false, 3);
                if (state.speedEvidence.clean() && state.clipEvidence.clean()) {
                    SetbackUtil.acceptVehicle(p, at);
                }
            }
            return;
        }
        state.expected = (state.expected - .04) * .95;
        boolean badFly = dy - state.expected > .08;
        if (badFly) SetbackUtil.reject(p);
        if (state.flyEvidence.record(badFly, dy > .35, 3)) {
            int buf = state.flyEvidence.buffer();
            state.flyEvidence.reset();
            state.sampled = false;
            SetbackUtil.flag(p, "BoatFly", buf);
            return;
        }
        state.expected = Math.min(dy, state.expected);
    }

    public void cleanup(UUID uuid) { states.remove(uuid); }
}
