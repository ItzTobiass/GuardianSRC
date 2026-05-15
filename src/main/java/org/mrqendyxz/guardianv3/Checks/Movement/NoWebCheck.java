package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NoWebCheck {
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player p = (Player) event.getPlayer();
        if (p == null) return;

        double deltaX = wrapper.getLocation().getX() - p.getLocation().getX();
        double deltaZ = wrapper.getLocation().getZ() - p.getLocation().getZ();
        double dist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (p.getLocation().getBlock().getType() == Material.COBWEB && dist > 0.12) {
            int b = buffer.getOrDefault(p.getUniqueId(), 0) + 1;
            buffer.put(p.getUniqueId(), b);
            if (b > 3) {
                AlertUtil.sendAlert(p, "NoWeb", b);
                buffer.put(p.getUniqueId(), 0);
            }
        } else {
            buffer.put(p.getUniqueId(), Math.max(0, buffer.getOrDefault(p.getUniqueId(), 0) - 1));
        }
    }
}