package org.mrqendyxz.guardianv3.Utils;

public final class PacketRateWindow {
    private long start = Long.MIN_VALUE;
    private int count;
    public boolean record(long nowMillis, int limit) {
        if (start == Long.MIN_VALUE || nowMillis - start >= 1000 || nowMillis < start) {
            start = nowMillis;
            count = 0;
        }
        return ++count == limit + 1;
    }
}
