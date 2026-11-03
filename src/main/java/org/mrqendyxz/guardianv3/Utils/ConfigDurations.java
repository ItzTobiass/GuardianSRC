package org.mrqendyxz.guardianv3.Utils;

public final class ConfigDurations {
    public static long seconds(long value) { return Math.max(1, Math.min(86_400, value)); }
}
