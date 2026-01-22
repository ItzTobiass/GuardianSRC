package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

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
        int targetId = wrapper.getEntityId();

        Entity target = null;
        for (Entity entity : player.getNearbyEntities(6, 6, 6)) {
            if (entity.getEntityId() == targetId) {
                target = entity;
                break;
            }
        }

        if (target == null) return;

        double distance = player.getEyeLocation().distance(target.getLocation());

        double maxReach = (player.getGameMode() == GameMode.CREATIVE) ? 5.2 : 3.2;

        if (distance > maxReach) {
            UUID uuid = player.getUniqueId();
            int vl = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, vl);

            if (vl > 2) {
                AlertUtil.sendAlert(player, "Reach", vl);

            }
        } else {
            buffer.put(player.getUniqueId(), Math.max(0, buffer.getOrDefault(player.getUniqueId(), 0) - 1));
        }
    }
}