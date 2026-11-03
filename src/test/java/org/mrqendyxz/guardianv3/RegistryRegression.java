package org.mrqendyxz.guardianv3;

import org.mrqendyxz.guardianv3.Checks.Check;
import org.mrqendyxz.guardianv3.Managers.CheckRegistry;
import org.mrqendyxz.guardianv3.Managers.CheckManager;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import java.util.*;
import java.nio.file.*;

public final class RegistryRegression {
    @SuppressWarnings("unchecked")
    public static int verify() throws Exception {
        var checks = CheckRegistry.create();
        Set<Class<?>> registered = new HashSet<>();
        int assertions = 0;
        UUID id = UUID.randomUUID();
        for (Check check : checks) {
            if (!registered.add(check.getClass())) throw new AssertionError("Duplicate check " + check.getClass());
            assertions++;
            List<Map<UUID, Object>> maps = new ArrayList<>();
            for (var field : check.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Map<UUID, Object> map = (Map<UUID, Object>) field.get(check);
                map.put(id, new Object()); maps.add(map);
            }
            check.cleanup(id);
            for (var map : maps) {
                if (map.containsKey(id)) throw new AssertionError("Uncleaned map in " + check.getClass());
                assertions++;
            }
        }
        if (checks.stream().filter(Check::runsBeforeContext).count() != 1
                || !(checks.stream().filter(Check::runsBeforeContext).findFirst().orElseThrow()
                instanceof org.mrqendyxz.guardianv3.Checks.Packet.BadPacketsCheck)) {
            throw new AssertionError("BadPackets must run before packet context updates");
        }
        assertions++;
        Set<Class<?>> discovered = new HashSet<>();
        Path root = Path.of(Check.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var paths = Files.walk(root.resolve("org/mrqendyxz/guardianv3/Checks"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = root.relativize(path).toString().replace(java.io.File.separatorChar, '.').replaceAll("\\.class$", "");
                Class<?> type = Class.forName(name, false, Check.class.getClassLoader());
                if (Check.class.isAssignableFrom(type) && !type.isInterface() && !java.lang.reflect.Modifier.isAbstract(type.getModifiers())) discovered.add(type);
            }
        }
        if (!registered.equals(discovered)) throw new AssertionError("Check registry differs from compiled check classes: " + discovered + " / " + registered);
        assertions++;
        for (var type : List.of(PacketType.Play.Client.VEHICLE_MOVE, PacketType.Play.Client.USE_ITEM,
                PacketType.Play.Client.HELD_ITEM_CHANGE, PacketType.Play.Client.INTERACT_ENTITY,
                PacketType.Play.Client.PLAYER_DIGGING, PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT,
                PacketType.Play.Client.TELEPORT_CONFIRM, PacketType.Play.Client.PLAYER_POSITION)) {
            if (!CheckManager.acceptsIncoming(type)) throw new AssertionError("Missing packet routing: " + type);
            assertions++;
        }
        if (checks.stream().noneMatch(c -> c instanceof org.mrqendyxz.guardianv3.Checks.Movement.VelocityCheck)) throw new AssertionError("Velocity missing");
        assertions++;
        System.out.println("Registry: " + registered.size() + " / " + discovered.size() + " checks, all per-player maps cleaned.");
        return assertions;
    }
}
