package org.mrqendyxz.guardianv3.Utils;

public final class MovementEvidence {
    private int buffer;
    public boolean record(boolean suspicious, boolean severe, int threshold) {
        buffer = suspicious ? buffer + 1 : Math.max(0, buffer - 1);
        return suspicious && (severe || buffer >= Math.max(1, threshold));
    }
    public int buffer() { return buffer; }
    public boolean clean() { return buffer == 0; }
    public void reset() { buffer = 0; }
}
