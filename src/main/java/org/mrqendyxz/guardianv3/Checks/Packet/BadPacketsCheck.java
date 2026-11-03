package org.mrqendyxz.guardianv3.Checks.Packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Checks.Check;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import org.mrqendyxz.guardianv3.Utils.BadPacketRules;
import org.mrqendyxz.guardianv3.Utils.SetbackUtil;

public final class BadPacketsCheck implements Check {
    @Override public boolean runsBeforeContext() { return true; }

    @Override public void handle(PacketReceiveEvent event) {
        String reason = violation(event);
        if (reason == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        SetbackUtil.reject(player);
        AlertUtil.sendAlert(player, "BadPackets (" + reason + ")", 1);
    }

    public static String violation(PacketReceiveEvent event) {
        var type = event.getPacketType();
        if (WrapperPlayClientPlayerFlying.isFlying(type)) {
            var packet = new WrapperPlayClientPlayerFlying(event);
            var at = packet.getLocation();
            if (packet.hasPositionChanged()) {
                String reason = BadPacketRules.position(at.getX(), at.getY(), at.getZ());
                if (reason != null) return reason;
            }
            if (packet.hasRotationChanged()) return BadPacketRules.rotation(at.getYaw(), at.getPitch(), true);
        } else if (type == PacketType.Play.Client.VEHICLE_MOVE) {
            var packet = new WrapperPlayClientVehicleMove(event);
            var at = packet.getPosition();
            String reason = BadPacketRules.position(at.x, at.y, at.z);
            return reason != null ? reason : BadPacketRules.rotation(packet.getYaw(), packet.getPitch(), false);
        } else if (type == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            return BadPacketRules.hotbar(new WrapperPlayClientHeldItemChange(event).getSlot());
        } else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            var packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                return BadPacketRules.attack(packet.getEntityId(), event.getUser().getEntityId());
            }
        }
        return null;
    }
}
