package org.mrqendyxz.guardianv3.Utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ViolationLedger {
    private record Level(int value, long decayedAt) {}
    private final Map<UUID, Map<String, Level>> levels = new ConcurrentHashMap<>();
    public int increment(UUID id, String check, long now, long decayMillis) {
        Map<String, Level> checks = levels.computeIfAbsent(id, key -> new ConcurrentHashMap<>());
        return checks.compute(check, (key, old) -> {
            if (old == null) return new Level(1, now);
            long steps = Math.max(0, now - old.decayedAt()) / decayMillis;
            int value = (int) Math.max(0, old.value() - steps);
            return new Level(value + 1, value == 0 ? now : old.decayedAt() + steps * decayMillis);
        }).value();
    }
    public void cleanup(UUID id) { levels.remove(id); }
    public void clear() { levels.clear(); }
}
