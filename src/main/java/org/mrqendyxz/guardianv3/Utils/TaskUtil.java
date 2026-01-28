package org.mrqendyxz.guardianv3.Utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class TaskUtil {

    private static Plugin cachedPlugin = null;

    private static Plugin getPlugin() {
        if (cachedPlugin != null) return cachedPlugin;

        cachedPlugin = Bukkit.getPluginManager().getPlugin("guardianv3");

        if (cachedPlugin == null) {
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                if (p.getName().equalsIgnoreCase("guardianv3")) {
                    cachedPlugin = p;
                    break;
                }
            }
        }
        return cachedPlugin;
    }

    public static void run(Runnable runnable) {
        Plugin plugin = getPlugin();

        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runAsync(Runnable runnable) {
        Plugin plugin = getPlugin();
        if (plugin != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }
}