package com.odin.hardware;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Fixed hardware operations, verification and rollback, shared by OEM Binder and developer bridge. */
public class HardwareOperations {
    static final String PERFORMANCE = "performance_mode";
    static final String PROPERTY = "persist.vendor.debug.mode";
    static final String FAN = "fan_mode";
    static final String LIGHT = "joystick_light_enabled";
    static final String HANDLE_LIGHT = "joystick_handle_light_enabled";
    static final String COLOR = "joystick_led_light_picker_color";
    static final String CHARGE = "percent_80_charge_limit";
    static final String POWER = "charging_limit_power_limit";
    static final String CHARGING_SEPARATION = "is_charging_separation";
    private final Store store;

    public HardwareOperations(Store store) { this.store = Objects.requireNonNull(store); }

    public synchronized String execute(String body) {
        String[] parts = body.split("\t", -1);
        if (body.length() > 384 || body.indexOf('\n') >= 0 || body.indexOf('\r') >= 0) return "ERR\tBAD_REQUEST";
        if (parts.length == 2 && "AIRPLANE".equals(parts[0]) && parts[1].matches("[01]")) {
            String previous;
            try { previous = store.airplane(); }
            catch (Exception unavailable) { return "ERR\tREAD_UNAVAILABLE"; }
            if (previous == null || !previous.matches("[01]")) return "ERR\tREAD_UNAVAILABLE";
            try {
                store.airplane(parts[1]);
                if (!parts[1].equals(store.airplane())) throw new ReadbackMismatch();
                return "OK\tAIRPLANE\t" + parts[1];
            } catch (Exception failure) {
                try {
                    store.airplane(previous);
                    if (!previous.equals(store.airplane())) return "ERR\tROLLBACK_INCOMPLETE";
                } catch (Exception restoration) { return "ERR\tROLLBACK_INCOMPLETE"; }
                return failure instanceof ReadbackMismatch ? "ERR\tREADBACK_MISMATCH" : "ERR\tWRITE_REJECTED";
            }
        }
        if (parts.length == 1 && "PERFORMANCE_GET".equals(parts[0])) {
            try {
                String value = store.property();
                return value != null && value.matches("[012]") ? "OK\tPERFORMANCE\t" + value : "ERR\tREAD_UNAVAILABLE";
            } catch (Exception unavailable) { return "ERR\tREAD_UNAVAILABLE"; }
        }
        if (parts.length == 1 && "FAN_GET".equals(parts[0])) {
            try {
                String fan = store.get(FAN);
                if (fan == null || !fan.matches("[0-6]") || !store.fanMatches(fan)) return "ERR\tREAD_UNAVAILABLE";
                return "OK\tFAN\t" + fan;
            } catch (Exception unavailable) { return "ERR\tREAD_UNAVAILABLE"; }
        }
        if (parts.length == 1 && "FAN_TELEMETRY".equals(parts[0])) {
            try {
                String[] readings = store.fanTelemetry();
                if (readings == null || readings.length != 4) return "ERR\tREAD_UNAVAILABLE";
                for (String reading : readings) {
                    if (reading == null || !reading.matches("[0-9]{1,6}")) return "ERR\tREAD_UNAVAILABLE";
                }
                int rpm = Integer.parseInt(readings[0]);
                int state = Integer.parseInt(readings[1]);
                int duty = Integer.parseInt(readings[2]);
                int period = Integer.parseInt(readings[3]);
                if (rpm > 50000 || state > 1 || period != 50000 || duty > period) return "ERR\tREAD_UNAVAILABLE";
                return "OK\tFAN_TELEMETRY\t" + rpm + "\t" + (duty * 100 / period);
            } catch (Exception unavailable) { return "ERR\tREAD_UNAVAILABLE"; }
        }
        LinkedHashMap<String, String> changes = new LinkedHashMap<>();
        String property = null;
        String requestedFan = null;
        String reply;
        if (parts.length == 3 && "SET".equals(parts[0]) && !PERFORMANCE.equals(parts[1]) && allowed(parts[1], parts[2])) {
            changes.put(parts[1], parts[2]);
            reply = parts[1] + "\t" + parts[2];
        } else if (parts.length == 2 && "PERFORMANCE".equals(parts[0]) && parts[1].matches("[012]")) {
            changes.put(PERFORMANCE, parts[1]);
            property = parts[1];
            reply = "PERFORMANCE\t" + parts[1];
        } else if (parts.length == 3 && "PERFORMANCE_FAN".equals(parts[0]) &&
                parts[1].matches("[012]") && parts[2].matches("[045]") &&
                ("0".equals(parts[1]) || !"0".equals(parts[2]))) {
            changes.put(PERFORMANCE, parts[1]);
            property = parts[1];
            requestedFan = parts[2];
            reply = "PERFORMANCE_FAN\t" + parts[1] + "\t" + parts[2];
        } else if (parts.length == 2 && "CHARGE".equals(parts[0]) && parts[1].matches("[01]")) {
            changes.put(CHARGE, parts[1]); changes.put(POWER, parts[1]);
            reply = "CHARGE\t" + parts[1];
        } else if (parts.length == 2 && "LIGHTS".equals(parts[0]) && parts[1].matches("(?:0,0|1,1)")) {
            changes.put(LIGHT, parts[1]); changes.put(HANDLE_LIGHT, parts[1]);
            reply = "LIGHTS\t" + parts[1];
        } else return "ERR\tBAD_REQUEST";

        LinkedHashMap<String, String> previous = new LinkedHashMap<>();
        String oldProperty = null;
        boolean started = false;
        try {
            if (property != null) {
                String fan = store.get(FAN);
                if (fan == null || !fan.matches("[0-6]")) throw new IOException("Invalid existing fan");
                changes.put(FAN, requestedFan != null ? requestedFan :
                    "5".equals(fan) ? "5" : "0".equals(property) ? "0" : "4");
            }
            for (String name : changes.keySet()) {
                String old = store.get(name);
                if (!restorable(name, old)) throw new IOException("Invalid existing value");
                previous.put(name, old);
            }
            if (property != null) {
                oldProperty = store.property();
                if (!oldProperty.matches("[012]")) throw new IOException("Invalid existing mode");
            }
            started = true;
            if (property != null) {
                String observerFan = preparePerformanceObserver(property, changes.get(FAN));
                store.put(PERFORMANCE, property);
                store.property(property);
                // SystemUI's performance observer resets fan_mode; OEM init can reset PWM too.
                store.settlePerformance(observerFan);
                applyFan(changes.get(FAN));
            } else if (changes.containsKey(FAN)) {
                applyFan(changes.get(FAN));
            } else {
                store.putAll(changes);
            }
            // Read all fields after the whole transaction, including the actual active property.
            for (Map.Entry<String, String> entry : changes.entrySet()) {
                if (!entry.getValue().equals(store.get(entry.getKey()))) throw new ReadbackMismatch();
            }
            if (property != null && !property.equals(store.property())) throw new ReadbackMismatch();
            return "OK\t" + reply;
        } catch (Exception failure) {
            boolean restored = true;
            if (started) {
                if (property != null) {
                    // Restoring the mirror triggers SystemUI again. Restore BOTH performance
                    // inputs first and the fan LAST, including a partially rejected mirror write.
                    String observerFan = null;
                    try { observerFan = preparePerformanceObserver(previous.get(PERFORMANCE), previous.get(FAN)); }
                    catch (Exception ignored) { restored = false; }
                    try { store.put(PERFORMANCE, previous.get(PERFORMANCE)); }
                    catch (Exception ignored) { restored = false; }
                    try { store.property(oldProperty); }
                    catch (Exception ignored) { restored = false; }
                    try { store.settlePerformance(observerFan); }
                    catch (Exception ignored) { restored = false; }
                    try { restoreFan(previous.get(FAN)); }
                    catch (Exception ignored) { restored = false; }
                } else {
                    List<String> names = new ArrayList<>(previous.keySet());
                    Collections.reverse(names);
                    for (String name : names) {
                        try {
                            if (FAN.equals(name)) restoreFan(previous.get(name));
                            else store.put(name, previous.get(name));
                        } catch (Exception ignored) { restored = false; }
                    }
                }
                // Check the final combined state; an earlier per-field read can be invalidated
                // by a later observer notification from another field in the same rollback.
                for (String name : previous.keySet()) {
                    try { restored &= Objects.equals(previous.get(name), store.get(name)); }
                    catch (Exception ignored) { restored = false; }
                }
                if (property != null) {
                    try { restored &= Objects.equals(oldProperty, store.property()); }
                    catch (Exception ignored) { restored = false; }
                }
                if (previous.get(FAN) != null) {
                    try { store.awaitFan(previous.get(FAN)); }
                    catch (Exception ignored) { restored = false; }
                }
            }
            return "ERR\t" + (!restored ? "ROLLBACK_INCOMPLETE" :
                failure instanceof ReadbackMismatch ? "READBACK_MISMATCH" : "WRITE_REJECTED");
        }
    }

