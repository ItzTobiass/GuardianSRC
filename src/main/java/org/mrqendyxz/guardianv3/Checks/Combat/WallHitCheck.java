package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import org.mrqendyxz.guardianv3.Utils.TaskUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WallHitCheck {
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        Player player = (Player) event.getPlayer();
        if (player == null) return;

        int targetId = wrapper.getEntityId();
        UUID uuid = player.getUniqueId();

        TaskUtil.run(() -> {
            if (!player.isOnline()) return;

            Entity target = null;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getEntityId() == targetId) {
                    target = online;
                    break;
                }
            }

            if (target == null || target.getWorld() != player.getWorld()) return;

            Vector start = player.getEyeLocation().toVector();
            Vector end = target.getLocation().add(0, 1.0, 0).toVector();
            Vector direction = end.clone().subtract(start);
            double distance = start.distance(end);

            if (distance < 0.5) return;

            RayTraceResult result = player.getWorld().rayTraceBlocks(
                    player.getEyeLocation(),
                    direction.normalize(),
                    distance,
                    FluidCollisionMode.NEVER,
                    true
            );

            if (result != null && result.getHitBlock() != null) {
                Material type = result.getHitBlock().getType();
                if (type.isSolid() && !isTransparent(type)) {
                    int buf = buffer.getOrDefault(uuid, 0) + 1;
                    buffer.put(uuid, buf);
                    if (buf > 5) {
                        AlertUtil.sendAlert(player, "WallHit", buf);
                        buffer.put(uuid, 0);
                    }
                    return;
                }
            }
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
        });
    }

    private boolean isTransparent(Material m) {
        String name = m.name();
        return !m.isOccluding() ||
                name.contains("GLASS") ||
                name.contains("FENCE") ||
                name.contains("DOOR") ||
                name.contains("WEB") ||
                name.contains("LEAVES") ||
                name.contains("TRAPDOOR") ||
                name.contains("WALL") ||
                name.contains("GATE") ||
                name.contains("STAIRS") ||
                name.contains("SLAB") ||
                name.contains("BARS") ||
                name.contains("PANE");
    }
}