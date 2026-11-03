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

public class ReachCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private record PendingReach(List<Vector> preEyes, List<BoundingBox> boxes, double allowed) {}
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();
    private final Map<UUID, PendingReach> pending = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            PendingReach check = pending.remove(id);
            if (check == null) return;
            var postEyes = CombatHistory.eyeVectors(player);
            double preDist = check.boxes().stream().mapToDouble(b -> CombatHistory.minDistance(check.preEyes(), b)).min().orElse(0);
            double postDist = check.boxes().stream().mapToDouble(b -> CombatHistory.minDistance(postEyes, b)).min().orElse(0);
            double reach = Math.min(preDist, postDist);
            evaluate(player, id, reach, check.allowed());
            return;
        }
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        var packet = new WrapperPlayClientInteractEntity(event);
        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
        if (CombatHistory.uncertain(player)) { cleanup(id); return; }
        var target = CombatHistory.target(player, packet.getEntityId());
        if (target == null || target.getWorld() != player.getWorld()) { cleanup(id); return; }
        var preEyes = CombatHistory.eyeVectors(player);
        var boxes = CombatHistory.boxes(player, target);
        double pingPad = Math.min(.18, Math.max(0, player.getPing()) * .0008);
        double allowed = CombatHistory.range(player) + pingPad;
        double preDist = boxes.stream().mapToDouble(b -> CombatHistory.minDistance(preEyes, b)).min().orElse(0);
        if (preDist <= allowed) {
            pending.remove(id);
            evaluate(player, id, preDist, allowed);
        } else {
            pending.put(id, new PendingReach(preEyes, boxes, allowed));
        }
    }

    private void evaluate(Player player, UUID id, double reach, double allowed) {
        boolean bad = reach > allowed;
        var state = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        if (state.record(bad, reach > allowed + .35, 3)) {
            AlertUtil.sendAlert(player, "Reach", state.buffer());
            state.reset();
        }
    }

    public void cleanup(UUID uuid) {
        evidence.remove(uuid);
        pending.remove(uuid);
    }
}
