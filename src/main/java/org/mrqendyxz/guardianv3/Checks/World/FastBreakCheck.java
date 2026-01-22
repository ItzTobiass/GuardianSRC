package org.mrqendyxz.guardianv3.Checks.World;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FastBreakCheck {
    private final Map<UUID, Long> breakStart = new HashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
            Player player = (Player) event.getPlayer();
            UUID uuid = player.getUniqueId();

            DiggingAction action = wrapper.getAction();

            if (action == DiggingAction.START_DIGGING) {
                breakStart.put(uuid, System.currentTimeMillis());
            }
            else if (action == DiggingAction.FINISHED_DIGGING) {
                long start = breakStart.getOrDefault(uuid, 0L);
                if (start == 0) return;

                long duration = System.currentTimeMillis() - start;

                if (duration < 40) {
                    AlertUtil.sendAlert(player, "FastBreak", 1);
                }
                breakStart.put(uuid, 0L);
            }
        }
    }
}