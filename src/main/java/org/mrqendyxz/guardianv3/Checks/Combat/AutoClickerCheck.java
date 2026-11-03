package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoClickerCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private final Map<UUID, SustainedRate> rates = new ConcurrentHashMap<>();
    public void handle(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        var attack = new WrapperPlayClientInteractEntity(event);
        if (attack.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
        Player player = event.getPlayer();
        if (CombatHistory.uncertain(player)) { cleanup(player.getUniqueId()); return; }
        int max = Math.max(1, org.mrqendyxz.guardianv3.Guardianv3.getInstance().getConfig().getInt("checks.autoclicker.max-cps", 40));
        if (rates.computeIfAbsent(player.getUniqueId(), id -> new SustainedRate()).record(PacketContext.now(), max, 3)) {
            AlertUtil.sendAlert(player, "AutoClicker", 3);
        }
    }
    public void cleanup(UUID uuid) { rates.remove(uuid); }
}
