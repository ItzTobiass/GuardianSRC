package org.mrqendyxz.guardianv3.Utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import java.util.*;

public final class CombatHistory {
    private record Sample(UUID world, long time, BoundingBox box) {}
    private static final Map<UUID, ArrayDeque<Sample>> history = new HashMap<>();
    private static final Map<Integer, Player> players = new HashMap<>();

    public static void capture() {
        players.clear();
        Set<UUID> online = new HashSet<>();
        long now = System.nanoTime() / 1_000_000L;
        for (Player p : Bukkit.getOnlinePlayers()) {
            online.add(p.getUniqueId());
            players.put(p.getEntityId(), p);
            recordBox(p.getUniqueId(), p.getWorld().getUID(), now, p.getBoundingBox().clone());
            Location pkt = PacketContext.location(p);
            if (pkt != null && pkt.getWorld() == p.getWorld()) {
                double w = p.getWidth() * .5, h = p.getHeight();
                recordBox(p.getUniqueId(), p.getWorld().getUID(), now,
                        new BoundingBox(pkt.getX() - w, pkt.getY(), pkt.getZ() - w,
                                pkt.getX() + w, pkt.getY() + h, pkt.getZ() + w));
            }
        }
        history.keySet().retainAll(online);
    }

    public static void recordPosition(Player p, Location at) {
        if (p == null || at == null || at.getWorld() == null) return;
        double w = p.getWidth() * .5, h = p.getHeight();
        recordBox(p.getUniqueId(), at.getWorld().getUID(), PacketContext.now(),
                new BoundingBox(at.getX() - w, at.getY(), at.getZ() - w,
                        at.getX() + w, at.getY() + h, at.getZ() + w));
    }

    private static void recordBox(UUID id, UUID world, long now, BoundingBox box) {
        var samples = history.computeIfAbsent(id, k -> new ArrayDeque<>());
        samples.addLast(new Sample(world, now, box));
        while (samples.size() > 40 || (!samples.isEmpty() && now - samples.peekFirst().time() > 1200)) {
            samples.removeFirst();
        }
    }

    public static Entity target(Player p, int entityId) {
        Player target = players.get(entityId);
        if (target != null && target.isOnline() && target.getWorld() == p.getWorld()) return target;
        for (Entity e : p.getNearbyEntities(8, 8, 8)) if (e.getEntityId() == entityId) return e;
        return null;
    }

    public static boolean uncertain(Player p) {
        return p.getPing() > 300 || p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR;
    }

    public static double range(Player p) {
        return Math.max(0, MovementGuards.attribute(p, Attribute.PLAYER_ENTITY_INTERACTION_RANGE, 3)) + .08;
    }

    public static List<Vector> eyeVectors(Player p) {
        List<Vector> result = new ArrayList<>(6);
        Location cur = PacketContext.location(p);
        result.addAll(eyeVectorsAt(p, cur));
        Location prev = PacketContext.previous(p);
        if (prev.distanceSquared(cur) > 1e-6) {
            result.addAll(eyeVectorsAt(p, prev));
        }
        return result;
    }

    public static List<Vector> eyeVectorsAt(Player p, Location base) {
        double scale = Math.max(.1, MovementGuards.attribute(p, Attribute.GENERIC_SCALE, 1));
        double current = p.getEyeHeight();
        double standing = 1.62 * scale;
        double sneaking = 1.27 * scale;
        List<Vector> eyes = new ArrayList<>(3);
        eyes.add(new Vector(base.getX(), base.getY() + current, base.getZ()));
        if (Math.abs(current - standing) > 1e-3) eyes.add(new Vector(base.getX(), base.getY() + standing, base.getZ()));
        if (Math.abs(current - sneaking) > 1e-3) eyes.add(new Vector(base.getX(), base.getY() + sneaking, base.getZ()));
        return eyes;
    }

    public static List<BoundingBox> boxes(Player attacker, Entity target) {
        List<BoundingBox> result = new ArrayList<>();
        result.add(target.getBoundingBox().clone());
        if (target instanceof Player tp) {
            Location pkt = PacketContext.location(tp);
            if (pkt != null && pkt.getWorld() == target.getWorld()) {
                double w = tp.getWidth() * .5, h = tp.getHeight();
                result.add(new BoundingBox(pkt.getX() - w, pkt.getY(), pkt.getZ() - w,
                        pkt.getX() + w, pkt.getY() + h, pkt.getZ() + w));
            }
            Location prev = PacketContext.previous(tp);
            if (prev != null && prev.getWorld() == target.getWorld()) {
                double w = tp.getWidth() * .5, h = tp.getHeight();
                result.add(new BoundingBox(prev.getX() - w, prev.getY(), prev.getZ() - w,
                        prev.getX() + w, prev.getY() + h, prev.getZ() + w));
            }
        }
        var samples = history.get(target.getUniqueId());
        if (samples == null) return result;
        long now = PacketContext.now();
        long ping = Math.max(0, attacker.getPing());
        long maxAge = Math.min(400, ping + 180L);
        var iter = samples.descendingIterator();
        BoundingBox prevBox = null;
        while (iter.hasNext() && result.size() < 20) {
            Sample sample = iter.next();
            if (sample.world().equals(target.getWorld().getUID())
                    && sample.time() >= now - maxAge && sample.time() <= now + 100) {
                BoundingBox current = sample.box().clone();
                result.add(current);
                if (prevBox != null) {
                    result.add(new BoundingBox(
                            (current.getMinX() + prevBox.getMinX()) * .5,
                            (current.getMinY() + prevBox.getMinY()) * .5,
                            (current.getMinZ() + prevBox.getMinZ()) * .5,
                            (current.getMaxX() + prevBox.getMaxX()) * .5,
                            (current.getMaxY() + prevBox.getMaxY()) * .5,
                            (current.getMaxZ() + prevBox.getMaxZ()) * .5
                    ));
                }
                prevBox = current;
            }
        }
        return result;
    }

    public static double distance(Vector eye, BoundingBox box) {
        return ReachGeometry.distance(eye.getX(), eye.getY(), eye.getZ(), box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }

    public static double minDistance(List<Vector> eyes, BoundingBox box) {
        double best = Double.MAX_VALUE;
        for (Vector eye : eyes) best = Math.min(best, distance(eye, box));
        return best;
    }

    public static void cleanup(UUID id) { history.remove(id); players.values().removeIf(p -> p.getUniqueId().equals(id)); }
    public static void clear() { history.clear(); players.clear(); }
}
