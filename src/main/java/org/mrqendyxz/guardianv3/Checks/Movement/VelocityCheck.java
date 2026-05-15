package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityCheck {
    private final Map<UUID, Double> pendingVelocity = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> flyingPackets = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handleSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity wrapper = new WrapperPlayServerEntityVelocity(event);
            if (event.getPlayer() instanceof Player) {
                Player p = (Player) event.getPlayer();
                if (wrapper.getEntityId() == p.getEntityId()) {
                    double velY = wrapper.getVelocity().y;
                    if (velY > 0.0) {
                        pendingVelocity.put(p.getUniqueId(), velY);
                        flyingPackets.put(p.getUniqueId(), 0);
                    }
                }
            }
        }
    }

    public void handleReceive(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        Player p = (Player) event.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!pendingVelocity.containsKey(uuid)) return;

        int packets = flyingPackets.getOrDefault(uuid, 0) + 1;
        flyingPackets.put(uuid, packets);

        double deltaY = p.getVelocity().getY();

        if (deltaY > 0.001) {
            pendingVelocity.remove(uuid);
            flyingPackets.remove(uuid);
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            return;
        }

        if (packets > 20) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);
            if (b > 1) {
                AlertUtil.sendAlert(p, "Velocity (0%)", b);
                buffer.put(uuid, 0);
            }
            pendingVelocity.remove(uuid);
            flyingPackets.remove(uuid);
        }
    }

    public void handle(PacketReceiveEvent event) {
    }
}