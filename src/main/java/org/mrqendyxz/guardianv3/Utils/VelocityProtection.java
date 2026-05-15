package org.mrqendyxz.guardianv3.Utils;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityProtection implements PacketListener {

    private static final Map<UUID, Long> lastVelocity = new ConcurrentHashMap<>();

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity wrapper = new WrapperPlayServerEntityVelocity(event);
            if (wrapper.getEntityId() == event.getUser().getEntityId()) {
                lastVelocity.put(event.getUser().getUUID(), System.currentTimeMillis());
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {}

    public static boolean shouldBypass(Player player) {
        return System.currentTimeMillis() - lastVelocity.getOrDefault(player.getUniqueId(), 0L) < 3000;
    }
}