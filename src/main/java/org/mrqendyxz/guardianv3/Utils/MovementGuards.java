package org.mrqendyxz.guardianv3.Utils;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.potion.PotionEffectType;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MovementGuards {
    private record PlacedBlock(UUID world, int x, int y, int z, long expires) {}
    private static final Map<UUID, Long> specialUntil = new HashMap<>();
    private static final Map<UUID, Long> iceUntil = new HashMap<>();
    private static final Map<UUID, Long> ceilingUntil = new HashMap<>();
    private static final Map<UUID, Long> bridgeUntil = new HashMap<>();
    private static final Map<UUID, ArrayDeque<PlacedBlock>> placedBlocks = new HashMap<>();

    public static double attribute(Player player, Attribute name, double fallback) {
        var instance = player.getAttribute(name);
        double value = instance == null ? fallback : instance.getValue();
        return Double.isFinite(value) ? value : fallback;
    }
    public static boolean mode(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR
                || p.getAllowFlight() || p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isRiptiding();
    }
    public static void handlePlacement(Player p, PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
        WorldCache.clear();
        MovementEnvironment.clearCache();
        var packet = new WrapperPlayClientPlayerBlockPlacement(event);
        var held = packet.getHand() == InteractionHand.MAIN_HAND
                ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand();
        if (held.getType().name().contains("WIND_CHARGE")
                || p.getInventory().getItemInMainHand().getType().name().contains("WIND_CHARGE")
                || p.getInventory().getItemInOffHand().getType().name().contains("WIND_CHARGE")) {
            VelocityProtection.recordWindCharge(p);
            return;
        }
        if (!held.getType().isBlock() || held.getType().isAir()) return;
        var pos = packet.getBlockPosition();
        World world = p.getWorld();
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return;
        if (pos.getY() < world.getMinHeight() || pos.getY() >= world.getMaxHeight()) return;
        if (!hasAdjacentSolidOrSelf(world, pos.getX(), pos.getY(), pos.getZ())) return;
        BlockFace face = packet.getFace();
        int bx = pos.getX() + (face == null ? 0 : face.getModX());
        int by = pos.getY() + (face == null ? 0 : face.getModY());
        int bz = pos.getZ() + (face == null ? 0 : face.getModZ());
        Location at = PacketContext.location(p);
        if (Math.abs(bx + .5 - at.getX()) > 4.5 || Math.abs(bz + .5 - at.getZ()) > 4.5 || Math.abs(by - at.getY()) > 4.5) return;
        UUID id = p.getUniqueId();
        long now = PacketContext.now();
        var queue = placedBlocks.computeIfAbsent(id, key -> new ArrayDeque<>());
        while (queue.size() >= 8) queue.removeFirst();
        queue.addLast(new PlacedBlock(world.getUID(), bx, by, bz, now + 600));
        queue.addLast(new PlacedBlock(world.getUID(), pos.getX(), pos.getY(), pos.getZ(), now + 600));
        if (by <= Math.floor(at.getY() + .5) && by >= Math.floor(at.getY() - 2.0)
                && Math.abs(bx + .5 - at.getX()) <= 2.2 && Math.abs(bz + .5 - at.getZ()) <= 2.2) {
            bridgeUntil.put(id, now + 500);
        }
    }
    private static boolean hasAdjacentSolidOrSelf(World world, int x, int y, int z) {
        if (!WorldCache.type(world, x, y, z).isAir()) return true;
        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] d : dirs) {
            if (!WorldCache.type(world, x + d[0], y + d[1], z + d[2]).isAir()) return true;
        }
        return false;
    }
    public static boolean supportedByPlacement(Player p, Location at) {
        var queue = placedBlocks.get(p.getUniqueId());
        if (queue == null || queue.isEmpty() || at.getWorld() == null) return false;
        long now = PacketContext.now();
        UUID worldId = at.getWorld().getUID();
        var iter = queue.iterator();
        while (iter.hasNext()) {
            PlacedBlock b = iter.next();
            if (now > b.expires()) { iter.remove(); continue; }
            if (!b.world().equals(worldId)) continue;
            double top = b.y() + 1.0;
            if (at.getY() >= b.y() - .05 && at.getY() <= top + .15
                    && at.getX() + .45 >= b.x() && at.getX() - .45 <= b.x() + 1.0
                    && at.getZ() + .45 >= b.z() && at.getZ() - .45 <= b.z() + 1.0) {
                return true;
            }
        }
        return false;
    }
    public static boolean bridging(Player p) {
        return PacketContext.now() < bridgeUntil.getOrDefault(p.getUniqueId(), Long.MIN_VALUE)
                || supportedByPlacement(p, PacketContext.location(p));
    }
    public static boolean specialBlock(Material m) {
        if (m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN
                || m == Material.POWDER_SNOW || m == Material.COBWEB) return true;
        String n = m.name();
        return n.contains("VINE") || n.contains("LADDER") || n.contains("SCAFFOLDING");
    }
    public static boolean touchesSpecialBlock(Location at, double height) {
        World world = at.getWorld();
        if (world == null) return false;
        int minX = (int) Math.floor(at.getX() - .3001), maxX = (int) Math.floor(at.getX() + .3001);
        int minZ = (int) Math.floor(at.getZ() - .3001), maxZ = (int) Math.floor(at.getZ() + .3001);
        int minY = (int) Math.floor(at.getY() - .05), maxY = (int) Math.floor(at.getY() + Math.max(1.0, height));
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (specialBlock(WorldCache.type(world, x, y, z))) return true;
                }
            }
        }
        return false;
    }
    public static boolean touchesLiquidFootprint(Location at, double minOffset, double maxOffset) {
        World world = at.getWorld();
        if (world == null) return false;
        int minX = (int) Math.floor(at.getX() - .3001), maxX = (int) Math.floor(at.getX() + .3001);
        int minZ = (int) Math.floor(at.getZ() - .3001), maxZ = (int) Math.floor(at.getZ() + .3001);
        int minY = (int) Math.floor(at.getY() + minOffset), maxY = (int) Math.floor(at.getY() + maxOffset);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Material m = WorldCache.type(world, x, y, z);
                    if (m == Material.WATER || m == Material.LAVA) return true;
                }
            }
        }
        return false;
    }
    public static boolean nearBubbleColumn(Location at) {
        World world = at.getWorld();
        if (world == null) return false;
        int minX = (int) Math.floor(at.getX() - .4), maxX = (int) Math.floor(at.getX() + .4);
        int minZ = (int) Math.floor(at.getZ() - .4), maxZ = (int) Math.floor(at.getZ() + .4);
        int minY = (int) Math.floor(at.getY() - 1.0), maxY = (int) Math.floor(at.getY() + 1.8);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (WorldCache.type(world, x, y, z) == Material.BUBBLE_COLUMN) return true;
                }
            }
        }
        return false;
    }
    public static boolean ordinary(Player p) {
        Location at = PacketContext.location(p);
        return mode(p) || !MovementEnvironment.loaded(at)
                || touchesSpecialBlock(at, p.getHeight())
                || exceptionalCollision(at)
                || p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || Math.abs(attribute(p, Attribute.GENERIC_GRAVITY, .08) - .08) > 1e-6
                || Math.abs(attribute(p, Attribute.GENERIC_SCALE, 1) - 1) > 1e-6;
    }
    public static boolean supportedByEntity(Player p, Location at) {
        for (Entity e : p.getNearbyEntities(1.5, 1.5, 1.5)) {
            if (e instanceof Boat || e instanceof Shulker) {
                var box = e.getBoundingBox();
                if (at.getX() + .35 >= box.getMinX() && at.getX() - .35 <= box.getMaxX()
                        && at.getZ() + .35 >= box.getMinZ() && at.getZ() - .35 <= box.getMaxZ()
                        && at.getY() >= box.getMinY() - .2 && at.getY() <= box.getMaxY() + .25) {
                    return true;
                }
            }
        }
        return false;
    }
    public static boolean exceptionalCollision(Location at) {
        for (int x = at.getBlockX() - 1; x <= at.getBlockX() + 1; x++) {
            for (int z = at.getBlockZ() - 1; z <= at.getBlockZ() + 1; z++) {
                for (int y = at.getBlockY() - 1; y <= at.getBlockY() + 1; y++) {
                    Material m = WorldCache.type(at.getWorld(), x, y, z);
                    if (m == Material.MOVING_PISTON || m == Material.PISTON_HEAD || m == Material.HONEY_BLOCK) return true;
                }
            }
        }
        return false;
    }
    public static boolean secondary(Player p) {
        return ordinary(p) || PacketContext.now() < specialUntil.getOrDefault(p.getUniqueId(), Long.MIN_VALUE)
                || VelocityProtection.allowance(p) > .02 || VelocityProtection.verticalAllowance(p) > .02;
    }
    public static double footprintBounceFactor(Location at) {
        World world = at.getWorld();
        if (world == null) return 0;
        int minX = (int) Math.floor(at.getX() - .35), maxX = (int) Math.floor(at.getX() + .35);
        int minZ = (int) Math.floor(at.getZ() - .35), maxZ = (int) Math.floor(at.getZ() + .35);
        double factor = 0;
        for (double dy : new double[]{-.05, -.55}) {
            int y = (int) Math.floor(at.getY() + dy);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Material below = WorldCache.type(world, x, y, z);
                    if (below == Material.SLIME_BLOCK) return 1.0;
                    if (below.name().endsWith("_BED")) factor = Math.max(factor, .66);
                }
            }
        }
        return factor;
    }
    public static boolean footprintHasIce(Location at) {
        World world = at.getWorld();
        if (world == null) return false;
        int minX = (int) Math.floor(at.getX() - .35), maxX = (int) Math.floor(at.getX() + .35);
        int minZ = (int) Math.floor(at.getZ() - .35), maxZ = (int) Math.floor(at.getZ() + .35);
        for (double dy : new double[]{-.05, -.55, -.85}) {
            int y = (int) Math.floor(at.getY() + dy);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (WorldCache.type(world, x, y, z).name().contains("ICE")) return true;
                }
            }
        }
        return false;
    }
    public static void observe(Player p) {
        UUID id = p.getUniqueId();
        long now = PacketContext.now();
        Location at = PacketContext.location(p);
        if (mode(p) || touchesSpecialBlock(at, p.getHeight())) specialUntil.put(id, now + 500);
        if (MovementEnvironment.supported(PacketContext.previous(p), at) && footprintBounceFactor(at) > 0) {
            specialUntil.put(id, now + 1500);
        }
        if (footprintHasIce(at)) {
            iceUntil.put(id, now + 1000);
        }
        if (!MovementEnvironment.clear(at.clone().add(0, .25, 0), p.getHeight())) {
            ceilingUntil.put(id, now + 500);
        }
    }
    public static boolean iceActive(Player p) {
        return PacketContext.now() < iceUntil.getOrDefault(p.getUniqueId(), Long.MIN_VALUE)
                || footprintHasIce(PacketContext.location(p));
    }
    public static boolean ceilingActive(Player p) {
        return PacketContext.now() < ceilingUntil.getOrDefault(p.getUniqueId(), Long.MIN_VALUE);
    }
    public static double jump(Player p) {
        double jump = attribute(p, Attribute.GENERIC_JUMP_STRENGTH, .42);
        var boost = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
        return jump + (boost == null ? 0 : .1 * (boost.getAmplifier() + 1));
    }
    public static int depthStrider(Player p) {
        var boots = p.getInventory().getBoots();
        return boots == null ? 0 : boots.getEnchantmentLevel(Enchantment.DEPTH_STRIDER);
    }
    public static double speedMultiplier(Player p) {
        double mult = Math.max(1, Math.max(attribute(p, Attribute.GENERIC_MOVEMENT_SPEED, .1) / .1, p.getWalkSpeed() / .2));
        if (p.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) mult = Math.max(mult, 2.2);
        var boots = p.getInventory().getBoots();
        if (boots != null) {
            int soulSpeed = boots.getEnchantmentLevel(Enchantment.SOUL_SPEED);
            if (soulSpeed > 0) {
                mult = Math.max(mult, 1.0 + 0.3 + soulSpeed * 0.15);
            }
        }
        return mult;
    }
    public static void cleanup(UUID id) {
        specialUntil.remove(id);
        iceUntil.remove(id);
        ceilingUntil.remove(id);
        bridgeUntil.remove(id);
        placedBlocks.remove(id);
    }
    public static void clear() {
        specialUntil.clear();
        iceUntil.clear();
        ceilingUntil.clear();
        bridgeUntil.clear();
        placedBlocks.clear();
    }
}
