package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import org.mrqendyxz.guardianv3.Utils.TaskUtil;

public class ShieldAttackCheck {
    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        Player player = (Player) event.getPlayer();
        if (player == null) return;

        TaskUtil.run(() -> {
            if (player.isBlocking() && player.getAttackCooldown() < 0.5) {
                AlertUtil.sendAlert(player, "ShieldAttack", 1);
            }
        });
    }
}