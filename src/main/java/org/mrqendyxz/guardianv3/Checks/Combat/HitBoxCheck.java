package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HitBoxCheck {

    private final Map<UUID, Integer> buffer = new HashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);

            if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                return;
            }

            Player player = (Player) event.getPlayer();
            if (player.isOp()) return;

            Entity target = null;
            for (Entity entity : player.getNearbyEntities(6, 6, 6)) {
                if (entity.getEntityId() == wrapper.getEntityId()) {
                    target = entity;
                    break;
                }
            }

            if (target == null) return;

            UUID uuid = player.getUniqueId();

            Vector eyeLocation = player.getEyeLocation().toVector();
            Vector lookDirection = player.getEyeLocation().getDirection().normalize();

            Vector targetPos = target.getLocation().toVector().add(new Vector(0, 1, 0));
            Vector directionToTarget = targetPos.subtract(eyeLocation).normalize();

            double dot = lookDirection.dot(directionToTarget);

            if (dot < 0.91) {
                int b = buffer.getOrDefault(uuid, 0) + 1;
                buffer.put(uuid, b);

                if (b > 3) {
                    AlertUtil.sendAlert(player, "HitBox (Expand)", b);
                }
            } else {
                buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            }
        }
    }
}