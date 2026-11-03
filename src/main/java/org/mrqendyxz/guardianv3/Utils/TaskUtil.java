package org.mrqendyxz.guardianv3.Utils;

import org.bukkit.Bukkit;
import org.mrqendyxz.guardianv3.Guardianv3;

public final class TaskUtil {
    public static void run(Runnable runnable) {
        Guardianv3 plugin = Guardianv3.getInstance();
        if (plugin == null || !plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) runnable.run();
        else Bukkit.getScheduler().runTask(plugin, runnable);
    }
    public static void runAsync(Runnable runnable) {
        Guardianv3 plugin = Guardianv3.getInstance();
        if (plugin != null && plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }
}
