package org.mrqendyxz.guardianv3.Checks.World;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScaffoldCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private record PendingPlace(List<Vector> eyes, BoundingBox box, double range) {}
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();
    private final Map<UUID, PendingPlace> pending = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            PendingPlace place = pending.remove(id);
            if (place == null) return;
            var direction = PacketContext.location(p).getDirection();
            boolean validPostFlying = intersects(place.eyes(), direction, place.box(), place.range())
                    || intersects(CombatHistory.eyeVectors(p), direction, place.box(), place.range());
            recordResult(p, id, !validPostFlying);
            return;
        }
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
        if (CombatHistory.uncertain(p)) { cleanup(id); return; }
        if (pending.remove(id) != null) {
            recordResult(p, id, true);
        }
        var packet = new WrapperPlayClientPlayerBlockPlacement(event);
        var held = packet.getHand() == InteractionHand.MAIN_HAND ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand();
        if (!held.getType().isBlock() || held.getType().isAir() || p.getPing() > 250) { cleanup(id); return; }
        var pos = packet.getBlockPosition();
        if (!p.getWorld().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4) || pos.getY() < p.getWorld().getMinHeight() || pos.getY() >= p.getWorld().getMaxHeight()) return;
        var material = WorldCache.type(p.getWorld(), pos.getX(), pos.getY(), pos.getZ());
        if (material.isAir() || (material.isInteractable() && !p.isSneaking())) { cleanup(id); return; }
        boolean below = pos.getY() < PacketContext.location(p).getY();
        if (!below) {
            recordResult(p, id, false);
            return;
        }
        double range = MovementGuards.attribute(p, org.bukkit.attribute.Attribute.PLAYER_BLOCK_INTERACTION_RANGE, 4.5) + .45;
        var box = new BoundingBox(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1).expand(.35);
        var eyes = CombatHistory.eyeVectors(p);
        var direction = PacketContext.location(p).getDirection();
        if (intersects(eyes, direction, box, range)) {
            recordResult(p, id, false);
        } else {
            pending.put(id, new PendingPlace(eyes, box, range));
        }
    }

    private boolean intersects(List<Vector> eyes, Vector direction, BoundingBox box, double range) {
        for (Vector eye : eyes) {
            if (box.contains(eye) || box.rayTrace(eye, direction, range) != null) return true;
        }
        return false;
    }

    private void recordResult(Player p, UUID id, boolean bad) {
        var e = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        if (e.record(bad, false, 6)) {
            AlertUtil.sendAlert(p, "Scaffold (PlacementRay)", e.buffer());
            e.reset();
        }
    }

    public void cleanup(UUID uuid) { evidence.remove(uuid); pending.remove(uuid); }
}