    private String preparePerformanceObserver(String performance, String fan) throws Exception {
        if (Objects.equals(performance, store.get(PERFORMANCE))) return null;
        // SystemUI has no completion API or fixed Handler delay. For transitions which could
        // downgrade our final fan, use its deterministic fan write as an acknowledgement.
        // Preselect MAX so NORMAL must produce 0, and STANDARD must produce QUIET (1).
        if ((performance == null || "0".equals(performance)) && !"0".equals(fan)) {
            applyFan("4");
            return "0";
        }
        if ("1".equals(performance) && "5".equals(fan)) {
            applyFan("5");
            return "1";
        }
        return null;
    }

    private void restoreFan(String mode) throws Exception {
        if (mode != null && allowed(FAN, mode)) applyFan(mode);
        else store.put(FAN, mode);
    }

    private void applyFan(String target) throws Exception {
        if (!"4".equals(target) && Objects.equals(target, store.get(FAN)) && store.fanMatches(target)) {
            store.awaitFan(target);
            return;
        }
        // A same-value Settings write does not notify observers. Explicit SMART requests
        // also re-arm the software thermal loop, whose liveness is not proved by static PWM.
        // Re-arm via a cooling mode,
        // never by temporarily stopping the fan, when the key and driver have diverged.
        if (Objects.equals(target, store.get(FAN))) {
            String intermediate = "4".equals(target) ? "5" : "4";
            store.put(FAN, intermediate);
            store.awaitFan(intermediate);
        }
        store.put(FAN, target);
        store.awaitFan(target);
    }

