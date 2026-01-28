package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
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

        TaskUtil.run(() -> {
            if (!player.isOnline()) return;

            Entity target = null;
            for (Entity e : player.getNearbyEntities(6.0, 6.0, 6.0)) {
                if (e.getEntityId() == targetId) {
                    target = e;
                    break;
                }
            }

            if (target == null) return;

            BlockIterator iter = new BlockIterator(player.getEyeLocation(), 0, (int) player.getEyeLocation().distance(target.getLocation()));
            while (iter.hasNext()) {
                Block b = iter.next();
                if (b.getType().isSolid() && !isTransparent(b.getType())) {
                    UUID uuid = player.getUniqueId();
                    int buf = buffer.getOrDefault(uuid, 0) + 1;
                    buffer.put(uuid, buf);
                    if (buf > 2) {
                        AlertUtil.sendAlert(player, "WallHit", buf);
                        buffer.put(uuid, 0);
                    }
                    break;
                }
            }
        });
    }

    private boolean isTransparent(Material m) {
        return m.name().contains("GLASS") || m.name().contains("FENCE") || m.name().contains("DOOR");
    }
}