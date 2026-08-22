package org.mrqendyxz.guardianv3.Checks.Combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.mrqendyxz.guardianv3.Utils.AlertUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CrystalAuraCheck {

    private static final int    WINDOW_MS           = 1000;
    private static final int    MAX_PLACE_PER_SEC   = 8;
    private static final int    MAX_ATTACK_PER_SEC  = 10;
    private static final double MAX_PLACE_REACH     = 5.5;
    private static final double MAX_ATTACK_REACH    = 6.0;
    private static final int    PLACE_BUFFER_MAX    = 5;
    private static final int    ATTACK_BUFFER_MAX   = 5;
    private static final int    SWITCH_THRESHOLD_MS = 180;

    private final Map<UUID, Deque<Long>> placeTimestamps  = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> attackTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>     placeBuffer      = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>     attackBuffer     = new ConcurrentHashMap<>();
    private final Map<UUID, Long>        lastPlaceTime    = new ConcurrentHashMap<>();
    private final Map<UUID, Long>        lastAttackTime   = new ConcurrentHashMap<>();
    private final Map<UUID, Location>    lastPlaceLocation = new ConcurrentHashMap<>();

    public void handle(PacketReceiveEvent event) {
        Player player = (Player) event.getPlayer();
        if (player == null) return;
        if (isExempt(player)) return;

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            handlePlace(event, player);
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            handleAttack(event, player);
        }
    }

    private void handlePlace(PacketReceiveEvent event, Player player) {
        WrapperPlayClientPlayerBlockPlacement wrapper = new WrapperPlayClientPlayerBlockPlacement(event);

        Material held = player.getInventory().getItemInMainHand().getType();
        if (held != Material.END_CRYSTAL) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Location placeTarget = player.getTargetBlockExact(6) != null
                ? player.getTargetBlockExact(6).getLocation()
                : null;

        if (placeTarget != null) {
            double reach = player.getEyeLocation().distance(placeTarget.clone().add(0.5, 0.5, 0.5));
            if (reach > MAX_PLACE_REACH) {
                int b = placeBuffer.getOrDefault(uuid, 0) + 2;
                placeBuffer.put(uuid, b);
                if (b >= PLACE_BUFFER_MAX) {
                    AlertUtil.sendAlert(player, "CrystalAura[Place-Reach]", b);
                    placeBuffer.put(uuid, b / 2);
                }
                return;
            }
            lastPlaceLocation.put(uuid, placeTarget);
        }

        Deque<Long> timestamps = placeTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        timestamps.addLast(now);
        pruneWindow(timestamps, now);

        if (timestamps.size() > MAX_PLACE_PER_SEC) {
            int b = placeBuffer.getOrDefault(uuid, 0) + 1;
            placeBuffer.put(uuid, b);
            if (b >= PLACE_BUFFER_MAX) {
                AlertUtil.sendAlert(player, "CrystalAura[Place-Speed]", b);
                placeBuffer.put(uuid, b / 2);
            }
        } else {
            decayBuffer(placeBuffer, uuid);
        }

        lastPlaceTime.put(uuid, now);
    }

    private void handleAttack(PacketReceiveEvent event, Player player) {
        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        int entityId = wrapper.getEntityId();
        Entity target = getEntityById(player, entityId);

        if (target == null || target.getType() != EntityType.END_CRYSTAL) return;

        double reach = player.getEyeLocation().distance(target.getLocation());
        if (reach > MAX_ATTACK_REACH) {
            int b = attackBuffer.getOrDefault(uuid, 0) + 2;
            attackBuffer.put(uuid, b);
            if (b >= ATTACK_BUFFER_MAX) {
                AlertUtil.sendAlert(player, "CrystalAura[Attack-Reach]", b);
                attackBuffer.put(uuid, b / 2);
            }
            return;
        }

        Deque<Long> timestamps = attackTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        timestamps.addLast(now);
        pruneWindow(timestamps, now);

        if (timestamps.size() > MAX_ATTACK_PER_SEC) {
            int b = attackBuffer.getOrDefault(uuid, 0) + 1;
            attackBuffer.put(uuid, b);
            if (b >= ATTACK_BUFFER_MAX) {
                AlertUtil.sendAlert(player, "CrystalAura[Attack-Speed]", b);
                attackBuffer.put(uuid, b / 2);
            }
        } else {
            decayBuffer(attackBuffer, uuid);
        }

        long lastPlace = lastPlaceTime.getOrDefault(uuid, 0L);
        if (lastPlace > 0 && (now - lastPlace) < SWITCH_THRESHOLD_MS) {
            int b = attackBuffer.getOrDefault(uuid, 0) + 1;
            attackBuffer.put(uuid, b);
            if (b >= ATTACK_BUFFER_MAX) {
                AlertUtil.sendAlert(player, "CrystalAura[Switch-Speed]", b);
                attackBuffer.put(uuid, b / 2);
            }
        }

        lastAttackTime.put(uuid, now);
    }

    private Entity getEntityById(Player player, int entityId) {
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity.getEntityId() == entityId) return entity;
        }
        return null;
    }

    private void pruneWindow(Deque<Long> deque, long now) {
        while (!deque.isEmpty() && now - deque.peekFirst() > WINDOW_MS) {
            deque.pollFirst();
        }
    }

    private void decayBuffer(Map<UUID, Integer> map, UUID uuid) {
        map.put(uuid, Math.max(0, map.getOrDefault(uuid, 0) - 1));
    }

    private boolean isExempt(Player p) {
        GameMode gm = p.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    public void cleanup(UUID uuid) {
        placeTimestamps.remove(uuid);
        attackTimestamps.remove(uuid);
        placeBuffer.remove(uuid);
        attackBuffer.remove(uuid);
        lastPlaceTime.remove(uuid);
        lastAttackTime.remove(uuid);
        lastPlaceLocation.remove(uuid);
    }
}