    static boolean allowed(String name, String value) {
        if (PERFORMANCE.equals(name)) return value.matches("[012]");
        if (FAN.equals(name)) return value.matches("[045]");
        if (LIGHT.equals(name) || HANDLE_LIGHT.equals(name)) return value.matches("(?:0,0|1,1)");
        if (CHARGE.equals(name) || POWER.equals(name) || CHARGING_SEPARATION.equals(name)) return value.matches("[01]");
        if (COLOR.equals(name)) return value.matches("#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?,#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?");
        return false;
    }

    private static boolean restorable(String name, String value) {
        if (value == null || allowed(name, value)) return true;
        if (FAN.equals(name)) return value.matches("[0-6]");
        return (LIGHT.equals(name) || HANDLE_LIGHT.equals(name)) && value.matches("[01],[01]");
    }

    public interface Store {
        default String airplane() throws Exception { throw new IOException("Airplane status unavailable"); }
        default void airplane(String value) throws Exception { throw new IOException("Airplane control unavailable"); }
        default String[] fanTelemetry() throws Exception { throw new IOException("Telemetry unavailable"); }
        String get(String key) throws Exception;
        void put(String key, String value) throws Exception;
        default void putAll(Map<String, String> entries) throws Exception {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
        String property() throws Exception;
        void property(String value) throws Exception;
        default void settlePerformance() throws Exception { }
        default void settlePerformance(String expectedObserverFan) throws Exception { settlePerformance(); }
        default boolean fanMatches(String value) throws Exception { return Objects.equals(value, get(FAN)); }
        default void awaitFan(String value) throws Exception {
            if (!Objects.equals(value, get(FAN)) || !fanMatches(value)) throw new ReadbackMismatch();
        }
    }

