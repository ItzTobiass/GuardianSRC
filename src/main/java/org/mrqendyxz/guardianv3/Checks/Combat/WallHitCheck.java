package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WallHitCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private record PendingWallHit(List<Vector> eyes, List<BoundingBox> boxes, double range) {}
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();
    private final Map<UUID, PendingWallHit> pending = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        Player player = event.getPlayer(); UUID id = player.getUniqueId();
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            PendingWallHit hit = pending.remove(id);
            if (hit == null) return;
            int status = evaluate(player.getWorld(), hit.eyes(), PacketContext.location(player).getDirection(), hit.boxes(), hit.range());
            if (status == -1) { cleanup(id); return; }
            recordResult(player, id, status == 1);
            return;
        }
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        var packet = new WrapperPlayClientInteractEntity(event);
        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
        if (CombatHistory.uncertain(player)) { cleanup(id); return; }
        if (pending.remove(id) != null) {
            recordResult(player, id, true);
        }
        var target = CombatHistory.target(player, packet.getEntityId());
        if (target == null || target.getWorld() != player.getWorld()) { cleanup(id); return; }
        var eyes = CombatHistory.eyeVectors(player);
        double range = Math.min(8, CombatHistory.range(player));
        var boxes = CombatHistory.boxes(player, target);
        int status = evaluate(player.getWorld(), eyes, PacketContext.location(player).getDirection(), boxes, range);
        if (status == -1) { cleanup(id); return; }
        if (status == 0) {
            recordResult(player, id, false);
        } else {
            pending.put(id, new PendingWallHit(eyes, boxes, range));
        }
    }

    private int evaluate(World world, List<Vector> eyes, Vector direction, List<BoundingBox> boxes, double range) {
        boolean intersects = false, allBlocked = true;
        for (BoundingBox raw : boxes) {
            BoundingBox box = raw.clone().expand(.15);
            for (Vector eye : eyes) {
                if (box.contains(eye)) return 0;
                var hit = box.rayTrace(eye, direction, range);
                if (hit == null) continue;
                intersects = true;
                double distance = eye.distance(hit.getHitPosition());
                Location eyeLoc = new Location(world, eye.getX(), eye.getY(), eye.getZ());
                for (double d = 0; d <= distance; d += .5) {
                    var at = eyeLoc.clone().add(direction.clone().multiply(d));
                    if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return -1;
                }
                var obstruction = world.rayTraceBlocks(eyeLoc, direction,
                        Math.max(0, distance - .1), org.bukkit.FluidCollisionMode.NEVER, true);
                if (obstruction == null || obstruction.getHitBlock() == null || nonOccluding(obstruction.getHitBlock().getType())) {
                    allBlocked = false;
                    break;
                }
            }
            if (!allBlocked) break;
        }
        return (intersects && allBlocked) ? 1 : 0;
    }

    private boolean nonOccluding(Material type) {
        String name = type.name();
        return type == Material.COBWEB || name.contains("FENCE") || name.contains("WALL")
                || name.contains("IRON_BARS") || name.contains("PANE") || name.contains("CHAIN");
    }

    private void recordResult(Player player, UUID id, boolean bad) {
        var state = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        if (state.record(bad, false, 5)) {
            AlertUtil.sendAlert(player, "WallHit", state.buffer());
            state.reset();
        }
    }

    public void cleanup(UUID uuid) { evidence.remove(uuid); pending.remove(uuid); }
}
