package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HitBoxCheck {
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        Player attacker = (Player) event.getPlayer();
        int targetId = wrapper.getEntityId();

        Player target = null;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getEntityId() == targetId) {
                target = onlinePlayer;
                break;
            }
        }

        if (target == null || target.getWorld() != attacker.getWorld()) return;

        UUID uuid = attacker.getUniqueId();
        Vector attackerEye = attacker.getEyeLocation().toVector();
        Vector targetLoc = target.getLocation().toVector();

        double distanceXZ = Math.sqrt(Math.pow(attackerEye.getX() - targetLoc.getX(), 2) + Math.pow(attackerEye.getZ() - targetLoc.getZ(), 2));
        distanceXZ -= 0.4;

        double distanceY = Math.abs(attackerEye.getY() - targetLoc.getY());

        if (distanceXZ > 3.6 || distanceY > 3.2) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);
            if (b > 5) {
                AlertUtil.sendAlert(attacker, "HitBox", b);
                buffer.put(uuid, 0);
            }
        } else {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
        }
    }
}