package org.mrqendyxz.guardianv3.Utils;

public final class BadPacketRules {
    private BadPacketRules() {}

    public static String position(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return "Position";
        if (Math.abs(x) > 30_000_000 || Math.abs(y) > 30_000_000 || Math.abs(z) > 30_000_000) return "Position";
        return null;
    }

    public static String rotation(float yaw, float pitch, boolean playerRotation) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return "Rotation";
        if (playerRotation && (pitch < -90.0001F || pitch > 90.0001F)) return "Pitch";
        return null;
    }

    public static String hotbar(int slot) { return slot < 0 || slot > 8 ? "Slot" : null; }
    public static String attack(int target, int self) { return target == self ? "SelfAttack" : null; }
}
