package org.mrqendyxz.guardianv3.Utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;
import org.mrqendyxz.guardianv3.Guardianv3;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SetbackUtil {
    private static final class State {
        Location origin;
        final ArrayDeque<Location> ground = new ArrayDeque<>();
        boolean rejected;
        boolean applying;
        Location requested;
        long lastFailure;
    }
    private static final Map<UUID, State> states = new HashMap<>();
    private static double clearanceHeight(Player player, Location at) {
        if (player.isSwimming() || player.getHeight() <= 1.1 || MovementGuards.touchesLiquidFootprint(at, -.3, .8)) {
            return .5;
        }
        return player.getHeight();
    }
    public static void seed(Player player, Location location) {
        State state = states.computeIfAbsent(player.getUniqueId(), id -> new State());
        if (state.applying && state.requested != null
                && state.requested.getWorld() == location.getWorld()
                && state.requested.distanceSquared(location) < 1.0E-8) return;
        state.origin = location.clone();
        state.ground.clear();
        state.rejected = false;
    }
    public static void begin(Player player) {
        State state = states.get(player.getUniqueId());
        if (state != null) state.rejected = false;
    }
    public static void reject(Player player) {
        State state = states.get(player.getUniqueId());
        if (state != null) state.rejected = true;
    }
    public static void accept(Player player) {
        State state = states.get(player.getUniqueId());
        if (state == null || state.rejected) return;
        Location at = PacketContext.location(player);
        if (!MovementEnvironment.supported(at) || !MovementEnvironment.clear(at, clearanceHeight(player, at))) return;
        if (!state.ground.isEmpty() && state.ground.peekLast().getWorld() == at.getWorld()
                && state.ground.peekLast().distanceSquared(at) < .01) return;
        state.ground.addLast(at.clone());
        while (state.ground.size() > 16) state.ground.removeFirst();
    }
    public static void acceptVehicle(Player player, Location at) {
        State state = states.get(player.getUniqueId());
        if (state == null || state.rejected || at == null) return;
        if (!MovementEnvironment.clear(at, clearanceHeight(player, at))) return;
        if (!state.ground.isEmpty() && state.ground.peekLast().getWorld() == at.getWorld()
                && state.ground.peekLast().distanceSquared(at) < .04) return;
        state.ground.addLast(at.clone());
        while (state.ground.size() > 16) state.ground.removeFirst();
    }
    public static void flag(Player player, String check, int evidence) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Setback must run on the server thread");
        State state = states.get(player.getUniqueId());
        if (state == null) throw new IllegalStateException("Missing join/teleport anchor for " + player.getUniqueId());
        state.rejected = true;
        Location destination = destination(player, state);
        state.applying = true;
        state.requested = destination;
        try {
            boolean corrected = SetbackExecutor.execute(() -> AlertUtil.sendAlert(player, check, evidence), player::isOnline,
                    () -> {
                        if (destination == null) return false;
                        Entity vehicle = player.getVehicle();
                        if (vehicle != null) {
                            player.leaveVehicle();
                            vehicle.setVelocity(new Vector());
                            vehicle.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
                        }
                        return player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    },
                    () -> { player.setVelocity(new Vector()); player.setFallDistance(0); });
            if (!corrected && player.isOnline()) {
                long now = System.nanoTime();
                if (state.lastFailure == 0 || now - state.lastFailure >= 5_000_000_000L) {
                    state.lastFailure = now;
                    Guardianv3.getInstance().getLogger().warning("Setback failed for " + player.getName()
                            + ": no clear loaded anchor, or teleport cancelled by another plugin.");
                }
            }
        } finally { state.applying = false; state.requested = null; }
    }
    private static Location destination(Player player, State state) {
        var history = state.ground.descendingIterator();
        while (history.hasNext()) {
            Location candidate = history.next();
            if (candidate.getWorld() == player.getWorld()
                    && (MovementEnvironment.supported(candidate) || MovementGuards.touchesLiquidFootprint(candidate, -.5, .2))
                    && MovementEnvironment.clear(candidate, clearanceHeight(player, candidate))) return facing(player, candidate);
        }
        if (state.origin != null && state.origin.getWorld() == player.getWorld()
                && MovementEnvironment.clear(state.origin, clearanceHeight(player, state.origin))) return facing(player, state.origin);
        return null;
    }
    private static Location facing(Player player, Location at) {
        Location result = at.clone(), pose = PacketContext.location(player);
        result.setYaw(pose.getYaw()); result.setPitch(pose.getPitch());
        return result;
    }
    public static void cleanup(UUID id) { states.remove(id); }
    public static void clear() { states.clear(); }
}