    public abstract static class CommandStore implements Store {
        protected abstract String command(String... arguments) throws Exception;
        public String airplane() throws Exception {
            String state = command("/system/bin/cmd", "connectivity", "airplane-mode");
            if ("enabled".equals(state)) return "1";
            if ("disabled".equals(state)) return "0";
            throw new IOException("Unknown airplane state");
        }
        public void airplane(String value) throws Exception {
            command("/system/bin/cmd", "connectivity", "airplane-mode", "1".equals(value) ? "enable" : "disable");
        }
        public String[] fanTelemetry() throws Exception {
            return command("/system/bin/cat", "/sys/class/gpio5_pwm2/speed", "/sys/class/gpio5_pwm2/state",
                "/sys/class/gpio5_pwm2/duty", "/sys/class/gpio5_pwm2/period").split("\\s+");
        }
        public String get(String key) throws Exception {
            String value = command("/system/bin/cmd", "settings", "--user", "0", "get", "system", key);
            return "null".equals(value) ? null : value;
        }
        public void put(String key, String value) throws Exception {
            if (value == null) command("/system/bin/cmd", "settings", "--user", "0", "delete", "system", key);
            else command("/system/bin/cmd", "settings", "--user", "0", "put", "system", key, value);
        }
        public void putAll(Map<String, String> entries) throws Exception {
            if (entries.isEmpty()) return;
            if (entries.size() == 1) {
                Map.Entry<String, String> single = entries.entrySet().iterator().next();
                put(single.getKey(), single.getValue());
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                if (sb.length() > 0) sb.append("; ");
                if (entry.getValue() == null) {
                    sb.append("cmd settings --user 0 delete system ").append(entry.getKey());
                } else {
                    sb.append("cmd settings --user 0 put system ").append(entry.getKey()).append(" ").append(entry.getValue());
                }
            }
            command("/system/bin/sh", "-c", sb.toString());
        }
        public String property() throws Exception { return command("/system/bin/getprop", PROPERTY); }
        public void property(String value) throws Exception { command("/system/bin/setprop", PROPERTY, value); }
        public void settlePerformance(String expectedObserverFan) throws Exception {
            if (expectedObserverFan == null) return;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < deadline) {
                if (expectedObserverFan.equals(get(FAN))) return;
                Thread.sleep(50);
            }
            throw new ReadbackMismatch();
        }
        public boolean fanMatches(String mode) throws Exception {
            if (!mode.matches("[045]")) return true; // Other OEM presets are reported as configured.
            String[] values = command("/system/bin/cat", "/sys/class/gpio5_pwm2/state",
                "/sys/class/gpio5_pwm2/duty", "/sys/class/gpio5_pwm2/period").split("\\s+");
            if (values.length != 3) return false;
            if ("0".equals(mode)) return "0".equals(values[0]) && "0".equals(values[1]);
            if (!"1".equals(values[0]) || !"50000".equals(values[2])) return false;
            // OEM SMART is a software thermal loop and may legitimately request zero duty.
            long duty;
            try { duty = Long.parseLong(values[1]); } catch (NumberFormatException invalid) { return false; }
            return "4".equals(mode) ? duty >= 0 && duty <= 50000 : duty == 25000;
        }
        public void awaitFan(String mode) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            int consecutive = 0;
            while (System.nanoTime() < deadline) {
                if (mode.equals(get(FAN)) && fanMatches(mode)) {
                    if (++consecutive == 3) return;
                } else consecutive = 0;
                Thread.sleep(75);
            }
            throw new ReadbackMismatch();
        }
    }

    private static final class ReadbackMismatch extends IOException { }
}
