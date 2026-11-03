package org.mrqendyxz.guardianv3.Utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.mrqendyxz.guardianv3.Guardianv3;
import org.mrqendyxz.guardianv3.Managers.FreezeManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FreezeListener implements Listener {
    private final FreezeManager freezeManager;
    private final Map<UUID, Long> lastMessage = new HashMap<>();
    public FreezeListener(FreezeManager fm) { freezeManager = fm; }
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!freezeManager.isFrozen(id)) { lastMessage.remove(id); return; }
        Location to = event.getTo(), from = event.getFrom();
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) return;
        Location frozen = from.clone();
        frozen.setYaw(to.getYaw()); frozen.setPitch(to.getPitch());
        event.setTo(frozen);
        notifyFrozen(event.getPlayer());
    }
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (freezeManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            notifyFrozen(event.getPlayer());
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (freezeManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            notifyFrozen(event.getPlayer());
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (freezeManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            notifyFrozen(event.getPlayer());
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (freezeManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            notifyFrozen(event.getPlayer());
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (freezeManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            notifyFrozen(event.getPlayer());
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && freezeManager.isFrozen(attacker.getUniqueId())) {
            event.setCancelled(true);
            notifyFrozen(attacker);
        } else if (event.getEntity() instanceof Player victim && freezeManager.isFrozen(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }
    private void notifyFrozen(Player player) {
        UUID id = player.getUniqueId();
        long now = System.nanoTime() / 1_000_000L;
        long cooldown = ConfigDurations.seconds(Guardianv3.getInstance().getConfig().getLong("freeze.message-cooldown-seconds", 3)) * 1000;
        if (now - lastMessage.getOrDefault(id, Long.MIN_VALUE / 2) >= cooldown) {
            lastMessage.put(id, now);
            player.sendMessage("§c§lGuardian §8» §fYou've been frozen by staff!");
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        freezeManager.cleanup(id); lastMessage.remove(id);
    }
}
