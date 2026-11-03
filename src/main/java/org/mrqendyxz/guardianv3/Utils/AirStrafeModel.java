package org.mrqendyxz.guardianv3.Utils;

public final class AirStrafeModel {
    public static boolean excessive(double x, double z, double oldX, double oldZ, double allowed) {
        return Math.hypot(x, z) > Math.hypot(oldX, oldZ) + .03
                && Math.hypot(x - oldX * .91, z - oldZ * .91) > allowed;
    }
}
