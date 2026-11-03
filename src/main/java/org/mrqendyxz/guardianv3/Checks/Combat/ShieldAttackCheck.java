package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShieldAttackCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        var packet = new WrapperPlayClientInteractEntity(event);
        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
        Player p = event.getPlayer();
        var state = evidence.computeIfAbsent(p.getUniqueId(), id -> new MovementEvidence());
        boolean bad = ItemUseTracker.stable(p, org.bukkit.Material.SHIELD) || p.isBlocking();
        if (state.record(bad, false, 2)) { AlertUtil.sendAlert(p, "ShieldAttack", state.buffer()); state.reset(); }
    }
    public void cleanup(UUID uuid) { evidence.remove(uuid); }
}
