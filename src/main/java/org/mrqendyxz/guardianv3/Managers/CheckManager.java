package org.mrqendyxz.guardianv3.Managers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.mrqendyxz.guardianv3.Guardianv3;
import org.mrqendyxz.guardianv3.Utils.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CheckManager implements PacketListener, Listener {
    private final List<org.mrqendyxz.guardianv3.Checks.Check> checks = CheckRegistry.create();
    private record Pending(ProtocolPacketEvent event, UUID id, long generation, long received) {}
    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    private final Map<UUID, Long> generations = new ConcurrentHashMap<>();
    private final TeleportTracker pendingTeleports = new TeleportTracker();
    private final Map<UUID, Integer> queued = new HashMap<>();
    private final Set<UUID> overflow = new HashSet<>();
    private final Set<UUID> overloaded = new HashSet<>();
    private final Set<UUID> bypassed = new HashSet<>();
    private long lastErrorLog;
    private BukkitTask task;
    private PacketListenerCommon registration;
    private volatile boolean closed;

    public static boolean acceptsIncoming(com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon type) {
        return WrapperPlayClientPlayerFlying.isFlying(type) || type == PacketType.Play.Client.INTERACT_ENTITY
                || type == PacketType.Play.Client.PLAYER_DIGGING || type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
                || type == PacketType.Play.Client.VEHICLE_MOVE || type == PacketType.Play.Client.USE_ITEM
                || type == PacketType.Play.Client.HELD_ITEM_CHANGE || type == PacketType.Play.Client.TELEPORT_CONFIRM;
    }
    public void register() {
        registration = PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.MONITOR);
        Bukkit.getPluginManager().registerEvents(this, Guardianv3.getInstance());
        for (Player player : Bukkit.getOnlinePlayers()) initialize(player, player.getLocation());
        task = Bukkit.getScheduler().runTaskTimer(Guardianv3.getInstance(), this::drain, 1, 1);
    }

    @Override public void onPacketReceive(PacketReceiveEvent event) {
        if (closed || event.isCancelled() || !(event.getPlayer() instanceof Player)) return;
        var type = event.getPacketType();
        UUID id = event.getUser().getUUID();
        if (type == PacketType.Play.Client.TELEPORT_CONFIRM) {
            var confirmation = new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTeleportConfirm(event);
            if (id != null) pendingTeleports.confirm(id, confirmation.getTeleportId());
            return;
        }
        if (id != null && pendingTeleports.pending(id)) return;
        if (!acceptsIncoming(type)) return;
        enqueue(event);
    }
    @Override public void onPacketSend(PacketSendEvent event) {
        if (closed || event.isCancelled() || !(event.getPlayer() instanceof Player)) return;
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) {
            var teleport = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook(event);
            UUID id = event.getUser().getUUID();
            if (id != null) pendingTeleports.sent(id, teleport.getTeleportId());
        } else if ((event.getPacketType() == PacketType.Play.Server.ENTITY_VELOCITY || event.getPacketType() == PacketType.Play.Server.EXPLOSION)) enqueue(event);
    }
    private synchronized void enqueue(ProtocolPacketEvent event) {
        if (closed) return;
        UUID id = event.getUser().getUUID();
        if (id == null) return;
        if (queued.getOrDefault(id, 0) >= 256) {
            overflow.add(id);
            return;
        }
        if (queue.size() >= 8192) {
            overloaded.add(id);
            return;
        }
        ProtocolPacketEvent copy = event.clone();
        if (copy == null) return;
        Object payload;
        try { payload = ByteBufHelper.copy(copy.getByteBuf()); }
        finally { copy.cleanUp(); }
        copy.setByteBuf(payload);
        queue.add(new Pending(copy, id, generations.getOrDefault(id, 0L), System.nanoTime()));
        queued.merge(id, 1, Integer::sum);
    }
    private void drain() {
        WorldCache.clear();
        MovementEnvironment.clearCache();
        PacketContext.beginBatch();
        CombatHistory.capture();
        List<Pending> batch;
        Set<UUID> dropped;
        Set<UUID> flooded;
        synchronized (this) {
            batch = new ArrayList<>(queue);
            queue.clear(); queued.clear();
            flooded = new HashSet<>(overflow);
            dropped = new HashSet<>(overloaded);
            dropped.addAll(overflow);
            overflow.clear(); overloaded.clear();
        }
        for (UUID id : dropped) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && flooded.contains(id)
                    && !player.isOp() && !player.hasPermission("guardian.bypass")) {
                SetbackUtil.flag(player, "PacketFlood", 256);
            } else resetChecks(id);
        }
        for (Pending pending : batch) {
            try {
                Player player = pending.event().getPlayer();
                if (dropped.contains(pending.id()) || !player.isOnline()
                        || Bukkit.getPlayer(pending.id()) != player
                        || pending.generation() != generations.getOrDefault(pending.id(), 0L)) continue;
                if (player.isOp() || player.hasPermission("guardian.bypass")
                        || Guardianv3.getInstance().getFreezeManager().isFrozen(player.getUniqueId())) {
                    bypassed.add(player.getUniqueId()); continue;
                }
                if (bypassed.remove(player.getUniqueId())) { resetChecks(player.getUniqueId()); initialize(player, player.getLocation()); continue; }
                if (System.nanoTime() - pending.received() > 250_000_000L) {
                    resetChecks(player.getUniqueId()); PacketContext.seed(player, player.getLocation()); continue;
                }
                PacketContext.time(pending.received());
                if (pending.event() instanceof PacketReceiveEvent receive) handle(receive, player);
                else if (pending.event() instanceof PacketSendEvent send) {
                    for (var check : checks) check.handleSend(send);
                    VelocityProtection.handleSend(send);
                }
            } catch (RuntimeException ex) {
                logFailure("packet dispatch", ex);
            } finally {
                pending.event().cleanUp();
            }
        }
        WorldCache.clear();
        MovementEnvironment.clearCache();
    }
    private void handle(PacketReceiveEvent event, Player player) {
        long generation = generations.getOrDefault(player.getUniqueId(), 0L);
        for (var check : checks) {
            if (!check.runsBeforeContext()) continue;
            try { check.handle(event); }
            catch (RuntimeException ex) {
                check.cleanup(player.getUniqueId()); SetbackUtil.reject(player);
                logFailure(check.getClass().getSimpleName(), ex);
                return;
            }
            if (event.isCancelled() || !player.isOnline()
                    || generation != generations.getOrDefault(player.getUniqueId(), 0L)) return;
        }
        ItemUseTracker.handle(event);
        MovementGuards.handlePlacement(player, event);
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            var flying = new WrapperPlayClientPlayerFlying(event);
            var position = flying.getLocation();
            if (flying.hasPositionChanged() && (!Double.isFinite(position.getX())
                    || !Double.isFinite(position.getY()) || !Double.isFinite(position.getZ())
                    || Math.abs(position.getX()) > 30_000_000 || Math.abs(position.getY()) > 30_000_000
                    || Math.abs(position.getZ()) > 30_000_000)) return;
            if (flying.hasRotationChanged() && (!Float.isFinite(position.getYaw()) || !Float.isFinite(position.getPitch()))) return;
            PacketContext.update(player, flying);
            SetbackUtil.begin(player);
            MovementGuards.observe(player);
        }
        for (var check : checks) {
            if (check.runsBeforeContext()) continue;
            try { check.handle(event); }
            catch (RuntimeException ex) {
                check.cleanup(player.getUniqueId()); SetbackUtil.reject(player);
                logFailure(check.getClass().getSimpleName(), ex);
            }
            if (!player.isOnline() || generation != generations.getOrDefault(player.getUniqueId(), 0L)) {
                return;
            }
        }
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) SetbackUtil.accept(player);
    }
    private void logFailure(String check, RuntimeException ex) {
        long now = System.nanoTime();
        if (lastErrorLog == 0 || now - lastErrorLog > 5_000_000_000L) {
            lastErrorLog = now;
            Guardianv3.getInstance().getLogger().log(java.util.logging.Level.WARNING, "Check processing failed: " + check, ex);
        }
    }
    private void initialize(Player player, org.bukkit.Location at) {
        SetbackUtil.seed(player, at);
        PacketContext.seed(player, at);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { initialize(event.getPlayer(), event.getPlayer().getLocation()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        VelocityProtection.recordBukkitVelocity(event.getPlayer(), event.getVelocity());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            VelocityProtection.recordDamage(player);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getType().name().contains("WIND_CHARGE")
                && event.getEntity().getShooter() instanceof Player shooter) {
            VelocityProtection.recordWindCharge(shooter);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity().getType().name().contains("WIND_CHARGE")) {
            for (Entity nearby : event.getEntity().getNearbyEntities(6, 6, 6)) {
                if (nearby instanceof Player player) {
                    VelocityProtection.recordWindCharge(player);
                }
            }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        for (Entity nearby : event.getEntity().getNearbyEntities(8, 8, 8)) {
            if (nearby instanceof Player player) {
                VelocityProtection.recordWindCharge(player);
            }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        invalidate(event.getPlayer().getUniqueId());
        initialize(event.getPlayer(), event.getTo());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        invalidate(event.getPlayer().getUniqueId());
        initialize(event.getPlayer(), event.getRespawnLocation());
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { cleanup(event.getPlayer().getUniqueId()); }
    @Override public void onUserDisconnect(UserDisconnectEvent event) {
        UUID id = event.getUser().getUUID();
        if (id != null) TaskUtil.run(() -> { if (Bukkit.getPlayer(id) == null) cleanup(id); });
    }
    private synchronized void invalidate(UUID id) {
        generations.merge(id, 1L, Long::sum);
        resetChecks(id);
    }
    private void resetChecks(UUID id) {
        for (var check : checks) check.cleanup(id);
        PacketContext.cleanup(id);
        MovementGuards.cleanup(id);
        ItemUseTracker.cleanup(id);
        CombatHistory.cleanup(id);
    }
    public synchronized void cleanup(UUID id) {
        queue.removeIf(pending -> {
            if (!pending.id().equals(id)) return false;
            pending.event().cleanUp();
            return true;
        });
        queued.remove(id); overflow.remove(id); overloaded.remove(id); generations.remove(id);
        resetChecks(id);
        VelocityProtection.cleanup(id);
        AlertUtil.cleanup(id);
        SetbackUtil.cleanup(id);
        pendingTeleports.cleanup(id);
        bypassed.remove(id);
    }
    public synchronized void close() {
        closed = true;
        PacketEvents.getAPI().getEventManager().unregisterListener(registration);
        if (task != null) task.cancel();
        for (Pending pending : queue) pending.event().cleanUp();
        queue.clear(); queued.clear(); overflow.clear(); overloaded.clear(); generations.clear();
        for (Player player : Bukkit.getOnlinePlayers()) resetChecks(player.getUniqueId());
        PacketContext.clear(); WorldCache.clear(); AlertUtil.clear(); VelocityProtection.clear();
        SetbackUtil.clear(); MovementEnvironment.clearCache(); pendingTeleports.clear();
        MovementGuards.clear(); ItemUseTracker.clear(); CombatHistory.clear(); bypassed.clear();
    }
}
