package org.mrqendyxz.guardianv3.Utils;

public final class ReachGeometry {
    public static double distance(double x, double y, double z, double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        double dx = Math.max(minX - x, Math.max(0, x - maxX));
        double dy = Math.max(minY - y, Math.max(0, y - maxY));
        double dz = Math.max(minZ - z, Math.max(0, z - maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
