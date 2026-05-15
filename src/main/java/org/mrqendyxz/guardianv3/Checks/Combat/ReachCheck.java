package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
        if (player == null || player.getGameMode() == GameMode.CREATIVE) return;

        int targetId = wrapper.getEntityId();
        Player target = null;

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getEntityId() == targetId) {
                target = onlinePlayer;
                break;
            }
        }

        if (target == null || target.getWorld() != player.getWorld()) return;

        UUID uuid = player.getUniqueId();

        double distXZ = Math.sqrt(Math.pow(player.getLocation().getX() - target.getLocation().getX(), 2) +
                Math.pow(player.getLocation().getZ() - target.getLocation().getZ(), 2));

        double distY = Math.abs(player.getEyeLocation().getY() - target.getLocation().getY());
        double totalDist = Math.sqrt(distXZ * distXZ + distY * distY);

        double maxReach = 3.1;

        if (player.isSprinting()) maxReach += 0.15;
        if (target.getVelocity().length() > 0.1) maxReach += 0.4;

        double ping = com.github.retrooper.packetevents.PacketEvents.getAPI().getPlayerManager().getPing(player);
        maxReach += (ping / 1000.0) * 2.5;

        if (totalDist > maxReach) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);
            if (b > 4) {
                AlertUtil.sendAlert(player, "Reach", (int) Math.round(totalDist));
                buffer.put(uuid, 0);
            }
        } else {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
        }
    }
}