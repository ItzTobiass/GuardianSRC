package org.mrqendyxz.guardianv3.Utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WorldCache {
    private record Key(UUID world, int x, int y, int z) {}
    private static final Map<Key, Material> blocks = new HashMap<>();
    public static Material type(Location location) {
        return type(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    public static Material type(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return Material.AIR;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return Material.BEDROCK;
        return blocks.computeIfAbsent(new Key(world.getUID(), x, y, z), key -> world.getBlockAt(x, y, z).getType());
    }
    public static boolean liquid(Location location) {
        Material type = type(location);
        return type == Material.WATER || type == Material.LAVA;
    }
    public static void clear() { blocks.clear(); }
}
