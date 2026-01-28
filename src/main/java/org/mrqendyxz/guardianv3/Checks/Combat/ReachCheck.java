package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import org.mrqendyxz.guardianv3.Utils.TaskUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ReachCheck {
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
            if (!player.isOnline() || player.getGameMode() == GameMode.CREATIVE) return;

            Entity target = null;
            for (Entity e : player.getWorld().getEntities()) {
                if (e.getEntityId() == targetId) {
                    target = e;
                    break;
                }
            }

            if (target == null) return;

            double distance = player.getEyeLocation().distance(target.getLocation());
            double maxReach = player.isSprinting() ? 3.5 : 3.2;

            if (distance > maxReach) {
                int b = buffer.getOrDefault(uuid, 0) + 1;
                buffer.put(uuid, b);
                if (b > 2) {
                    AlertUtil.sendAlert(player, "Reach", (int) Math.round(distance));
                    buffer.put(uuid, 0);
                }
            } else {
                buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            }
        });
    }
}