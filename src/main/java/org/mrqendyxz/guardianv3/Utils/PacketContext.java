package org.mrqendyxz.guardianv3.Utils;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PacketContext {
    private static final class Pose {
        final UUID world;
        final PacketMotion motion;
        boolean wasSupported = true;
        boolean currentlySupported = true;
        long lastFlyingMillis = Long.MIN_VALUE;
        boolean duplicatePosition;
        Pose(UUID world, PacketMotion motion) {
            this.world = world;
            this.motion = motion;
        }
    }
    private static final Map<UUID, Pose> poses = new HashMap<>();
    private static long receivedMillis;
    private static long batch;
    public static void beginBatch() { batch++; }
    public static long batch() { return batch; }
    public static void time(long nanos) { receivedMillis = nanos / 1_000_000L; }
    public static long now() { return receivedMillis; }
    public static void update(Player player, WrapperPlayClientPlayerFlying packet) {
        UUID id = player.getUniqueId();
        Pose pose = poses.get(id);
        if (pose == null || !pose.world.equals(player.getWorld().getUID())) {
            Location at = player.getLocation();
            pose = new Pose(player.getWorld().getUID(), new PacketMotion(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch()));
            poses.put(id, pose);
        }
        var at = packet.getLocation();
        double prevX = pose.motion.x(), prevY = pose.motion.y(), prevZ = pose.motion.z();
        boolean dup = packet.hasPositionChanged() && packet.hasRotationChanged()
                && !pose.duplicatePosition
                && pose.lastFlyingMillis != Long.MIN_VALUE && receivedMillis - pose.lastFlyingMillis < 45
                && Math.abs(at.getX() - prevX) < 1e-7
                && Math.abs(at.getY() - prevY) < 1e-7
                && Math.abs(at.getZ() - prevZ) < 1e-7;
        pose.duplicatePosition = dup;
        pose.lastFlyingMillis = receivedMillis;
        if (!dup) {
            pose.wasSupported = pose.currentlySupported;
        }
        pose.motion.update(packet.hasPositionChanged(), at.getX(), at.getY(), at.getZ(),
                packet.hasRotationChanged(), at.getYaw(), at.getPitch());
        if (!dup) {
            Location prevLoc = new Location(player.getWorld(), prevX, prevY, prevZ);
            Location currentLoc = new Location(player.getWorld(), pose.motion.x(), pose.motion.y(), pose.motion.z(), pose.motion.yaw(), pose.motion.pitch());
            pose.currentlySupported = MovementEnvironment.supported(prevLoc, currentLoc)
                    || MovementGuards.supportedByPlacement(player, currentLoc)
                    || MovementGuards.bridging(player);
            if (packet.hasPositionChanged()) {
                CombatHistory.recordPosition(player, currentLoc);
            }
        }
    }
    public static Location location(Player player) {
        Pose pose = poses.get(player.getUniqueId());
        if (pose == null || !pose.world.equals(player.getWorld().getUID())) return player.getLocation();
        PacketMotion m = pose.motion;
        return new Location(player.getWorld(), m.x(), m.y(), m.z(), m.yaw(), m.pitch());
    }
    public static Location previous(Player player) {
        Location at = location(player);
        return at.subtract(deltaX(player), deltaY(player), deltaZ(player));
    }
    public static Location eye(Player player) { return location(player).add(0, player.getEyeHeight(), 0); }
    public static double deltaX(Player p) { Pose s = poses.get(p.getUniqueId()); return s == null ? 0 : s.motion.dx(); }
    public static double deltaY(Player p) { Pose s = poses.get(p.getUniqueId()); return s == null ? 0 : s.motion.dy(); }
    public static double deltaZ(Player p) { Pose s = poses.get(p.getUniqueId()); return s == null ? 0 : s.motion.dz(); }
    public static boolean wasSupported(Player p) { Pose s = poses.get(p.getUniqueId()); return s == null || s.wasSupported; }
    public static boolean duplicatePosition(Player p) { Pose s = poses.get(p.getUniqueId()); return s != null && s.duplicatePosition; }
    public static void seed(Player player, Location at) {
        PacketMotion motion = new PacketMotion(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
        motion.update(true, at.getX(), at.getY(), at.getZ(), true, at.getYaw(), at.getPitch());
        Pose pose = new Pose(at.getWorld().getUID(), motion);
        pose.currentlySupported = MovementEnvironment.supported(at);
        pose.wasSupported = pose.currentlySupported;
        poses.put(player.getUniqueId(), pose);
        CombatHistory.recordPosition(player, at);
    }
    public static void cleanup(UUID id) { poses.remove(id); }
    public static void clear() { poses.clear(); }
}
