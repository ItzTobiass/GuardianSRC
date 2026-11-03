package org.mrqendyxz.guardianv3.Utils;

public final class RotationMath {
    public static float deltaDegrees(float a, float b) {
        if (!Float.isFinite(a) || !Float.isFinite(b)) return 0;
        return (float) Math.IEEEremainder((double) a - b, 360.0);
    }
}
