package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CrystalAuraCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private final Map<UUID, SustainedRate> place = new ConcurrentHashMap<>();
    private final Map<UUID, SustainedRate> attack = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (CombatHistory.uncertain(p)) { cleanup(id); return; }
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) { cleanup(id); return; }
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            var packet = new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement(event);
            var held = packet.getHand() == com.github.retrooper.packetevents.protocol.player.InteractionHand.MAIN_HAND
                    ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand();
            if (held.getType() != org.bukkit.Material.END_CRYSTAL) return;
            var pos = packet.getBlockPosition();
            if (!p.getWorld().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return;
            var material = WorldCache.type(p.getWorld(), pos.getX(), pos.getY(), pos.getZ());
            if (material != org.bukkit.Material.OBSIDIAN && material != org.bukkit.Material.BEDROCK) return;
            if (place.computeIfAbsent(id, key -> new SustainedRate()).record(PacketContext.now(), 25, 3)) AlertUtil.sendAlert(p, "CrystalAura (PlaceRate)", 3);
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            var packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
            var target = CombatHistory.target(p, packet.getEntityId());
            if (target == null || target.getType() != org.bukkit.entity.EntityType.END_CRYSTAL) return;
            if (attack.computeIfAbsent(id, key -> new SustainedRate()).record(PacketContext.now(), 35, 3)) AlertUtil.sendAlert(p, "CrystalAura (AttackRate)", 3);
        }
    }
    public void cleanup(UUID uuid) { place.remove(uuid); attack.remove(uuid); }
}
