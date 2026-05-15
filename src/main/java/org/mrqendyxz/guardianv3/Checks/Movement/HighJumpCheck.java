package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import org.mrqendyxz.guardianv3.Utils.VelocityProtection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HighJumpCheck {
    private final Map<UUID, Double> lastY = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        Player player = (Player) event.getPlayer();
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        if (player.getGameMode() == GameMode.CREATIVE || player.getAllowFlight() || player.getVehicle() != null
                || player.isGliding() || VelocityProtection.shouldBypass(player)) return;

        double currentY = wrapper.getLocation().getY();
        double prevY = lastY.getOrDefault(uuid, currentY);
        double deltaY = currentY - prevY;
        lastY.put(uuid, currentY);

        if (wrapper.isOnGround() || deltaY <= 0) {
            buffer.put(uuid, Math.max(0, buffer.getOrDefault(uuid, 0) - 1));
            return;
        }

        double maxJump = 0.421;
        PotionEffect jumpEffect = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jumpEffect != null) {
            maxJump += (Math.pow(jumpEffect.getAmplifier() + 1, 2) * 0.1);
        }

        if (deltaY > maxJump + 0.01) {
            int b = buffer.getOrDefault(uuid, 0) + 1;
            buffer.put(uuid, b);
            if (b > 3) {
                AlertUtil.sendAlert(player, "HighJump", b);
                buffer.put(uuid, 0);
            }
        }
    }
}