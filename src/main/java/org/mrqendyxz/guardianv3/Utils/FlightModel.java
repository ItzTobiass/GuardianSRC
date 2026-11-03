package org.mrqendyxz.guardianv3.Utils;

public final class FlightModel {
    private final MovementEvidence evidence = new MovementEvidence();
    private double expected;
    private double previousDelta;
    private double bounce;
    private int airTicks;
    private long lastPacket;
    public boolean record(double dy, boolean supported, double bounceFactor, double jump,
                          double impulse, long now, int threshold) {
        if (supported) {
            bounce = bounceFactor > 0 ? Math.max(0, -Math.min(previousDelta, dy)) * bounceFactor : 0;
            airTicks = 0;
            expected = 0;
            previousDelta = dy;
            lastPacket = now;
            evidence.record(false, false, threshold);
            return false;
        }
        double launch = Math.max(Math.max(jump, bounce), impulse);
        boolean lateTakeoff = airTicks == 1 && previousDelta <= 0 && previousDelta >= -.16 && dy > 0 && dy <= jump + .05;
        double initialLimit = (dy > 0 || impulse > 0) ? launch : 0;
        double limit = (airTicks == 0 || lateTakeoff) ? initialLimit : (impulse > 0 ? Math.max(expected, impulse) : expected);
        boolean suspicious = dy - limit > .075 && !(impulse > 0 && dy <= impulse + .05);
        boolean flag = evidence.record(suspicious, dy > launch + .35, threshold);
        expected = (Math.min(dy, launch) - .08) * .98;
        previousDelta = dy;
        airTicks++;
        lastPacket = now;
        return flag;
    }
    public boolean clean() { return evidence.clean(); }
    public int buffer() { return evidence.buffer(); }
    public void reset() {
        evidence.reset(); expected = previousDelta = bounce = 0; airTicks = 0; lastPacket = 0;
    }
}
