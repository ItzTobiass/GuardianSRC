package org.mrqendyxz.guardianv3.Checks.World;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FastPlaceCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private final Map<UUID, SustainedRate> rates = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
        Player p = event.getPlayer();
        if (CombatHistory.uncertain(p)) { cleanup(p.getUniqueId()); return; }
        var packet = new WrapperPlayClientPlayerBlockPlacement(event);
        var held = packet.getHand() == InteractionHand.MAIN_HAND ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand();
        if (!held.getType().isBlock() || held.getType().isAir()) { cleanup(p.getUniqueId()); return; }
        var pos = packet.getBlockPosition();
        if (!p.getWorld().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return;
        var target = WorldCache.type(p.getWorld(), pos.getX(), pos.getY(), pos.getZ());
        if (target.isAir() || (target.isInteractable() && !p.isSneaking())) return;
        int limit = Math.max(1, org.mrqendyxz.guardianv3.Guardianv3.getInstance().getConfig().getInt("checks.fastplace.max-per-second", 25));
        if (rates.computeIfAbsent(p.getUniqueId(), id -> new SustainedRate()).record(PacketContext.now(), limit, 3)) AlertUtil.sendAlert(p, "FastPlace", 3);
    }
    public void cleanup(UUID uuid) { rates.remove(uuid); }
}
