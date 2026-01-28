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

public class WallClimbCheck {
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();

        if (player == null || player.getGameMode() == GameMode.CREATIVE || wrapper.isOnGround()) {
            buffer.put(player.getUniqueId(), 0);
            return;
        }

        if (player.getLocation().getBlock().isLiquid() || player.getEyeLocation().getBlock().isLiquid()) {
            buffer.put(player.getUniqueId(), 0);
            return;
        }

        double deltaY = wrapper.getLocation().getY() - player.getLocation().getY();

        if (deltaY > 0.0 && deltaY < 0.4 && !isClimbable(player) && isNearWall(player)) {
            UUID uuid = player.getUniqueId();
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);
            if (b > 8) {
                AlertUtil.sendAlert(player, "WallClimb", b);
                buffer.put(uuid, 0);
            }
        } else {
            buffer.put(player.getUniqueId(), Math.max(0, buffer.getOrDefault(player.getUniqueId(), 0) - 1));
        }
    }

    private boolean isClimbable(Player p) {
        Material m = p.getLocation().getBlock().getType();
        return m == Material.LADDER || m == Material.VINE || m == Material.SCAFFOLDING || m == Material.TWISTING_VINES || m == Material.WEEPING_VINES;
    }

    private boolean isNearWall(Player p) {
        for (double x = -0.3; x <= 0.3; x += 0.3) {
            for (double z = -0.3; z <= 0.3; z += 0.3) {
                if (p.getLocation().add(x, 0.5, z).getBlock().getType().isSolid()) return true;
            }
        }
        return false;
    }
}