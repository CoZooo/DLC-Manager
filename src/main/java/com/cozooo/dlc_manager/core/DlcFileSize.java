package com.cozooo.dlc_manager.core;

import java.util.Locale;

public final class DlcFileSize {
    public static final long KIB = 1024L;
    public static final long MIB = KIB * 1024L;
    public static final long GIB = MIB * 1024L;
    private static final String[] UNITS = {"B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};

    private DlcFileSize() {}

    public static long ceilMiB(long bytes) {
        if (bytes <= 0) return 0;
        return bytes / MIB + (bytes % MIB == 0 ? 0 : 1);
    }

    public static String formatProgress(long done, long total) {
        return format(done) + (total < 0 ? "" : "/" + format(total));
    }

    public static String format(long bytes) {
        if (bytes < 0) return "--";
        if (bytes < KIB) return bytes + " B";
        double value = bytes;
        int unit = 0;
        while (value >= KIB && unit < UNITS.length - 1) { value /= KIB; unit++; }
        return String.format(Locale.ROOT, "%.1f %s", value, UNITS[unit]);
    }
}
