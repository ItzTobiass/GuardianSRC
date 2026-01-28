package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JesusCheck {

    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE || player.getAllowFlight() || player.getVehicle() != null) return;

        double y = wrapper.getLocation().getY();
        UUID uuid = player.getUniqueId();

        if (y % 1.0 == 0.015625 || y % 1.0 == 0.5 || y % 1.0 == 0.0) {
            Block block = player.getLocation().getBlock();
            Block below = player.getLocation().clone().subtract(0, 0.1, 0).getBlock();

            if (isLiquid(below) && !isLiquid(block) && !isNearSolid(player)) {
                int b = buffer.getOrDefault(uuid, 0) + 1;
                buffer.put(uuid, b);

                if (b > 3) {
                    AlertUtil.sendAlert(player, "Jesus", b);
                    buffer.put(uuid, 0);
                }
            } else {
                buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            }
        }
    }

    private boolean isLiquid(Block b) {
        return b.getType() == Material.WATER || b.getType() == Material.LAVA;
    }

    private boolean isNearSolid(Player p) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block b = p.getLocation().add(x, 0, z).getBlock();
                Block b2 = p.getLocation().add(x, -0.7, z).getBlock();
                if (b.getType().isSolid() || b2.getType().isSolid()) return true;
            }
        }
        return false;
    }
}