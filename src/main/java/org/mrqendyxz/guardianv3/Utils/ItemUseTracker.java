package org.mrqendyxz.guardianv3.Utils;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ItemUseTracker {
    private static final class State {
        Material item;
        long started = Long.MIN_VALUE;
        int serverHandTicks;
        int useId;
        long lastCancelMillis = Long.MIN_VALUE;
        long grimBypassUntil = Long.MIN_VALUE;
        final ArrayDeque<Long> rapidCycles = new ArrayDeque<>();
    }
    private static final Map<UUID, State> states = new HashMap<>();
    private static final Map<UUID, Long> rockets = new HashMap<>();

    public static void handle(PacketReceiveEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        long now = PacketContext.now();
        State state = states.computeIfAbsent(id, k -> new State());

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            if (!PacketContext.duplicatePosition(p)) {
                if (p.isHandRaised() || p.isBlocking()) {
                    if (state.serverHandTicks == 0 && state.started == Long.MIN_VALUE) {
                        Material active = resolveUsableItem(p, InteractionHand.MAIN_HAND);
                        if (active != null) {
                            state.item = active;
                            state.started = now;
                            state.useId++;
                        }
                    }
                    state.serverHandTicks++;
                } else {
                    if (state.serverHandTicks > 0) {
                        state.serverHandTicks = 0;
                        state.started = Long.MIN_VALUE;
                        state.item = null;
                    } else if (state.started != Long.MIN_VALUE && now - state.started > 250) {
                        state.started = Long.MIN_VALUE;
                        state.item = null;
                    }
                }
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            var packet = new WrapperPlayClientUseItem(event);
            var direct = packet.getHand() == InteractionHand.MAIN_HAND
                    ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand();
            if (direct.getType() == Material.FIREWORK_ROCKET) {
                rockets.put(id, now + 5000);
                return;
            }
            if (isWindCharge(direct.getType())
                    || isWindCharge(p.getInventory().getItemInMainHand().getType())
                    || isWindCharge(p.getInventory().getItemInOffHand().getType())) {
                VelocityProtection.recordWindCharge(p);
                return;
            }
            Material usable = resolveUsableItem(p, packet.getHand());
            if (usable == null || !canUseSlowItem(p, usable)) return;
            boolean rapidCancelCycle = state.lastCancelMillis != Long.MIN_VALUE && now - state.lastCancelMillis <= 120;
            if (rapidCancelCycle) {
                while (!state.rapidCycles.isEmpty() && now - state.rapidCycles.peekFirst() > 800) {
                    state.rapidCycles.removeFirst();
                }
                state.rapidCycles.addLast(now);
                if (state.rapidCycles.size() >= 3) {
                    state.grimBypassUntil = now + 500;
                }
            } else {
                state.useId++;
                state.serverHandTicks = 0;
            }
            state.item = usable;
            state.started = now;
        } else if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            if (state.started != Long.MIN_VALUE) {
                if (now - state.started <= 200) {
                    state.lastCancelMillis = now;
                }
                state.started = Long.MIN_VALUE;
                state.item = null;
                state.serverHandTicks = 0;
            }
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            var action = new WrapperPlayClientPlayerDigging(event).getAction();
            if (action == DiggingAction.RELEASE_USE_ITEM || action == DiggingAction.DROP_ITEM || action == DiggingAction.DROP_ITEM_STACK) {
                if (state.started != Long.MIN_VALUE && now - state.started <= 200) {
                    state.lastCancelMillis = now;
                }
                state.started = Long.MIN_VALUE;
                state.item = null;
                state.serverHandTicks = 0;
            }
        }
    }

    private static boolean isWindCharge(Material m) {
        return m != null && m.name().contains("WIND_CHARGE");
    }

    private static boolean canUseSlowItem(Player p, Material m) {
        if (m == null || m.isAir()) return false;
        if (m.isEdible()) {
            if (m == Material.GOLDEN_APPLE || m == Material.ENCHANTED_GOLDEN_APPLE
                    || m == Material.CHORUS_FRUIT || m == Material.HONEY_BOTTLE
                    || m.name().contains("SUSPICIOUS_STEW") || m.name().contains("OMINOUS_BOTTLE")) {
                return true;
            }
            return p.getFoodLevel() < 20 || p.getGameMode() == GameMode.CREATIVE;
        }
        return true;
    }

    private static Material resolveUsableItem(Player p, InteractionHand preferred) {
        Material first = (preferred == InteractionHand.MAIN_HAND
                ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand()).getType();
        if (isSlowUseItem(first)) return first;
        Material second = (preferred == InteractionHand.MAIN_HAND
                ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand()).getType();
        if (isSlowUseItem(second)) return second;
        return null;
    }

    private static boolean isSlowUseItem(Material m) {
        if (m == null || m.isAir()) return false;
        if (m.isEdible()) return true;
        return m == Material.SHIELD || m == Material.BOW || m == Material.CROSSBOW
                || m == Material.TRIDENT || m == Material.POTION || m == Material.MILK_BUCKET
                || m == Material.HONEY_BOTTLE || m == Material.SPYGLASS || m == Material.GOAT_HORN
                || m == Material.BRUSH || m.name().contains("OMINOUS_BOTTLE");
    }

    public static boolean stable(Player p, Material item) {
        State state = states.get(p.getUniqueId());
        if (state == null) return false;
        return (p.isHandRaised() || p.isBlocking()) && state.serverHandTicks >= 3 && state.item == item;
    }

    public static boolean using(Player p) {
        State state = states.get(p.getUniqueId());
        if (state == null) return p.isBlocking();
        long now = PacketContext.now();
        return ((p.isHandRaised() || p.isBlocking()) && state.serverHandTicks >= 2)
                || now < state.grimBypassUntil;
    }

    public static int useId(Player p) {
        State state = states.get(p.getUniqueId());
        return state == null ? 0 : state.useId;
    }

    public static boolean grimBypass(Player p) {
        State state = states.get(p.getUniqueId());
        return state != null && PacketContext.now() < state.grimBypassUntil;
    }

    public static void recordRocket(Player p) {
        rockets.put(p.getUniqueId(), PacketContext.now() + 5000);
    }

    public static boolean boosted(Player p) {
        return PacketContext.now() < rockets.getOrDefault(p.getUniqueId(), Long.MIN_VALUE);
    }

    public static void cleanup(UUID id) { states.remove(id); rockets.remove(id); }
    public static void clear() { states.clear(); rockets.clear(); }
}
