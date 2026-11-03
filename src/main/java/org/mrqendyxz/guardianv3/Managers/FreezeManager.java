package org.mrqendyxz.guardianv3.Managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.mrqendyxz.guardianv3.Guardianv3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FreezeManager {
    private final Map<UUID, BukkitTask> frozen = new HashMap<>();
    public void toggleFreeze(Player target) {
        UUID id = target.getUniqueId();
        if (isFrozen(id)) {
            cleanup(id);
            target.sendMessage("§c§lGuardian §8» §aYou've been unfrozen by staff!");
        } else {
            long seconds = org.mrqendyxz.guardianv3.Utils.ConfigDurations.seconds(Guardianv3.getInstance().getConfig().getLong("freeze.timeout-seconds", 300));
            frozen.put(id, Bukkit.getScheduler().runTaskLater(Guardianv3.getInstance(), () -> {
                cleanup(id);
                if (target.isOnline()) target.sendMessage("§c§lGuardian §8» §aYour freeze has expired.");
            }, seconds * 20));
            target.sendMessage("§c§lGuardian §8» §cYou've been frozen by staff!");
        }
    }
    public boolean isFrozen(UUID id) { return frozen.containsKey(id); }
    public void cleanup(UUID id) {
        BukkitTask task = frozen.remove(id);
        if (task != null) task.cancel();
    }
    public void clear() { frozen.values().forEach(BukkitTask::cancel); frozen.clear(); }
}
