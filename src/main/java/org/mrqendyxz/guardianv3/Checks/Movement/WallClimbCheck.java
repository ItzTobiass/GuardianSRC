package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WallClimbCheck {

    private static final double PLAYER_WIDTH  = 0.3;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final double DELTA_MIN     = 0.005;
    private static final double DELTA_MAX     = 0.42;
    private static final int    AIR_TICK_GRACE = 2;
    private static final int    BUFFER_MAX     = 6;

    private final Map<UUID, Double>  lastY     = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> buffer    = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> airTicks  = new ConcurrentHashMap<>();
    private final Map<UUID, Double>  lastDeltaY = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        Player player = (Player) event.getPlayer();
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        double currentY = wrapper.getLocation().getY();

        if (isExempt(player)) {
            reset(uuid, currentY);
            return;
        }

        if (isClimbable(player) || isInLiquid(player)) {
            reset(uuid, currentY);
            return;
        }

        double prevY   = lastY.getOrDefault(uuid, currentY);
        double deltaY  = currentY - prevY;
        double prevDeltaY = lastDeltaY.getOrDefault(uuid, 0.0);
        lastY.put(uuid, currentY);
        lastDeltaY.put(uuid, deltaY);

        if (wrapper.isOnGround() || deltaY <= 0) {
            airTicks.put(uuid, 0);
            decayBuffer(uuid);
            return;
        }

        int ticks = airTicks.getOrDefault(uuid, 0) + 1;
        airTicks.put(uuid, ticks);

        if (ticks <= AIR_TICK_GRACE) return;

        if (isVelocitySpike(deltaY, prevDeltaY)) {
            decayBuffer(uuid);
            return;
        }

        if (!isNearWall(player)) {
            decayBuffer(uuid);
            return;
        }

        if (deltaY > DELTA_MIN && deltaY < DELTA_MAX) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);
            if (b >= BUFFER_MAX) {
                AlertUtil.sendAlert(player, "WallClimb", b);
                buffer.put(uuid, b / 2);
            }
        } else {
            decayBuffer(uuid);
        }
    }

    private boolean isExempt(Player p) {
        GameMode gm = p.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR
                || p.getAllowFlight() || p.isFlying()
                || p.isGliding();
    }

    private boolean isVelocitySpike(double deltaY, double prevDeltaY) {
        return deltaY > DELTA_MAX || (prevDeltaY <= 0 && deltaY > 0.35);
    }

    private boolean isClimbable(Player p) {
        Block block = p.getLocation().getBlock();
        return Tag.CLIMBABLE.isTagged(block.getType());
    }

    private boolean isInLiquid(Player p) {
        Material feet = p.getLocation().getBlock().getType();
        Material eye  = p.getEyeLocation().getBlock().getType();
        return feet == Material.WATER || feet == Material.LAVA
                || eye  == Material.WATER || eye  == Material.LAVA;
    }

    private boolean isNearWall(Player p) {
        double bx = p.getLocation().getX();
        double by = p.getLocation().getY();
        double bz = p.getLocation().getZ();

        double[] offsets = { -(PLAYER_WIDTH + 0.05), 0, PLAYER_WIDTH + 0.05 };

        for (double ox : offsets) {
            for (double oz : offsets) {
                if (ox == 0 && oz == 0) continue;

                boolean isPerimeter = (ox != 0 || oz != 0);
                if (!isPerimeter) continue;

                for (double oy = 0.1; oy <= PLAYER_HEIGHT - 0.1; oy += 0.5) {
                    Block b = p.getWorld().getBlockAt(
                            (int) Math.floor(bx + ox),
                            (int) Math.floor(by + oy),
                            (int) Math.floor(bz + oz)
                    );
                    if (b.getType().isSolid() && !Tag.CLIMBABLE.isTagged(b.getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void decayBuffer(UUID uuid) {
        buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
    }

    private void reset(UUID uuid, double y) {
        lastY.put(uuid, y);
        lastDeltaY.put(uuid, 0.0);
        buffer.put(uuid, 0);
        airTicks.put(uuid, 0);
    }

    public void cleanup(UUID uuid) {
        lastY.remove(uuid);
        lastDeltaY.remove(uuid);
        buffer.remove(uuid);
        airTicks.remove(uuid);
    }
}