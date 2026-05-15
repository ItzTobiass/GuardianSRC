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

    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();

        if (player == null || player.getGameMode() == GameMode.CREATIVE || player.getAllowFlight() || player.getVehicle() != null) return;

        float fallDistance = player.getFallDistance();
        boolean clientGround = wrapper.isOnGround();
        UUID uuid = player.getUniqueId();

        if (clientGround && fallDistance > 3.2F) {
            if (isFallDampingBlock(player) || isSpecialGround(player)) {
                buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
                return;
            }

            double deltaY = Math.abs(wrapper.getLocation().getY() % 1.0);

            if (deltaY > 0.0001 && deltaY < 0.999) {
                if (deltaY == 0.5 || deltaY == 0.0625 || deltaY == 0.015625 || deltaY == 0.125 || deltaY == 0.1875 || deltaY == 0.03125) {
                    buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
                    return;
                }

                int b = buffer.getOrDefault(uuid, 0) + 1;
                buffer.put(uuid, b);

                if (b > 3) {
                    AlertUtil.sendAlert(player, "NoFall", (int) fallDistance);
                    buffer.put(uuid, 0);
                }
            }
        } else if (clientGround) {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
        }
    }

    private boolean isFallDampingBlock(Player p) {
        Material m = p.getLocation().getBlock().getType();
        Material below = p.getLocation().clone().subtract(0, 0.5, 0).getBlock().getType();
        Material feet = p.getEyeLocation().clone().subtract(0, 1.5, 0).getBlock().getType();

        if (m == Material.LILY_PAD || below == Material.LILY_PAD || feet == Material.LILY_PAD) return true;

        return m == Material.WATER || below == Material.WATER || feet == Material.WATER ||
                m == Material.POWDER_SNOW || below == Material.POWDER_SNOW ||
                m == Material.COBWEB || below == Material.COBWEB ||
                m == Material.SLIME_BLOCK || below == Material.SLIME_BLOCK ||
                m == Material.HONEY_BLOCK || below == Material.HONEY_BLOCK ||
                m == Material.SCAFFOLDING || below == Material.SCAFFOLDING ||
                m == Material.LADDER || below == Material.LADDER ||
                m == Material.VINE || m.name().contains("VINE") ||
                m == Material.SWEET_BERRY_BUSH;
    }

    private boolean isSpecialGround(Player p) {
        for (double x = -0.4; x <= 0.4; x += 0.2) {
            for (double z = -0.4; z <= 0.4; z += 0.2) {
                Material m = p.getLocation().clone().add(x, -0.2, z).getBlock().getType();
                Material mAt = p.getLocation().clone().add(x, 0, z).getBlock().getType();

                if (m == Material.LILY_PAD || mAt == Material.LILY_PAD) return true;

                String name = m.name();
                if (name.contains("SLAB") || name.contains("STAIRS") || name.contains("SNOW") ||
                        name.contains("STEP") || name.contains("CARPET") || name.contains("BED") ||
                        m == Material.FARMLAND || m == Material.DIRT_PATH || m == Material.WATER) {
                    return true;
                }
            }
        }
        return false;
    }
}