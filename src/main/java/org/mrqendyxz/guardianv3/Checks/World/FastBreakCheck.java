package org.mrqendyxz.guardianv3.Checks.World;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import org.mrqendyxz.guardianv3.Utils.PacketContext;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FastBreakCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private record Dig(UUID world, int x, int y, int z, long started, long minimumMillis, long batch, org.bukkit.Material type, int tool) {}
    private final Map<UUID, Dig> breakStart = new ConcurrentHashMap<>();
    private final Map<UUID, org.mrqendyxz.guardianv3.Utils.MovementEvidence> evidence = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
        var packet = new WrapperPlayClientPlayerDigging(event);
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            cleanup(id); return;
        }
        var pos = packet.getBlockPosition();
        if (packet.getAction() == DiggingAction.START_DIGGING) {
            breakStart.remove(id);
            if (!player.getWorld().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return;
            if (pos.getY() < player.getWorld().getMinHeight() || pos.getY() >= player.getWorld().getMaxHeight()) return;
            var block = player.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            float progress = block.getBreakSpeed(player);
            if (!Float.isFinite(progress) || progress <= 0 || progress >= .7f) return;
            long minimum = Math.max(0, ((long) Math.ceil(1.0 / progress) - 2) * 50);
            breakStart.put(id, new Dig(player.getWorld().getUID(), pos.getX(), pos.getY(), pos.getZ(), PacketContext.now(), minimum, PacketContext.batch(), block.getType(), tool(player)));
        } else if (packet.getAction() == DiggingAction.CANCELLED_DIGGING) {
            breakStart.remove(id);
        } else if (packet.getAction() == DiggingAction.FINISHED_DIGGING) {
            Dig dig = breakStart.remove(id);
            if (dig == null || !dig.world().equals(player.getWorld().getUID())
                    || dig.x() != pos.getX() || dig.y() != pos.getY() || dig.z() != pos.getZ()) return;
            if (dig.batch() == PacketContext.batch() || dig.tool() != tool(player)) return;
            if (!player.getWorld().isChunkLoaded(dig.x() >> 4, dig.z() >> 4)) return;
            var block = player.getWorld().getBlockAt(dig.x(), dig.y(), dig.z());
            if (block.getType() != dig.type()) return;
            float speed = block.getBreakSpeed(player);
            if (!Float.isFinite(speed) || speed <= 0 || speed >= .7f) return;
            long current = Math.max(0, ((long) Math.ceil(1.0 / speed) - 2) * 50);
            long tolerance = Math.min(300, Math.max(50, player.getPing() + 50L));
            long required = Math.min(current, dig.minimumMillis()) - tolerance;
            if (required <= 0) return;
            var e = evidence.computeIfAbsent(id, key -> new org.mrqendyxz.guardianv3.Utils.MovementEvidence());
            if (e.record(PacketContext.now() - dig.started() < required, false, 3)) {
                AlertUtil.sendAlert(player, "FastBreak", e.buffer()); e.reset();
            }
        }
    }
    private int tool(Player p) {
        var item = p.getInventory().getItemInMainHand();
        return java.util.Objects.hash(item.getType(), item.getEnchantments());
    }
    public void cleanup(UUID uuid) { breakStart.remove(uuid); evidence.remove(uuid); }
}
