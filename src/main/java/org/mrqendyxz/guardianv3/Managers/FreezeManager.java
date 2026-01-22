package org.mrqendyxz.guardianv3.Managers;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FreezeManager {
    private final Set<UUID> frozenPlayers = new HashSet<>();

    public void toggleFreeze(Player target) {
        UUID uuid = target.getUniqueId();
        if (frozenPlayers.contains(uuid)) {
            frozenPlayers.remove(uuid);
            target.removePotionEffect(PotionEffectType.BLINDNESS);
            target.removePotionEffect(PotionEffectType.SLOWNESS);
            target.sendMessage("§c§lGuardian §8» §aYou've been unfrozen by staff!");
        } else {
            frozenPlayers.add(uuid);
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false));
            target.sendMessage("§c§lGuardian §8» §cYou've been frozen by staff!");
        }
    }

    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.contains(uuid);
    }
}