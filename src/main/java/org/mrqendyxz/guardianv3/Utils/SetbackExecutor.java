package org.mrqendyxz.guardianv3.Utils;

import java.util.function.BooleanSupplier;

public final class SetbackExecutor {
    public static boolean execute(Runnable alert, BooleanSupplier online,
                                  BooleanSupplier teleport, Runnable stopMovement) {
        alert.run();
        if (!online.getAsBoolean() || !teleport.getAsBoolean()) return false;
        stopMovement.run();
        return true;
    }
}
