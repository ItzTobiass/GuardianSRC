package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NoWebCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private static final class State {
        final MovementEvidence evidence = new MovementEvidence();
        int webTicks;
        double climbed;
    }
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        var packet = new WrapperPlayClientPlayerFlying(event);
        if (!packet.hasPositionChanged()) return;
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (PacketContext.duplicatePosition(p)) return;
        var at = PacketContext.location(p);
        if (MovementGuards.mode(p) || !MovementEnvironment.loaded(at)
                || VelocityProtection.allowance(p) > .02 || VelocityProtection.verticalAllowance(p) > .02) {
            cleanup(id); return;
        }
        var prev = PacketContext.previous(p);
        double height = p.getHeight();
        boolean inside = inWeb(at, height);
        boolean wasInside = inWeb(prev, height);
        State state = states.computeIfAbsent(id, key -> new State());
        if (!inside || !wasInside) {
            state.webTicks = 0;
            state.climbed = 0;
            state.evidence.record(false, false, 3);
            return;
        }
        state.webTicks++;
        double mult = MovementGuards.speedMultiplier(p);
        double speed = Math.hypot(PacketContext.deltaX(p), PacketContext.deltaZ(p));
        double dy = PacketContext.deltaY(p);
        boolean supported = MovementEnvironment.supported(at);
        if (dy > 0) state.climbed += dy; else if (supported) state.climbed = 0; else state.climbed = Math.max(0, state.climbed + dy);
        boolean fastHorizontal = speed > .1 * mult;
        boolean fastUp = dy > .035;
        boolean fastDown = dy < -.12;
        boolean webClimb = state.webTicks >= 3 && state.climbed > .2;
        boolean bad = fastHorizontal || fastUp || fastDown || webClimb;
        if (bad) SetbackUtil.reject(p);
        boolean severe = speed > .22 * mult || dy > .15 || state.climbed > .6;
        if (state.evidence.record(bad, severe, 3)) {
            int buf = state.evidence.buffer();
            state.evidence.reset();
            state.climbed = 0;
            SetbackUtil.flag(p, "NoWeb", buf);
        }
    }

    private static boolean inWeb(Location at, double height) {
        World world = at.getWorld();
        if (world == null) return false;
        int minX = (int) Math.floor(at.getX() - .299), maxX = (int) Math.floor(at.getX() + .299);
        int minZ = (int) Math.floor(at.getZ() - .299), maxZ = (int) Math.floor(at.getZ() + .299);
        int minY = (int) Math.floor(at.getY() + .001), maxY = (int) Math.floor(at.getY() + height - .001);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (WorldCache.type(world, x, y, z) == Material.COBWEB) return true;
                }
            }
        }
        return false;
    }

    public void cleanup(UUID uuid) { states.remove(uuid); }
}
