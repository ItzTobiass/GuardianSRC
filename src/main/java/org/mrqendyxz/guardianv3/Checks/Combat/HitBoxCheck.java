package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HitBoxCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private record PendingAttack(List<Vector> eyes, List<BoundingBox> boxes, double range) {}
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();
    private final Map<UUID, PendingAttack> pending = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        Player player = event.getPlayer(); UUID id = player.getUniqueId();
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            PendingAttack attack = pending.remove(id);
            if (attack == null) return;
            var direction = PacketContext.location(player).getDirection();
            boolean validPostFlying = hitsAny(attack.eyes(), direction, attack.boxes(), attack.range())
                    || hitsAny(CombatHistory.eyeVectors(player), direction, attack.boxes(), attack.range());
            recordResult(player, id, !validPostFlying);
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
        double range = CombatHistory.range(player) + .3;
        var boxes = CombatHistory.boxes(player, target);
        if (boxes.stream().noneMatch(box -> CombatHistory.minDistance(eyes, box) <= range)) { cleanup(id); return; }
        var direction = PacketContext.location(player).getDirection();
        if (hitsAny(eyes, direction, boxes, range)) {
            recordResult(player, id, false);
        } else {
            pending.put(id, new PendingAttack(eyes, boxes, range));
        }
    }

    private boolean hitsAny(List<Vector> eyes, Vector direction, List<BoundingBox> boxes, double range) {
        for (BoundingBox raw : boxes) {
            BoundingBox box = raw.clone().expand(.28);
            for (Vector eye : eyes) {
                if (box.contains(eye) || box.rayTrace(eye, direction, range + .3) != null) return true;
            }
        }
        return false;
    }

    private void recordResult(Player player, UUID id, boolean bad) {
        var state = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        if (state.record(bad, false, 4)) {
            AlertUtil.sendAlert(player, "HitBox", state.buffer());
            state.reset();
        }
    }

    public void cleanup(UUID uuid) { evidence.remove(uuid); pending.remove(uuid); }
}
