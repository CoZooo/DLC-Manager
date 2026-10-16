package com.cozooo.dlc_manager.client.screen;

public final class DlcUiMotion {
    private static final long DURATION = 240_000_000L;
    private double start;
    private double target;
    private long startedAt;

    public DlcUiMotion(double value) {
        start = target = Math.max(0, Math.min(1, value));
    }

    public double getTarget() { return target; }

    public void animateTo(double value, long now) {
        value = Math.max(0, Math.min(1, value));
        if (value == target) return;
        start = valueAt(now);
        target = value;
        startedAt = now;
    }

    public double valueAt(long now) {
        if (start == target) return target;
        double time = Math.max(0, Math.min(1, (double) (now - startedAt) / DURATION));
        if (time == 0) return start;
        if (time == 1) return target;
        double low = 0, high = 1;
        for (int i = 0; i < 24; i++) {
            double t = (low + high) / 2;
            double x = 0.6 * t * (1 - t) * (1 - t) + t * t * t;
            if (x < time) low = t; else high = t;
        }
        double t = (low + high) / 2;
        double eased = t * t * (3 - 2 * t);
        return start + (target - start) * eased;
    }
}
