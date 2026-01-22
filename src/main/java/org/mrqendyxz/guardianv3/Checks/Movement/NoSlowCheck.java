package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NoSlowCheck {

    private final Map<UUID, Integer> buffer = new HashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.isOp() || player.getAllowFlight()) return;

        // Pokud hráč používá item (jí, pije, blokuje)
        if (player.isHandRaised()) {
            // A zároveň v paketu tvrdí, že sprintuje nebo se hýbe příliš rychle
            if (player.isSprinting() || player.getWalkSpeed() > 0.22) {
                int b = buffer.getOrDefault(uuid, 0) + 1;
                buffer.put(uuid, b);

                if (b > 5) {
                    AlertUtil.sendAlert(player, "NoSlow", b);
                }
            }
        } else {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
        }
    }
}