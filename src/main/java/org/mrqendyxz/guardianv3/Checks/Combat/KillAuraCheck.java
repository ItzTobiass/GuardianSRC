package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;
import org.mrqendyxz.guardianv3.Utils.TaskUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KillAuraCheck {

    private final Map<UUID, Float> lastYaw = new ConcurrentHashMap<>();
    private final Map<UUID, Float> lastPitch = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Float>> yawSamples = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferAngle = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferAimLock = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bufferMultiTarget = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastTarget = new ConcurrentHashMap<>();

    private static final double MAX_ANGLE_RAD = 0.45;

    public void handle(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            if (flying.hasRotationChanged()) {
                float yaw = flying.getLocation().getYaw();
                float pitch = flying.getLocation().getPitch();

                Deque<Float> samples = yawSamples.computeIfAbsent(uuid, k -> new ArrayDeque<>());
                if (lastYaw.containsKey(uuid)) {
                    samples.addLast(Math.abs(yaw - lastYaw.get(uuid)));
                    if (samples.size() > 20) samples.pollFirst();
                }

                lastYaw.put(uuid, yaw);
                lastPitch.put(uuid, pitch);
            }
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        int targetId = wrapper.getEntityId();
        float cachedYaw = lastYaw.getOrDefault(uuid, 0f);
        float cachedPitch = lastPitch.getOrDefault(uuid, 0f);
        Deque<Float> samples = yawSamples.getOrDefault(uuid, new ArrayDeque<>());

        TaskUtil.run(() -> {
            if (!player.isOnline()) return;

            Entity target = null;
            for (Entity e : player.getNearbyEntities(7, 7, 7)) {
                if (e.getEntityId() == targetId) {
                    target = e;
                    break;
                }
            }
            if (target == null) return;

            UUID targetUUID = target.getUniqueId();
            UUID prev = lastTarget.get(uuid);
            if (prev != null && !prev.equals(targetUUID)) {
                int b = bufferMultiTarget.getOrDefault(uuid, 0) + 1;
                bufferMultiTarget.put(uuid, b);
                if (b > 4) {
                    AlertUtil.sendAlert(player, "KillAura (MultiTarget)", b);
                    bufferMultiTarget.put(uuid, 0);
                }
            } else {
                bufferMultiTarget.put(uuid, Math.max(0, bufferMultiTarget.getOrDefault(uuid, 0) - 1));
            }
            lastTarget.put(uuid, targetUUID);

            Vector eye = player.getEyeLocation().toVector();
            Vector toTarget = target.getLocation().toVector().add(new Vector(0, 1.0, 0)).subtract(eye).normalize();
            double[] dir = yawPitchToDir(cachedYaw, cachedPitch);
            Vector dirVec = new Vector(dir[0], dir[1], dir[2]);
            double angle = dirVec.angle(toTarget);

            if (angle > MAX_ANGLE_RAD) {
                int b = bufferAngle.getOrDefault(uuid, 0) + 1;
                bufferAngle.put(uuid, b);
                if (b > 4) {
                    AlertUtil.sendAlert(player, "KillAura (Angle)", b);
                    bufferAngle.put(uuid, 0);
                }
            } else {
                bufferAngle.put(uuid, Math.max(0, bufferAngle.getOrDefault(uuid, 0) - 1));
            }

            if (samples.size() >= 10) {
                double avg = samples.stream().mapToDouble(f -> f).average().orElse(0);
                long constantCount = samples.stream().filter(f -> Math.abs(f - avg) < 0.0001).count();

                if (constantCount >= 8 && avg > 0.5) {
                    int b = bufferAimLock.getOrDefault(uuid, 0) + 1;
                    bufferAimLock.put(uuid, b);
                    if (b > 5) {
                        AlertUtil.sendAlert(player, "KillAura (AimLock)", b);
                        bufferAimLock.put(uuid, 0);
                    }
                } else {
                    bufferAimLock.put(uuid, Math.max(0, bufferAimLock.getOrDefault(uuid, 0) - 1));
                }
            }
        });
    }

    private double[] yawPitchToDir(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new double[]{x, y, z};
    }

    public void cleanup(UUID uuid) {
        lastYaw.remove(uuid);
        lastPitch.remove(uuid);
        yawSamples.remove(uuid);
        bufferAngle.remove(uuid);
        bufferAimLock.remove(uuid);
        bufferMultiTarget.remove(uuid);
        lastTarget.remove(uuid);
    }
}