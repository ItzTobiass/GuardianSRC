package org.mrqendyxz.guardianv3.Utils;

public final class SustainedRate {
    private long start = Long.MIN_VALUE;
    private int count, consecutive;
    public boolean record(long now, int limit, int requiredWindows) {
        boolean flag = false;
        if (start == Long.MIN_VALUE || now < start) { start = now; count = consecutive = 0; }
        if (now - start >= 1000) {
            consecutive = now - start < 2000 && count * 1000L > Math.max(1, limit) * (now - start) ? consecutive + 1 : 0;
            flag = consecutive >= requiredWindows;
            if (flag) consecutive = 0;
            start = now; count = 0;
        }
        count++;
        return flag;
    }
}
