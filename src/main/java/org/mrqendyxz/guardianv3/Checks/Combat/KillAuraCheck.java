package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import org.mrqendyxz.guardianv3.Utils.TaskUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KillAuraCheck {

    private final Map<UUID, Integer> lastTargetId = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAttackTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        Player player = (Player) event.getPlayer();
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        int targetId = wrapper.getEntityId();
        long now = System.currentTimeMillis();

        int prevTargetId = lastTargetId.getOrDefault(uuid, targetId);
        long prevAttackTime = lastAttackTime.getOrDefault(uuid, 0L);

        lastTargetId.put(uuid, targetId);
        lastAttackTime.put(uuid, now);

        if (targetId != prevTargetId && (now - prevAttackTime) < 15) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);
            if (b > 2) {
                AlertUtil.sendAlert(player, "KillAura (Type: Multi)", b);
                buffer.put(uuid, 0);
            }
            return;
        }

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

            Vector playerLook = player.getLocation().getDirection().setY(0).normalize();
            Vector targetDir = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0).normalize();
            double dot = playerLook.dot(targetDir);

            if (dot < -0.4) {
                int b = buffer.getOrDefault(uuid, 0) + 1;
                buffer.put(uuid, b);
                if (b > 4) {
                    AlertUtil.sendAlert(player, "KillAura (Type: Angle)", b);
                    buffer.put(uuid, 0);
                }
            } else {
                buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            }
        });
    }

    public void cleanup(UUID uuid) {
        lastTargetId.remove(uuid);
        lastAttackTime.remove(uuid);
        buffer.remove(uuid);
    }
}