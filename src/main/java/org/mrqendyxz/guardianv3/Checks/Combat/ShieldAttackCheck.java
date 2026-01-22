package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShieldAttackCheck {

    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        Player player = (Player) event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE) return;

        if (player.isBlocking() || isUsingShield(player)) {
            UUID uuid = player.getUniqueId();
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);

            if (b > 2) {
                AlertUtil.sendAlert(player, "ShieldAttack", b);
                buffer.put(uuid, 0);
            }
        } else {
            buffer.put(player.getUniqueId(), 0);
        }
    }

    private boolean isUsingShield(Player p) {
        return (p.getInventory().getItemInMainHand().getType() == Material.SHIELD ||
                p.getInventory().getItemInOffHand().getType() == Material.SHIELD) &&
                p.isHandRaised();
    }
}