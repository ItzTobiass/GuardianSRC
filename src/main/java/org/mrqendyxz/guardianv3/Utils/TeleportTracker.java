package org.mrqendyxz.guardianv3.Utils;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportTracker {
    private final Map<UUID, Integer> waiting = new ConcurrentHashMap<>();
    public void sent(UUID player, int teleportId) { waiting.put(player, teleportId); }
    public boolean confirm(UUID player, int teleportId) { return waiting.remove(player, teleportId); }
    public boolean pending(UUID player) { return waiting.containsKey(player); }
    public void cleanup(UUID player) { waiting.remove(player); }
    public void clear() { waiting.clear(); }
}
