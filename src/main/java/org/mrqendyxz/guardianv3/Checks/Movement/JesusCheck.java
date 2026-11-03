package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JesusCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private static final class State {
        final MovementEvidence evidence = new MovementEvidence();
        int waterTicks;
        int flatOnTopTicks;
        int subSurfaceFastTicks;
    }
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (PacketContext.duplicatePosition(p)) return;
        var at = PacketContext.location(p);
        var prev = PacketContext.previous(p);
        if (MovementGuards.mode(p) || p.isSwimming() || !MovementEnvironment.loaded(at)
                || p.hasPotionEffect(PotionEffectType.LEVITATION)
                || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || VelocityProtection.active(p)
                || MovementGuards.supportedByEntity(p, at)
                || MovementGuards.nearBubbleColumn(at)) {
            cleanup(id);
            return;
        }
        State state = states.computeIfAbsent(id, k -> new State());
        if (MovementEnvironment.nearGround(prev, at)
                || touchesSolidNearWater(at)
                || MovementGuards.supportedByPlacement(p, at)
                || MovementGuards.bridging(p)) {
            state.waterTicks = 0;
            state.flatOnTopTicks = 0;
            state.subSurfaceFastTicks = 0;
            state.evidence.record(false, false, 3);
            return;
        }
        boolean liquidBelowOrAtFeet = MovementGuards.touchesLiquidFootprint(at, -.35, .15);
        boolean headInLiquid = MovementGuards.touchesLiquidFootprint(at, .85, 1.6);
        if (!liquidBelowOrAtFeet || headInLiquid) {
            state.waterTicks = 0;
            state.flatOnTopTicks = 0;
            state.subSurfaceFastTicks = 0;
            state.evidence.record(false, false, 3);
            return;
        }
        state.waterTicks++;
        if (state.waterTicks <= 5) return;

        double dy = PacketContext.deltaY(p);
        double horizontal = Math.hypot(PacketContext.deltaX(p), PacketContext.deltaZ(p));
        double mult = MovementGuards.speedMultiplier(p);
        int ds = MovementGuards.depthStrider(p);
        double groundSpeedLimit = (.235 + ds * .05) * mult;

        boolean feetAboveLiquid = !MovementGuards.touchesLiquidFootprint(at, .08, .5);
        if (feetAboveLiquid && Math.abs(dy) < .003 && horizontal > .05) {
            state.flatOnTopTicks++;
        } else {
            state.flatOnTopTicks = 0;
        }

        if (Math.abs(dy) < .005 && horizontal > groundSpeedLimit) {
            state.subSurfaceFastTicks++;
        } else {
            state.subSurfaceFastTicks = 0;
        }

        boolean spoofedGround = packet.isOnGround() && feetAboveLiquid && Math.abs(dy) < .02;
        boolean bad = spoofedGround || state.flatOnTopTicks >= 5 || state.subSurfaceFastTicks >= 4;

        if (bad) {
            SetbackUtil.reject(p);
            if (state.evidence.record(true, false, 3)) {
                int buf = state.evidence.buffer();
                state.evidence.reset();
                state.flatOnTopTicks = 0;
                state.subSurfaceFastTicks = 0;
                SetbackUtil.flag(p, "Jesus", buf);
            }
        } else if (Math.abs(dy) >= .015) {
            state.evidence.record(false, false, 3);
        }
    }

    private boolean touchesSolidNearWater(Location at) {
        World world = at.getWorld();
        if (world == null) return false;
        int minX = (int) Math.floor(at.getX() - .45), maxX = (int) Math.floor(at.getX() + .45);
        int minZ = (int) Math.floor(at.getZ() - .45), maxZ = (int) Math.floor(at.getZ() + .45);
        int minY = (int) Math.floor(at.getY() - .55), maxY = (int) Math.floor(at.getY() + .35);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    var m = WorldCache.type(world, x, y, z);
                    if (!m.isAir() && m != org.bukkit.Material.WATER && m != org.bukkit.Material.LAVA) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void cleanup(UUID uuid) { states.remove(uuid); }
}
