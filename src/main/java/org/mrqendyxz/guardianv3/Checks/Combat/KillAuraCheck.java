package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KillAuraCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private static final class State {
        final MovementEvidence aim = new MovementEvidence();
        final MovementEvidence snap = new MovementEvidence();
        final MovementEvidence multi = new MovementEvidence();
        Integer tickTarget;
        float lastYaw, lastPitch;
        float prevSignedYaw, prevSignedPitch;
        boolean hasRotation;
        List<Vector> pendingEyes;
        List<BoundingBox> pendingBoxes;
        float preAttackYaw, preAttackPitch;
        float postAttackYaw, postAttackPitch;
        boolean preHit;
        int postAttackStep;
    }
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        State state = states.computeIfAbsent(id, k -> new State());

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            if (!PacketContext.duplicatePosition(player)) {
                state.tickTarget = null;
            }
            var loc = PacketContext.location(player);
            float yaw = loc.getYaw(), pitch = loc.getPitch();
            float signedYaw = state.hasRotation ? RotationMath.deltaDegrees(yaw, state.lastYaw) : 0;
            float signedPitch = state.hasRotation ? pitch - state.lastPitch : 0;
            state.lastYaw = yaw;
            state.lastPitch = pitch;
            state.hasRotation = true;

            if (state.postAttackStep == 1) {
                state.postAttackYaw = signedYaw;
                state.postAttackPitch = signedPitch;
                Vector postLook = loc.getDirection();
                boolean postHit = hitsAny(state.pendingEyes, postLook, state.pendingBoxes)
                        || hitsAny(CombatHistory.eyeVectors(player), postLook, state.pendingBoxes);
                boolean anyHit = state.preHit || postHit;
                boolean severeMiss = !anyHit && behindAll(PacketContext.eye(player).toVector(), postLook, state.pendingBoxes);
                if (state.aim.record(!anyHit, severeMiss, 3)) {
                    AlertUtil.sendAlert(player, "KillAura (Aim)", state.aim.buffer());
                    state.aim.reset();
                }
                state.postAttackStep = 2;
            } else if (state.postAttackStep == 2) {
                float preMag = (float) Math.hypot(state.preAttackYaw, state.preAttackPitch);
                float postMag = (float) Math.hypot(state.postAttackYaw, state.postAttackPitch);
                float outMag = (float) Math.hypot(signedYaw, signedPitch);

                boolean postThenOutReversal = postMag >= 45f && outMag >= 35f
                        && (state.postAttackYaw * signedYaw + state.postAttackPitch * signedPitch) < -.25f * postMag * outMag;
                boolean preThenPostReversal = preMag >= 45f && postMag >= 35f
                        && (state.preAttackYaw * state.postAttackYaw + state.preAttackPitch * state.postAttackPitch) < -.25f * preMag * postMag;
                boolean roboticOneTickSnap = (Math.abs(state.postAttackYaw) >= 75f && preMag < 2f && outMag < 2f && Math.abs(state.postAttackPitch) < .05f)
                        || (Math.abs(state.preAttackYaw) >= 75f && postMag < 2f && outMag < 2f && Math.abs(state.preAttackPitch) < .05f);

                boolean badSnap = postThenOutReversal || preThenPostReversal || roboticOneTickSnap;
                float maxIn = Math.max(preMag, postMag);
                float maxOut = Math.max(postMag, outMag);
                boolean severeSnap = badSnap && maxIn >= 85f && maxOut >= 55f;

                if (state.snap.record(badSnap, severeSnap, 2)) {
                    AlertUtil.sendAlert(player, "KillAura (Snap)", state.snap.buffer());
                    state.snap.reset();
                }
                state.postAttackStep = 0;
            }

            state.prevSignedYaw = signedYaw;
            state.prevSignedPitch = signedPitch;
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        var packet = new WrapperPlayClientInteractEntity(event);
        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
        if (CombatHistory.uncertain(player)) { cleanup(id); return; }

        Integer prevTarget = state.tickTarget;
        state.tickTarget = packet.getEntityId();
        if (prevTarget != null && prevTarget != packet.getEntityId()) {
            if (state.multi.record(true, true, 1)) {
                AlertUtil.sendAlert(player, "KillAura (Multi)", state.multi.buffer());
                state.multi.reset();
            }
        }

        var target = CombatHistory.target(player, packet.getEntityId());
        if (target == null || target.getWorld() != player.getWorld()) { cleanup(id); return; }
        var loc = PacketContext.location(player);
        var eyes = CombatHistory.eyeVectors(player);
        var boxes = CombatHistory.boxes(player, target);
        var look = loc.getDirection();

        state.pendingEyes = eyes;
        state.pendingBoxes = boxes;
        state.preAttackYaw = state.prevSignedYaw;
        state.preAttackPitch = state.prevSignedPitch;
        state.preHit = hitsAny(eyes, look, boxes);
        state.postAttackStep = 1;
    }

    private boolean hitsAny(List<Vector> eyes, Vector direction, List<BoundingBox> boxes) {
        if (eyes == null || boxes == null) return true;
        for (BoundingBox raw : boxes) {
            BoundingBox box = raw.clone().expand(.38);
            for (Vector eye : eyes) {
                if (box.contains(eye) || box.rayTrace(eye, direction, 6.0) != null) return true;
            }
        }
        return false;
    }

    private boolean behindAll(Vector eye, Vector look, List<BoundingBox> boxes) {
        if (boxes == null || boxes.isEmpty()) return false;
        return boxes.stream().allMatch(box -> {
            if (CombatHistory.distance(eye, box) < 1.2) return false;
            var direction = box.getCenter().subtract(eye);
            return direction.lengthSquared() > 0 && direction.normalize().dot(look) < -.25;
        });
    }

    public void cleanup(UUID uuid) {
        states.remove(uuid);
    }
}
