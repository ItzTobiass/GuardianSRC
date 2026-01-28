package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import org.mrqendyxz.guardianv3.Utils.TaskUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KillAuraCheck {
    private final Map<UUID, Float> lastYaw = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        Player player = (Player) event.getPlayer();
        if (player == null) return;

        TaskUtil.run(() -> {
            if (!player.isOnline()) return;
            float yaw = player.getLocation().getYaw();
            float last = lastYaw.getOrDefault(player.getUniqueId(), yaw);
            lastYaw.put(player.getUniqueId(), yaw);

            if (yaw == last && player.isSprinting()) {
                int b = buffer.getOrDefault(player.getUniqueId(), 0) + 1;
                buffer.put(player.getUniqueId(), b);
                if (b > 3) {
                    AlertUtil.sendAlert(player, "KillAura", b);
                    buffer.put(player.getUniqueId(), 0);
                }
            } else {
                buffer.put(player.getUniqueId(), Math.max(0, buffer.getOrDefault(player.getUniqueId(), 0) - 1));
            }
        });
    }
}