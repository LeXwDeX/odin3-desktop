package com.odin.desktop.service.fan;

import java.util.function.BooleanSupplier;

/** Serializes fan ownership with performance changes and rejects outdated policy decisions. */
public final class FanControlCoordinator {
    public static final int OFF = 0;
    public static final int SMART = 4;
    public static final int MAX = 5;

    public interface Backend {
        int readPerformance();
        int readFan();
        int readConfiguredFan();
        boolean readAutoEnabled();
        void writeAutoEnabled(boolean enabled);
        void writeFan(int mode);
        void writePerformanceAndFan(int performance, int fan);
    }

    public static final class Snapshot {
        public final long revision;
        public final int performanceMode;
        public final int fanMode;
        public final boolean autoEnabled;
        public final boolean mutedByPolicy;

        Snapshot(long revision, int performanceMode, int fanMode, boolean autoEnabled, boolean mutedByPolicy) {
            this.revision = revision;
            this.performanceMode = performanceMode;
            this.fanMode = fanMode;
            this.autoEnabled = autoEnabled;
            this.mutedByPolicy = mutedByPolicy;
        }
    }

    private long revision;
    private volatile boolean mutedByPolicy;

    // Lifecycle hint only: never block the main thread behind a hardware transaction.
    public boolean hasPendingRecovery() { return mutedByPolicy; }

    public enum ThermalDecision { HOLD, COOL, ALLOW_QUIET }

    /** Monotonic time, not event counts: battery broadcasts cannot accelerate a decision. */
    public static final class ThermalGate {
        public static final float WARM_C = 60;
        public static final float COOL_C = 55;
        public static final float HOT_C = 75;
        public static final float IMMEDIATE_COOLING_C = 90;
        public static final long HOT_DURATION_MS = 4_000;
        public static final long WARM_DURATION_MS = 16_000;
        public static final long COOL_DURATION_MS = 24_000;
        private static final long MAX_SAMPLE_GAP_MS = 20_000;
        private long warmSince = -1, hotSince = -1, coolSince = -1, lastSample = -1;
        private boolean cooling;

        public void reset() {
            warmSince = hotSince = coolSince = lastSample = -1;
            cooling = false;
        }

        public void sensorFailed() {
            reset();
            cooling = true;
        }

        /** Confirm active temperature windows more often; an idle desktop still samples every 8s. */
        public long nextSampleDelayMs() {
            return cooling || warmSince >= 0 || hotSince >= 0 ? 2_000 : 8_000;
        }

        public ThermalDecision evaluate(float temperature, long now) {
            if (!Float.isFinite(temperature) || temperature <= 0 || now < 0) {
                sensorFailed();
                throw new IllegalArgumentException("CPU/GPU temperature is unavailable");
            }
            if (lastSample >= 0 && (now < lastSample || now - lastSample > MAX_SAMPLE_GAP_MS)) {
                warmSince = hotSince = coolSince = -1;
            }
            lastSample = now;
            if (temperature >= HOT_C) {
                if (hotSince < 0) hotSince = now;
            } else hotSince = -1;
            if (temperature >= IMMEDIATE_COOLING_C ||
                    (hotSince >= 0 && now - hotSince >= HOT_DURATION_MS)) {
                cooling = true;
                warmSince = hotSince = coolSince = -1;
            }
            if (cooling) {
                if (temperature <= COOL_C) {
                    if (coolSince < 0) coolSince = now;
                    if (now - coolSince >= COOL_DURATION_MS) {
                        cooling = false;
                        warmSince = coolSince = -1;
                        return ThermalDecision.ALLOW_QUIET;
                    }
                } else coolSince = -1;
                return ThermalDecision.COOL;
            }
            if (temperature > WARM_C) {
                if (warmSince < 0) warmSince = now;
                if (now - warmSince >= WARM_DURATION_MS) {
                    cooling = true;
                    return ThermalDecision.COOL;
                }
                return ThermalDecision.HOLD;
            }
            warmSince = -1;
            return ThermalDecision.ALLOW_QUIET;
        }
    }

    public synchronized Snapshot snapshot(Backend backend) {
        return new Snapshot(revision, backend.readPerformance(), backend.readFan(),
                backend.readAutoEnabled(), mutedByPolicy);
    }

