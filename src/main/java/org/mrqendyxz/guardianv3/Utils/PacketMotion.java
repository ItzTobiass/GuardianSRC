package org.mrqendyxz.guardianv3.Utils;

public final class PacketMotion {
    private double x, y, z, dx, dy, dz;
    private float yaw, pitch;
    private boolean hasPosition;
    public PacketMotion(double x, double y, double z, float yaw, float pitch) {
        this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
    }
    public void update(boolean position, double x, double y, double z,
                       boolean rotation, float yaw, float pitch) {
        dx = dy = dz = 0;
        if (position) {
            if (hasPosition) { dx = x - this.x; dy = y - this.y; dz = z - this.z; }
            this.x = x; this.y = y; this.z = z;
            hasPosition = true;
        }
        if (rotation) { this.yaw = yaw; this.pitch = pitch; }
    }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double dx() { return dx; }
    public double dy() { return dy; }
    public double dz() { return dz; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
}
