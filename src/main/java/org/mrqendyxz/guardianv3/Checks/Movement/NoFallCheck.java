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

public class NoFallCheck {

    private final Map<UUID, Float> lastFallDistance = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE || player.getAllowFlight() || player.getVehicle() != null) return;

        float fallDistance = player.getFallDistance();
        boolean clientGround = wrapper.isOnGround();
        UUID uuid = player.getUniqueId();

        if (clientGround && fallDistance > 3.0F) {
            if (isFallDampingBlock(player) || isSpecialGround(player)) {
                lastFallDistance.put(uuid, 0.0F);
                return;
            }

            double deltaY = wrapper.getLocation().getY() % 1.0;
            if (deltaY > 0.0 && deltaY != 0.5 && deltaY != 0.0625 && deltaY != 0.125 && deltaY < 0.9) {
                AlertUtil.sendAlert(player, "NoFall", (int) fallDistance);
            }
        }

        if (clientGround) {
            lastFallDistance.put(uuid, 0.0F);
        } else {
            lastFallDistance.put(uuid, fallDistance);
        }
    }

    private boolean isFallDampingBlock(Player p) {
        Material m = p.getLocation().getBlock().getType();
        Material below = p.getLocation().clone().subtract(0, 0.1, 0).getBlock().getType();

        return m == Material.POWDER_SNOW || below == Material.POWDER_SNOW ||
                m == Material.WATER || below == Material.WATER ||
                m == Material.COBWEB || below == Material.COBWEB ||
                m == Material.SLIME_BLOCK || below == Material.SLIME_BLOCK ||
                m == Material.HONEY_BLOCK || below == Material.HONEY_BLOCK ||
                m == Material.SCAFFOLDING || below == Material.SCAFFOLDING;
    }

    private boolean isSpecialGround(Player p) {
        for (double x = -0.3; x <= 0.3; x += 0.3) {
            for (double z = -0.3; z <= 0.3; z += 0.3) {
                Material m = p.getLocation().add(x, -0.1, z).getBlock().getType();
                String name = m.name();
                if (name.contains("SLAB") || name.contains("STAIRS") || name.contains("SNOW") || name.contains("STEP")) {
                    return true;
                }
            }
        }
        return false;
    }
}