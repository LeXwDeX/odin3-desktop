package com.odin.desktop.service.fan;

/** Serializes user-initiated performance/fan transactions. No background policy owns a fan mode. */
public final class FanControlCoordinator {
    public static final int OFF = 0;
    public static final int SMART = 4;
    public static final int MAX = 5;

    public interface Backend {
        int readPerformance();
        int readFan();
        int readConfiguredFan();
        void writeFan(int mode);
        void writePerformanceAndFan(int performance, int fan);
    }

    public static final class Snapshot {
        public final int performanceMode;
        public final int fanMode;

        Snapshot(int performanceMode, int fanMode) {
            this.performanceMode = performanceMode;
            this.fanMode = fanMode;
        }
    }

    public synchronized Snapshot setPerformance(Backend backend, int performance) {
        if (performance < 0 || performance > 2) throw new IllegalArgumentException("Invalid performance mode");
        // Keep the existing user-triggered performance/cooling linkage.
        int configuredFan = backend.readConfiguredFan();
        int target = configuredFan == MAX ? MAX : performance != 0 ? SMART : OFF;
        return setPerformance(backend, performance, target);
    }

    /** UI commands carry the target selected alongside the visible performance choice. */
    public synchronized Snapshot setPerformance(Backend backend, int performance, int target) {
        if (performance < 0 || performance > 2) throw new IllegalArgumentException("Invalid performance mode");
        requireFan(target);
        if (performance != 0 && target == OFF) throw new IllegalArgumentException("Elevated performance requires cooling");
        backend.writePerformanceAndFan(performance, target);
        return new Snapshot(backend.readPerformance(), backend.readFan());
    }

    public synchronized int setManualFan(Backend backend, int mode) {
        requireFan(mode);
        backend.writeFan(mode);
        return backend.readFan();
    }

    private static void requireFan(int mode) {
        if (mode != OFF && mode != SMART && mode != MAX) throw new IllegalArgumentException("Invalid fan mode");
    }
}
