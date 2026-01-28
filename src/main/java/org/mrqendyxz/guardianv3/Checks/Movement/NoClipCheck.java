package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NoClipCheck {
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastJoin = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();

        if (player == null || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - lastJoin.getOrDefault(uuid, now) < 5000) {
            lastJoin.putIfAbsent(uuid, now);
            return;
        }

        Location to = new Location(player.getWorld(), wrapper.getLocation().getX(), wrapper.getLocation().getY(), wrapper.getLocation().getZ());

        if (isInsideSolid(to.clone().add(0, 0.5, 0)) && isInsideSolid(to.clone().add(0, 1.5, 0))) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);
            if (b > 4) {
                AlertUtil.sendAlert(player, "NoClip", b);
                buffer.put(uuid, 0);
            }
        } else {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
        }
    }

    private boolean isInsideSolid(Location loc) {
        Block block = loc.getBlock();
        Material m = block.getType();
        if (!m.isSolid() || !m.isOccluding()) return false;
        String name = m.name();
        return !name.contains("SLAB") && !name.contains("STAIRS") && !name.contains("FENCE") &&
                !name.contains("TRAPDOOR") && !name.contains("WALL") && !name.contains("DOOR");
    }
}