package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlyCheck {

    private final Map<UUID, Double> lastY = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferA = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferB = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferC = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.getGameMode() == GameMode.CREATIVE || player.getAllowFlight()
                || player.isGliding() || player.getVehicle() != null || isInWeb(player)) {
            lastY.put(uuid, wrapper.getLocation().getY());
            return;
        }

        double currentY = wrapper.getLocation().getY();
        double prevY = lastY.getOrDefault(uuid, currentY);
        double deltaY = currentY - prevY;
        lastY.put(uuid, currentY);

        if (wrapper.isOnGround() || isInLiquid(player) || isOnClimbable(player)) {
            bufferA.put(uuid, 0);
            bufferB.put(uuid, 0);
            bufferC.put(uuid, 0);
            return;
        }

        if (Math.abs(deltaY) < 0.001) {
            int b = bufferA.getOrDefault(uuid, 0) + 1;
            bufferA.put(uuid, b);
            if (b > 6) AlertUtil.sendAlert(player, "Fly (A)", b);
        } else {
            bufferA.put(uuid, Math.max(0, bufferA.getOrDefault(uuid, 0) - 1));
        }

        if (deltaY > 0 && player.getVelocity().getY() <= 0 && deltaY > 0.1) {
            int b = bufferB.getOrDefault(uuid, 0) + 1;
            bufferB.put(uuid, b);
            if (b > 3) AlertUtil.sendAlert(player, "Fly (B)", b);
        } else {
            bufferB.put(uuid, 0);
        }

        if (deltaY < 0 && deltaY > -0.05 && !player.getLocation().getBlock().getType().isSolid()) {
            int b = bufferC.getOrDefault(uuid, 0) + 1;
            bufferC.put(uuid, b);
            if (b > 5) AlertUtil.sendAlert(player, "Fly (C)", b);
        } else {
            bufferC.put(uuid, 0);
        }
    }

    private boolean isInLiquid(Player p) {
        Material m = p.getLocation().getBlock().getType();
        return m == Material.WATER || m == Material.LAVA;
    }

    private boolean isOnClimbable(Player p) {
        Material m = p.getLocation().getBlock().getType();
        return m == Material.LADDER || m == Material.VINE || m == Material.SCAFFOLDING;
    }

    private boolean isInWeb(Player p) {
        return p.getLocation().getBlock().getType() == Material.COBWEB;
    }
}