    public synchronized Snapshot setPerformance(Backend backend, int performance) {
        if (performance < 0 || performance > 2) throw new IllegalArgumentException("Invalid performance mode");
        // Configuration is the user's intent even when an OEM transition has changed the PWM.
        int configuredFan = backend.readConfiguredFan();
        boolean autoEnabled = backend.readAutoEnabled();
        int target = !autoEnabled && configuredFan == MAX ? MAX
                : autoEnabled || performance != 0 ? SMART : OFF;
        return setPerformance(backend, performance, target);
    }

    /** UI commands carry the fan target chosen alongside the visible performance selection. */
    public synchronized Snapshot setPerformance(Backend backend, int performance, int target) {
        if (performance < 0 || performance > 2) throw new IllegalArgumentException("Invalid performance mode");
        requireFan(target);
        if (performance != 0 && target == OFF) throw new IllegalArgumentException("Elevated performance requires cooling");
        revision++;
        backend.writePerformanceAndFan(performance, target);
        mutedByPolicy = false;
        return snapshot(backend);
    }

    public synchronized int setManualFan(Backend backend, int mode) {
        requireFan(mode);
        revision++;
        backend.writeAutoEnabled(false);
        // A manual OFF releases ownership too; later recovery must not replace it with SMART.
        mutedByPolicy = false;
        backend.writeFan(mode);
        return backend.readFan();
    }

    public synchronized void setAutoEnabled(Backend backend, boolean enabled) {
        revision++;
        backend.writeAutoEnabled(enabled);
        if (!enabled) {
            restoreOwnedCooling(backend);
        } else if (backend.readPerformance() != 0) {
            int current = backend.readFan();
            int target = safeCoolingMode(current);
            if (current != target) backend.writeFan(target);
            mutedByPolicy = false;
        } else {
            // Enabling automation explicitly hands an existing OFF to the policy, including
            // the gap before its first evaluation or a sensor-read failure during startup.
            mutedByPolicy = backend.readFan() == OFF;
        }
    }

    public static int safeCoolingMode(int current) {
        return current == MAX ? MAX : SMART;
    }

    public static Integer policyTarget(Snapshot snapshot, ThermalDecision thermal, boolean connectedToPower,
            boolean game, boolean knownForeground) {
        if (!snapshot.autoEnabled) return snapshot.mutedByPolicy ? SMART : null;
        if (thermal == null) throw new IllegalArgumentException("Missing thermal decision");
        if (thermal == ThermalDecision.COOL || game || snapshot.performanceMode != 0) {
            return safeCoolingMode(snapshot.fanMode);
        }
        if (!connectedToPower || !knownForeground) {
            return snapshot.mutedByPolicy ? safeCoolingMode(snapshot.fanMode) : null;
        }
        return thermal == ThermalDecision.ALLOW_QUIET ? OFF : null;
    }

    /** Returns false when a newer user action, OEM change, or policy write superseded the snapshot. */
    public synchronized boolean applyPolicy(Backend backend, Snapshot expected, int target,
            BooleanSupplier requestIsCurrent) {
        requireFan(target);
        if (expected.revision != revision) return false;
        boolean autoEnabled = backend.readAutoEnabled();
        int performance = backend.readPerformance();
        int fan = backend.readFan();
        if (autoEnabled != expected.autoEnabled || performance != expected.performanceMode || fan != expected.fanMode) {
            return false;
        }
        if (!requestIsCurrent.getAsBoolean()) return false;
        if (!autoEnabled) {
            // Only retry our own failed disable recovery. Manual selection cleared ownership.
            if (!mutedByPolicy || target != SMART) return false;
            revision++;
            restoreOwnedCooling(backend);
            return true;
        }
        // A stale or erroneous policy must never mute an elevated performance mode.
        if (target == OFF && performance != 0) return false;
        if (fan == target) {
            // Once automation accepts quiet conditions it owns OFF, even if no write is
            // needed. Otherwise re-enabling automation after manual OFF cannot recover it.
            mutedByPolicy = target == OFF;
            return true;
        }
        revision++;
        // A rejected response can follow a partial OFF write, which still needs recovery.
        if (target == OFF) mutedByPolicy = true;
        backend.writeFan(target);
        mutedByPolicy = target == OFF;
        return true;
    }

    private void restoreOwnedCooling(Backend backend) {
        if (!mutedByPolicy) return;
        int current = backend.readFan();
        if (current == OFF) backend.writeFan(SMART);
        // Keep ownership on an exception so the watchdog can retry the recovery.
        mutedByPolicy = false;
    }

    private static void requireFan(int mode) {
        if (mode != OFF && mode != SMART && mode != MAX) throw new IllegalArgumentException("Invalid fan mode");
    }
}
