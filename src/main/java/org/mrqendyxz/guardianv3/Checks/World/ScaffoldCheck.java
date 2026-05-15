package org.mrqendyxz.guardianv3.Checks.World;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScaffoldCheck {

    private final Map<UUID, Long> lastPlace = new ConcurrentHashMap<>();
    private final Map<UUID, Float> lastYaw = new ConcurrentHashMap<>();
    private final Map<UUID, Float> lastPitch = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Float>> pitchSamples = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Float>> yawDeltaSamples = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferAimLock = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferNoItem = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        Player player = (Player) event.getPlayer();
        if (player == null) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        UUID uuid = player.getUniqueId();

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement wrapper = new WrapperPlayClientPlayerBlockPlacement(event);

            boolean hasBlock = player.getInventory().getItemInMainHand().getType().isBlock()
                    || player.getInventory().getItemInOffHand().getType().isBlock();

            long now = System.currentTimeMillis();
            lastPlace.put(uuid, now);

            if (!hasBlock) {
                int b = bufferNoItem.getOrDefault(uuid, 0) + 1;
                bufferNoItem.put(uuid, b);
                if (b >= 3) {
                    AlertUtil.sendAlert(player, "Scaffold (NoItem)", b);
                    bufferNoItem.put(uuid, 0);
                }
            } else {
                bufferNoItem.put(uuid, Math.max(0, bufferNoItem.getOrDefault(uuid, 0) - 1));
            }
        }

        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
        if (!flying.hasRotationChanged()) return;

        float yaw = flying.getLocation().getYaw();
        float pitch = flying.getLocation().getPitch();
        float prevYaw = lastYaw.getOrDefault(uuid, yaw);
        float prevPitch = lastPitch.getOrDefault(uuid, pitch);

        float deltaYaw = Math.abs(yaw - prevYaw);
        float deltaPitch = Math.abs(pitch - prevPitch);

        lastYaw.put(uuid, yaw);
        lastPitch.put(uuid, pitch);

        Deque<Float> pitches = pitchSamples.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        Deque<Float> yawDeltas = yawDeltaSamples.computeIfAbsent(uuid, k -> new ArrayDeque<>());

        pitches.addLast(pitch);
        yawDeltas.addLast(deltaYaw);
        if (pitches.size() > 15) pitches.pollFirst();
        if (yawDeltas.size() > 15) yawDeltas.pollFirst();

        long timeSincePlace = System.currentTimeMillis() - lastPlace.getOrDefault(uuid, 0L);
        if (timeSincePlace > 300) {
            bufferAimLock.put(uuid, Math.max(0, bufferAimLock.getOrDefault(uuid, 0) - 1));
            return;
        }

        if (pitches.size() < 10) return;

        double avgPitch = pitches.stream().mapToDouble(f -> f).average().orElse(0);
        long downwardCount = pitches.stream().filter(p2 -> p2 > 70).count();
        double avgYawDelta = yawDeltas.stream().mapToDouble(f -> f).average().orElse(0);
        long constantYawCount = yawDeltas.stream().filter(d -> Math.abs(d - avgYawDelta) < 0.001).count();

        boolean lookingDown = avgPitch > 70 && downwardCount >= 7;
        boolean aimLocked = constantYawCount >= 8 && deltaPitch < 0.5;

        if (lookingDown && aimLocked && player.isSprinting()) {
            int b = bufferAimLock.getOrDefault(uuid, 0) + 1;
            bufferAimLock.put(uuid, b);
            if (b >= 5) {
                AlertUtil.sendAlert(player, "Scaffold (AimLock)", b);
                bufferAimLock.put(uuid, 0);
            }
        } else {
            bufferAimLock.put(uuid, Math.max(0, bufferAimLock.getOrDefault(uuid, 0) - 1));
        }
    }

    public void cleanup(UUID uuid) {
        lastPlace.remove(uuid);
        lastYaw.remove(uuid);
        lastPitch.remove(uuid);
        pitchSamples.remove(uuid);
        yawDeltaSamples.remove(uuid);
        bufferAimLock.remove(uuid);
        bufferNoItem.remove(uuid);
    }
}