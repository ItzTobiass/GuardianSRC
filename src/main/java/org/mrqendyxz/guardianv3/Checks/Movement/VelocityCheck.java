package org.mrqendyxz.guardianv3.Checks.Movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityCheck implements org.mrqendyxz.guardianv3.Checks.Check {
    private static final class Pending {
        double vertical; long due, expires; int samples;
    }
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, MovementEvidence> evidence = new ConcurrentHashMap<>();
    public void handleSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_VELOCITY) return;
        var packet = new WrapperPlayServerEntityVelocity(event);
        Player p = event.getPlayer();
        if (p == null) return;
        if (packet.getEntityId() != p.getEntityId() && packet.getEntityId() != event.getUser().getEntityId()) return;
        pending.remove(p.getUniqueId());
        if (packet.getVelocity().y < .08 || p.getPing() > 500) return;
        Pending state = new Pending(); state.vertical = packet.getVelocity().y;
        state.due = PacketContext.now() + Math.max(100, p.getPing() + 50L);
        state.expires = state.due + 600; pending.put(p.getUniqueId(), state);
    }
    public void handle(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        Player p = event.getPlayer(); UUID id = p.getUniqueId();
        if (PacketContext.duplicatePosition(p)) return;
        Pending state = pending.get(id); if (state == null) return;
        var at = PacketContext.location(p);
        if (MovementGuards.ordinary(p) || !MovementEnvironment.clear(at.clone().add(0, Math.min(.6, state.vertical), 0), p.getHeight())
                || MovementGuards.attribute(p, org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE, 0) >= .99) { cleanup(id); return; }
        double requiredVertical = Math.min(.1, state.vertical * .45);
        if (PacketContext.deltaY(p) >= requiredVertical) { pending.remove(id); evidence.remove(id); return; }
        if (PacketContext.now() < state.due) return;
        state.samples++;
        if (PacketContext.now() < state.expires) return;
        pending.remove(id);
        if (state.samples < 5) return;
        var e = evidence.computeIfAbsent(id, key -> new MovementEvidence());
        if (e.record(true, false, 2)) {
            int buf = e.buffer();
            e.reset();
            SetbackUtil.flag(p, "Velocity (NoResponse)", buf);
        }
    }
    public void cleanup(UUID uuid) { pending.remove(uuid); evidence.remove(uuid); }
}
