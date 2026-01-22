package org.mrqendyxz.guardianv3.Utils;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.mrqendyxz.guardianv3.Managers.FreezeManager;

public class FreezeListener implements Listener {
    private final FreezeManager freezeManager;

    public FreezeListener(FreezeManager fm) {
        this.freezeManager = fm;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (freezeManager.isFrozen(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
                event.getPlayer().sendMessage("§c§lGuardian §8» §fYou've been frozen by staff!");
            }
        }
    }
}