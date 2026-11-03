package org.mrqendyxz.guardianv3.Utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MovementEnvironment {
    private record Key(UUID world, int x, int y, int z) {}
    private static final Map<Key, List<BoundingBox>> shapes = new HashMap<>();
    public static boolean loaded(Location at) {
        World world = at.getWorld();
        if (world == null || !Double.isFinite(at.getX()) || !Double.isFinite(at.getY()) || !Double.isFinite(at.getZ())
                || Math.abs(at.getX()) > 30_000_000 || Math.abs(at.getZ()) > 30_000_000
                || Math.abs(at.getY()) > 30_000_000) return false;
        for (int x = floor(at.getX() - .3) >> 4; x <= floor(at.getX() + .3) >> 4; x++) {
            for (int z = floor(at.getZ() - .3) >> 4; z <= floor(at.getZ() + .3) >> 4; z++) {
                if (!world.isChunkLoaded(x, z)) return false;
            }
        }
        return true;
    }
    public static boolean supported(Location at) {
        if (!loaded(at)) return false;
        double x = at.getX(), y = at.getY(), z = at.getZ();
        for (int bx = floor(x - .3001); bx <= floor(x + .3001); bx++) {
            for (int bz = floor(z - .3001); bz <= floor(z + .3001); bz++) {
                for (int by = floor(y) - 2; by <= floor(y); by++) {
                    for (BoundingBox box : boxes(at.getWorld(), bx, by, bz)) {
                        if (supports(x, y, z, box.getMinX(), box.getMaxX(), box.getMaxY(), box.getMinZ(), box.getMaxZ())) return true;
                    }
                }
            }
        }
        return false;
    }
    public static boolean supported(Location previous, Location at) {
        if (supported(at)) return true;
        if (previous == null || previous.getWorld() != at.getWorld() || !loaded(previous)) return false;
        Location verticalStep = new Location(at.getWorld(), previous.getX(), at.getY(), previous.getZ());
        if (supported(verticalStep)) return true;
        Location xStep = new Location(at.getWorld(), at.getX(), at.getY(), previous.getZ());
        if (supported(xStep)) return true;
        Location zStep = new Location(at.getWorld(), previous.getX(), at.getY(), at.getZ());
        if (supported(zStep)) return true;
        Location midStep = new Location(at.getWorld(), (previous.getX() + at.getX()) * .5, at.getY(), (previous.getZ() + at.getZ()) * .5);
        return supported(midStep);
    }
    public static boolean nearGround(Location previous, Location at) {
        if (supported(previous, at)) return true;
        for (double offset : new double[]{-.08, -.16, .08}) {
            if (supported(previous == null ? null : previous.clone().add(0, offset, 0), at.clone().add(0, offset, 0))) {
                return true;
            }
        }
        return false;
    }
    public static boolean supports(double x, double y, double z, double minX, double maxX,
                                   double top, double minZ, double maxZ) {
        return y >= top - .0625 && y - top <= .09
                && x + .3001 > minX && x - .3001 < maxX && z + .3001 > minZ && z - .3001 < maxZ;
    }
    public static boolean clear(Location at, double height) {
        if (!loaded(at)) return false;
        BoundingBox body = new BoundingBox(at.getX() - .299, at.getY() + .001, at.getZ() - .299,
                at.getX() + .299, at.getY() + height - .001, at.getZ() + .299);
        for (int x = floor(body.getMinX()); x <= floor(body.getMaxX()); x++) {
            for (int z = floor(body.getMinZ()); z <= floor(body.getMaxZ()); z++) {
                for (int y = floor(body.getMinY()) - 1; y <= floor(body.getMaxY()); y++) {
                    for (BoundingBox box : boxes(at.getWorld(), x, y, z)) if (body.overlaps(box)) return false;
                }
            }
        }
        return true;
    }
    public static boolean solidBlocked(Location from, Location to, double radius, double minYOffset, double maxYOffset) {
        if (to == null || !loaded(to)) return false;
        World world = to.getWorld();
        if (from == null || from.getWorld() != world || !loaded(from)) {
            return coreBlockedAt(world, to.getX(), to.getY(), to.getZ(), radius, minYOffset, maxYOffset);
        }
        double dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, Math.min(24, (int) Math.ceil(dist / .22)));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double sx = from.getX() + dx * t;
            double sy = from.getY() + dy * t;
            double sz = from.getZ() + dz * t;
            if (coreBlockedAt(world, sx, sy, sz, radius, minYOffset, maxYOffset)) return true;
        }
        return false;
    }
    private static boolean coreBlockedAt(World world, double x, double y, double z, double r, double minOff, double maxOff) {
        if (maxOff <= minOff) return false;
        BoundingBox core = new BoundingBox(x - r, y + minOff, z - r, x + r, y + maxOff, z + r);
        for (int bx = floor(core.getMinX()); bx <= floor(core.getMaxX()); bx++) {
            for (int bz = floor(core.getMinZ()); bz <= floor(core.getMaxZ()); bz++) {
                for (int by = floor(core.getMinY()); by <= floor(core.getMaxY()); by++) {
                    Material m = WorldCache.type(world, bx, by, bz);
                    if (isIgnoredNoClipMaterial(m)) continue;
                    for (BoundingBox box : boxes(world, bx, by, bz)) {
                        if (core.overlaps(box)) return true;
                    }
                }
            }
        }
        return false;
    }
    private static boolean isIgnoredNoClipMaterial(Material m) {
        if (m == null || m.isAir() || m == Material.WATER || m == Material.LAVA || m == Material.COBWEB
                || m == Material.POWDER_SNOW || m == Material.SOUL_SAND || m == Material.FARMLAND
                || m == Material.DIRT_PATH || m == Material.MOVING_PISTON || m == Material.PISTON_HEAD
                || m == Material.HONEY_BLOCK || m == Material.SLIME_BLOCK || m == Material.SCAFFOLDING) {
            return true;
        }
        String n = m.name();
        return n.contains("DOOR") || n.contains("GATE") || n.contains("SLAB") || n.contains("STAIRS")
                || n.contains("FENCE") || n.contains("WALL") || n.contains("SNOW") || n.contains("CARPET")
                || n.contains("BED") || n.contains("CHEST") || n.contains("SHULKER") || n.contains("LADDER")
                || n.contains("VINE") || n.contains("PANE") || n.contains("BARS");
    }
    private static List<BoundingBox> boxes(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) return List.of();
        return shapes.computeIfAbsent(new Key(world.getUID(), x, y, z), key -> {
            Block block = world.getBlockAt(x, y, z);
            return block.getCollisionShape().getBoundingBoxes().stream()
                    .map(box -> box.clone().shift(x, y, z)).toList();
        });
    }
    private static int floor(double value) { return (int) Math.floor(value); }
    public static void clearCache() { shapes.clear(); }
}
