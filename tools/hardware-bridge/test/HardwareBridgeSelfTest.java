package com.odin.hardware;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Exercises rejection and partial-write recovery without an Android device or shell commands. */
public final class HardwareBridgeSelfTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--timeout-child".equals(args[0])) {
            Thread.sleep(5000);
            return;
        }
        normalOperations();
        performanceFanCoupling();
        performanceRetainsSelectedFan();
        quietFanProtocol();
        quietRollbackRestoration();
        observerOverwriteAndFanRollback();
        performanceReadOnly();
        fanTelemetry();
        rejectUnknownOperations();
        partialChargeRollback();
        incompleteRollbackIsReported();
        propertyReadbackRollback();
        propertyPartialFailureRollback();
        rejectedSnapshotDoesNotWrite();
        protocolPrimitives();
        nativeProtocol();
        airplaneControl();
        orientationControl();
        commandDeadline();
        System.out.println("Hardware bridge self-test passed (" + checks + " checks).");
    }

    private static void orientationControl() throws Exception {
        class RotationCommands implements OrientationOperations.Commands {
            final Map<String, String> settings = new LinkedHashMap<>();
            final Map<String, String> properties = new LinkedHashMap<>();
            String fixed = "default", size = "Physical size: 1080x1920 Override size: 900x1600";
            String reapplied = null, failProperty = null;
            int writes;
            RotationCommands() {
                settings.put("force_landscape", "1"); settings.put("accelerometer_rotation", "1");
                settings.put("user_rotation", "3");
                properties.put("persist.demo.rotationlock", "");
                properties.put("persist.demo.remoterotation", "");
            }
            public String run(String... args) throws Exception {
                // Every command must fit the firmware transport and remain safely quoted.
                OemCommandCodec.encode(args);
                switch (args[0]) {
                    case "/system/bin/settings":
                        if ("get".equals(args[3])) return settings.getOrDefault(args[5], "null");
                        writes++;
                        if ("delete".equals(args[3])) settings.remove(args[5]);
                        else settings.put(args[5], args[6]);
                        return "";
                    case "/system/bin/getprop": return properties.get(args[1]);
                    case "/system/bin/setprop":
                        writes++;
                        properties.put(args[1], args[2]); // Exercise a partial write then failure.
                        if (args[1].equals(failProperty)) { failProperty = null; throw new IOException("rejected"); }
                        return "";
                    case "/system/bin/wm":
                        if ("size".equals(args[1])) {
                            if (args.length == 2) return size;
                            reapplied = args[2]; writes++; return "";
                        }
                        if (args.length == 2) return fixed;
                        fixed = args[2]; writes++; return "";
                    default: throw new AssertionError("Unexpected command " + Arrays.toString(args));
                }
            }
        }
        RotationCommands state = new RotationCommands();
        OrientationOperations operations = new OrientationOperations(state);
        equal("OK\tORIENTATION\t0", operations.apply("0"));
        equal("0", state.settings.get("force_landscape"));
        equal("0", state.settings.get("accelerometer_rotation"));
        equal("1", state.settings.get("user_rotation"));
        equal("true", state.properties.get("persist.demo.rotationlock"));
        equal("landscape", state.properties.get("persist.demo.remoterotation"));
        equal("disabled", state.fixed);
        equal("900x1600", state.reapplied); // Custom display size must survive.
        equal("OK\tORIENTATION\t1", operations.apply("1"));
        equal("false", state.properties.get("persist.demo.rotationlock"));
        equal("1", state.settings.get("accelerometer_rotation"));
        equal("reset", OrientationOperations.existingSize("Physical size: 1080x1920"));
        state = new RotationCommands();
        Map<String, String> old = new LinkedHashMap<>(state.settings);
        state.failProperty = "persist.demo.rotationlock";
        equal("ERR\tWRITE_REJECTED", new OrientationOperations(state).apply("0"));
        equal(old, state.settings);
        equal("", state.properties.get("persist.demo.rotationlock"));
        equal("", state.properties.get("persist.demo.remoterotation"));
        equal("default", state.fixed);
        state = new RotationCommands(); state.size = "unavailable";
        equal("ERR\tREAD_UNAVAILABLE", new OrientationOperations(state).apply("0"));
        equal(0, state.writes);
        equal("ERR\tBAD_REQUEST", new OrientationOperations(state).apply("0; reboot"));
        equal("ERR\tBAD_REQUEST", new HardwareOperations(new MemoryStore()).execute("ORIENTATION\t2"));
    }

    private static void commandDeadline() throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        equal(true, OdinHardwareBridge.run(java, "-version").contains("version"));
        long start = System.nanoTime();
        try {
            OdinHardwareBridge.run(java, "-cp", System.getProperty("java.class.path"),
                HardwareBridgeSelfTest.class.getName(), "--timeout-child");
            throw new AssertionError("Hung command was not terminated");
        } catch (IOException expected) {
            long millis = (System.nanoTime() - start) / 1_000_000;
            equal(true, millis >= 1800 && millis < 4500);
        }
    }

    private static void fanTelemetry() {
        final String[][] reading = { { "5700", "1", "11500", "50000" } };
        MemoryStore store = new MemoryStore() {
            @Override public String[] fanTelemetry() { return reading[0]; }
        };
        HardwareOperations operations = new HardwareOperations(store);
        equal("OK\tFAN_TELEMETRY\t5700\t23", operations.execute("FAN_TELEMETRY"));
        reading[0] = new String[] { "0", "0", "0", "50000" };
        equal("OK\tFAN_TELEMETRY\t0\t0", operations.execute("FAN_TELEMETRY"));
        for (String[] bad : new String[][] {
                null, { "0" }, { "", "1", "11500", "50000" },
                { "5700", "1", "11500", "0" }, { "5700", "1", "50001", "50000" },
                { "5700", "2", "11500", "50000" }, { "-1", "1", "11500", "50000" },
                { "9999999", "1", "11500", "50000" } }) {
            reading[0] = bad;
            equal("ERR\tREAD_UNAVAILABLE", operations.execute("FAN_TELEMETRY"));
        }
        equal("ERR\tREAD_UNAVAILABLE", new HardwareOperations(new MemoryStore()).execute("FAN_TELEMETRY"));
        equal("ERR\tBAD_REQUEST", operations.execute("FAN_TELEMETRY\tanything"));
        equal(0, store.writes);
    }

    private static void nativeProtocol() throws Exception {
        // Use a real host shell to exercise quoting and the OEM's first-line response behavior.
        String literal = "red'; printf INJECTED; '";
        String command = OemCommandCodec.encode("/usr/bin/printf", "%s\\n", literal, "second")
            .replace("/system/bin/timeout 2 ", "").replace("/system/bin/tr", "/usr/bin/tr");
        String response = OdinHardwareBridge.run("/bin/sh", "-c", command);
        equal(false, response.contains("\n"));
        equal(literal + " second", OemCommandCodec.decode(response.getBytes(StandardCharsets.UTF_8)));
        for (String bad : Arrays.asList("", "0", "ODIN:1 denied", "ODIN:124 timed out", "ODIN:0")) {
            try { OemCommandCodec.decode(bad.getBytes(StandardCharsets.UTF_8)); throw new AssertionError("Bad status accepted"); }
            catch (IOException expected) { checks++; }
        }
        for (byte[] bad : new byte[][] { null, new byte[4097] }) {
            try { OemCommandCodec.decode(bad); throw new AssertionError("Bad response size accepted"); }
            catch (IOException expected) { checks++; }
        }
        try {
            OemCommandCodec.encode(String.join("", Collections.nCopies(256, "x")));
            throw new AssertionError("Truncated command accepted");
        } catch (IOException expected) { checks++; }
        equal(true, OemCommandCodec.encode("/system/bin/cat", "/sys/class/gpio5_pwm2/speed",
            "/sys/class/gpio5_pwm2/state", "/sys/class/gpio5_pwm2/duty", "/sys/class/gpio5_pwm2/period").length() <= 255);
        equal(true, OemCommandCodec.encode("/system/bin/cmd", "settings", "--user", "0", "put", "system",
            "joystick_led_light_picker_color", "#ff7c4dff,#ff7c4dff").length() <= 255);
    }

    private static void airplaneControl() {
        final String[] state = { "0" };
        final boolean[] reject = { false };
        MemoryStore store = new MemoryStore() {
            @Override public String airplane() { return state[0]; }
            @Override public void airplane(String value) throws IOException {
                state[0] = value;
                if (reject[0] && "1".equals(value)) throw new IOException("Partial airplane write");
            }
        };
        HardwareOperations operations = new HardwareOperations(store);
        equal("OK\tAIRPLANE\t1", operations.execute("AIRPLANE\t1"));
        equal("1", state[0]);
        equal("OK\tAIRPLANE\t0", operations.execute("AIRPLANE\t0"));
        reject[0] = true;
        equal("ERR\tWRITE_REJECTED", operations.execute("AIRPLANE\t1"));
        equal("0", state[0]);
        equal("ERR\tBAD_REQUEST", operations.execute("AIRPLANE\tenable;anything"));
        equal("ERR\tREAD_UNAVAILABLE", new HardwareOperations(new MemoryStore()).execute("AIRPLANE\t1"));
    }

    private static void performanceFanCoupling() {
        // OEM performance observers can reset PWM even while fan_mode still says MAX.
        MemoryStore store = new MemoryStore() {
            String output = "5";
            @Override public void property(String value) throws Exception {
                super.property(value);
                output = "0";
            }
            @Override public void put(String name, String value) throws Exception {
                String previous = values.get(name);
                super.put(name, value);
                if (OdinHardwareBridge.FAN.equals(name) && !Objects.equals(previous, value)) output = value;
            }
            public boolean fanMatches(String value) { return value.equals(output); }
        };
        store.values.put(OdinHardwareBridge.FAN, "5");
        OdinHardwareBridge bridge = bridge(store);
        for (String perf : Arrays.asList("1", "2", "0", "2", "1", "0")) {
            equal("OK\tPERFORMANCE\t" + perf, bridge.execute("PERFORMANCE\t" + perf));
            equal("5", store.values.get(OdinHardwareBridge.FAN));
            equal(true, store.fanMatches("5"));
        }
        equal("OK\tPERFORMANCE_FAN\t0\t4", bridge.execute("PERFORMANCE_FAN\t0\t4"));
        equal("OK\tFAN\t4", bridge.execute("FAN_GET"));
        equal("OK\tPERFORMANCE\t1", bridge.execute("PERFORMANCE\t1"));
        equal("4", store.values.get(OdinHardwareBridge.FAN));
        equal("OK\tPERFORMANCE\t0", bridge.execute("PERFORMANCE\t0"));
        equal("4", store.values.get(OdinHardwareBridge.FAN));
    }

    private static void performanceRetainsSelectedFan() throws Exception {
        // Requirement 2026-09-18: performance switching preserves the user's selected fan.
        // ObserverStore emulates the OEM stack faithfully: every performance_mode *change*
        // schedules exactly one SystemUI observer firing which, when it runs, rewrites
        // fan_mode from the value it reads at that moment (NORMAL->0; STANDARD->1 unless
        // the key already says SMART; HIGH->5 from OFF/QUIET). Firings land after a
        // configurable number of fan reads: during the bounded settle wait, after it, or
        // never within the window.
        for (String fan : Arrays.asList("0", "1", "4", "5")) {
            for (String performance : Arrays.asList("0", "1", "2")) {
                ObserverStore store = new ObserverStore(3);
                store.seed(rotate(performance), "0".equals(fan) ? "4" : "0");
                equal("OK\tPERFORMANCE_FAN\t" + performance + "\t" + fan,
                    bridge(store).execute("PERFORMANCE_FAN\t" + performance + "\t" + fan));
                equal(fan, store.values.get(OdinHardwareBridge.FAN));
                equal(performance, store.mode);
                equal(performance, store.values.get(OdinHardwareBridge.PERFORMANCE));
            }
        }
        for (String fan : Arrays.asList("0", "1", "4", "5")) {
            for (String performance : Arrays.asList("0", "1", "2")) {
                // A plain PERFORMANCE request keeps the configured fan instead of defaulting it.
                ObserverStore store = new ObserverStore(3);
                store.seed(rotate(performance), fan);
                equal("OK\tPERFORMANCE\t" + performance, bridge(store).execute("PERFORMANCE\t" + performance));
                equal(fan, store.values.get(OdinHardwareBridge.FAN));
                equal(performance, store.mode);
            }
        }
        // All six directed performance transitions retain each fan mode under varied,
        // realistic observer latencies (immediate, early, and late within the window).
        for (String[] edge : new String[][] {{"0", "1"}, {"0", "2"}, {"1", "0"}, {"1", "2"}, {"2", "0"}, {"2", "1"}}) {
            for (String fan : Arrays.asList("0", "1", "4", "5")) {
                for (int delay : new int[] {0, 1, 5}) {
                    ObserverStore store = new ObserverStore(delay);
                    store.seed(edge[0], "0".equals(fan) ? "4" : "0");
                    equal("OK\tPERFORMANCE_FAN\t" + edge[1] + "\t" + fan,
                        bridge(store).execute("PERFORMANCE_FAN\t" + edge[1] + "\t" + fan));
                    equal(fan, store.values.get(OdinHardwareBridge.FAN));
                    equal(edge[1], store.mode);
                }
            }
        }
        // A same-value performance change notifies no observer: a pure fan change.
        ObserverStore same = new ObserverStore(0);
        same.seed("1", "4");
        equal("OK\tPERFORMANCE_FAN\t1\t0", bridge(same).execute("PERFORMANCE_FAN\t1\t0"));
        equal("0", same.values.get(OdinHardwareBridge.FAN));
        equal("1", same.mode);
        // An observer firing later than the bounded settle window must fail the
        // transaction, never silently succeed; inputs are restored afterwards.
        ObserverStore late = new ObserverStore(100000);
        late.seed("0", "4");
        equal("ERR\tROLLBACK_INCOMPLETE", bridge(late).execute("PERFORMANCE_FAN\t2\t1"));
        late.drain();
        equal("4", late.values.get(OdinHardwareBridge.FAN));
        equal("0", late.values.get(OdinHardwareBridge.PERFORMANCE));
        equal("0", late.mode);
        ObserverStore lateStandard = new ObserverStore(100000);
        lateStandard.seed("2", "5");
        equal("ERR\tWRITE_REJECTED", bridge(lateStandard).execute("PERFORMANCE_FAN\t1\t0"));
        lateStandard.drain();
        equal("5", lateStandard.values.get(OdinHardwareBridge.FAN));
        equal("2", lateStandard.values.get(OdinHardwareBridge.PERFORMANCE));
        equal("2", lateStandard.mode);
        // An unrepresentable configured fan is rejected before any write.
        ObserverStore invalid = new ObserverStore(0);
        invalid.seed("0", "6");
        equal("ERR\tBAD_REQUEST", bridge(invalid).execute("PERFORMANCE\t1"));
        equal(0, invalid.writes);
    }

    private static String rotate(String performance) {
        return String.valueOf((Integer.parseInt(performance) + 1) % 3);
    }

    /** Emulates Settings notify -> SystemUI performance observer -> asynchronous fan write. */
    private static final class ObserverStore extends MemoryStore {
        private final ArrayDeque<Object[]> firings = new ArrayDeque<>(); // {countdown, performance cause}
        private final int delay;
        ObserverStore(int delay) { this.delay = delay; }
        void seed(String performance, String fan) {
            mode = performance;
            values.put(OdinHardwareBridge.PERFORMANCE, performance);
            values.put(OdinHardwareBridge.FAN, fan);
        }
        void drain() { firings.clear(); }
        @Override public void put(String name, String value) throws Exception {
            String previous = values.get(name);
            super.put(name, value);
            // Same-value Settings puts do not notify the observer.
            if (OdinHardwareBridge.PERFORMANCE.equals(name) && !Objects.equals(previous, value))
                firings.add(new Object[] {delay, value});
        }
        @Override public String get(String name) {
            if (OdinHardwareBridge.FAN.equals(name) && !firings.isEmpty()) {
                for (Object[] firing : firings) {
                    int countdown = (Integer) firing[0];
                    if (countdown > 0) firing[0] = countdown - 1;
                }
                for (Iterator<Object[]> it = firings.iterator(); it.hasNext(); ) {
                    Object[] firing = it.next();
                    if ((Integer) firing[0] == 0) {
                        it.remove();
                        String performance = (String) firing[1];
                        String current = values.get(OdinHardwareBridge.FAN);
                        if ("0".equals(performance)) values.put(OdinHardwareBridge.FAN, "0");
                        else if ("1".equals(performance)) { if (!"4".equals(current)) values.put(OdinHardwareBridge.FAN, "1"); }
                        else if ("2".equals(performance)) { if ("0".equals(current) || "1".equals(current)) values.put(OdinHardwareBridge.FAN, "5"); }
                        break;
                    }
                }
            }
            return super.get(name);
        }
        @Override public void settlePerformance(String expected) throws Exception {
            // CommandStore-like bounded acknowledgement wait, shortened for the JVM test.
            if (expected == null) return;
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);
            while (System.nanoTime() < deadline) {
                if (expected.equals(get(OdinHardwareBridge.FAN))) return;
                Thread.sleep(10);
            }
            throw new IOException("observer acknowledgement not observed within the window");
        }
    }

    private static void quietFanProtocol() {
        // The dock's four-gear cycle writes the OEM QUIET preset (1). Its PWM signature
        // is not guessed; transactions verify the configured mode, and the rollback test
        // below restores it through the verified fan path.
        MemoryStore store = new MemoryStore();
        OdinHardwareBridge bridge = bridge(store);
        for (String mode : Arrays.asList("1", "4", "1", "0", "1", "5", "1")) {
            equal("OK\tfan_mode\t" + mode, bridge.execute("SET\tfan_mode\t" + mode));
            equal("OK\tFAN\t" + mode, bridge.execute("FAN_GET"));
        }
        // The OEM observer pairs each performance mode with its own fan default. Combined
        // transactions retain the requested fan through those rewrites.
        MemoryStore coupled = new MemoryStore() {
            @Override public void settlePerformance() {
                // Emulate SystemUI: normal forces OFF, STANDARD forces QUIET unless SMART,
                // HIGH forces MAX from OFF/QUIET.
                if ("0".equals(mode)) values.put(OdinHardwareBridge.FAN, "0");
                else if ("1".equals(mode) && !"4".equals(values.get(OdinHardwareBridge.FAN)))
                    values.put(OdinHardwareBridge.FAN, "1");
                else if ("2".equals(mode)) {
                    String current = values.get(OdinHardwareBridge.FAN);
                    if ("0".equals(current) || "1".equals(current)) values.put(OdinHardwareBridge.FAN, "5");
                }
            }
        };
        coupled.mode = "2";
        coupled.values.put(OdinHardwareBridge.PERFORMANCE, "2");
        coupled.values.put(OdinHardwareBridge.FAN, "5");
        equal("OK\tPERFORMANCE_FAN\t0\t1", bridge(coupled).execute("PERFORMANCE_FAN\t0\t1"));
        equal("0", coupled.mode);
        equal("1", coupled.values.get(OdinHardwareBridge.FAN));
        equal("OK\tPERFORMANCE_FAN\t1\t1", bridge(coupled).execute("PERFORMANCE_FAN\t1\t1"));
        equal("1", coupled.values.get(OdinHardwareBridge.FAN));
        // High performance used to reject QUIET because the observer rewrites it to MAX;
        // the acknowledged transaction now restores the retained QUIET selection.
        equal("OK\tPERFORMANCE_FAN\t2\t1", bridge(coupled).execute("PERFORMANCE_FAN\t2\t1"));
        equal("2", coupled.mode);
        equal("1", coupled.values.get(OdinHardwareBridge.FAN));
        equal("OK\tPERFORMANCE_FAN\t1\t0", bridge(coupled).execute("PERFORMANCE_FAN\t1\t0"));
        equal("0", coupled.values.get(OdinHardwareBridge.FAN));
        // Values outside the OEM presets are still rejected before any write.
        MemoryStore rejected = new MemoryStore();
        equal("ERR\tBAD_REQUEST", bridge(rejected).execute("PERFORMANCE_FAN\t2\t6"));
        equal(0, rejected.writes);
    }

    private static void quietRollbackRestoration() {
        // A failed combined transaction must restore a previously configured QUIET fan
        // through the verified fan path (awaitFan), never a bare Settings write.
        List<String> awaited = new ArrayList<>();
        MemoryStore store = new MemoryStore() {
            @Override public void property(String value) throws Exception {
                mode = value;
                if ("1".equals(value)) throw new IOException("property failed after mirror write");
            }
            @Override public void awaitFan(String value) throws Exception {
                awaited.add(value);
                if (!Objects.equals(value, get(OdinHardwareBridge.FAN)) || !fanMatches(value))
                    throw new IOException("fan readback mismatch");
            }
        };
        store.values.put(OdinHardwareBridge.FAN, "1");
        equal("ERR\tWRITE_REJECTED", bridge(store).execute("PERFORMANCE_FAN\t1\t4"));
        equal("0", store.mode);
        equal("0", store.values.get(OdinHardwareBridge.PERFORMANCE));
        equal("1", store.values.get(OdinHardwareBridge.FAN));
        equal(true, awaited.contains("1"));
    }

    private static void observerOverwriteAndFanRollback() {
        MemoryStore observer = new MemoryStore() {
            @Override public void settlePerformance() {
                values.put(OdinHardwareBridge.FAN, "0".equals(mode) ? "0" : "1");
            }
        };
        observer.values.put(OdinHardwareBridge.FAN, "5");
        equal("OK\tPERFORMANCE_FAN\t0\t5", bridge(observer).execute("PERFORMANCE_FAN\t0\t5"));
        equal("5", observer.values.get(OdinHardwareBridge.FAN));

        MemoryStore rejected = new MemoryStore() {
            @Override public void put(String name, String value) throws Exception {
                super.put(name, value);
                if (OdinHardwareBridge.PERFORMANCE.equals(name)) values.put(OdinHardwareBridge.FAN, "0");
            }
            @Override public void awaitFan(String value) throws Exception {
                if ("5".equals(value)) throw new IOException("driver did not apply request");
            }
        };
        rejected.values.put(OdinHardwareBridge.FAN, "4");
        equal("ERR\tWRITE_REJECTED", bridge(rejected).execute("PERFORMANCE_FAN\t2\t5"));
        equal("0", rejected.mode);
        equal("0", rejected.values.get(OdinHardwareBridge.PERFORMANCE));
        equal("4", rejected.values.get(OdinHardwareBridge.FAN));

        MemoryStore mismatch = new MemoryStore() {
            @Override public boolean fanMatches(String value) { return false; }
        };
        equal("ERR\tREAD_UNAVAILABLE", bridge(mismatch).execute("FAN_GET"));
        equal(0, mismatch.writes);
    }

    private static void normalOperations() {
        MemoryStore store = new MemoryStore();
        OdinHardwareBridge bridge = bridge(store);
        equal("OK\tREADY", bridge.execute("PING"));
        equal("OK\tPERFORMANCE\t2", bridge.execute("PERFORMANCE\t2"));
        equal("2", store.mode); equal("2", store.values.get(OdinHardwareBridge.PERFORMANCE));
        for (String mode : Arrays.asList("0", "4", "5")) {
            equal("OK\tfan_mode\t" + mode, bridge.execute("SET\tfan_mode\t" + mode));
        }
        equal("OK\tCHARGE\t1", bridge.execute("CHARGE\t1"));
        equal("1", store.values.get(OdinHardwareBridge.CHARGE));
        equal("1", store.values.get(OdinHardwareBridge.POWER));
        equal("OK\tis_charging_separation\t1", bridge.execute("SET\tis_charging_separation\t1"));
        equal("1", store.values.get(OdinHardwareBridge.CHARGING_SEPARATION));
        equal("OK\tcharging_limit_power_limit\t0", bridge.execute("SET\tcharging_limit_power_limit\t0"));
        equal("0", store.values.get(OdinHardwareBridge.POWER));
        equal("OK\tLIGHTS\t1,1", bridge.execute("LIGHTS\t1,1"));
        equal("1,1", store.values.get(OdinHardwareBridge.LIGHT));
        equal("1,1", store.values.get(OdinHardwareBridge.HANDLE_LIGHT));
        equal("OK\tjoystick_led_light_picker_color\t#ff00ff,#ff00aaee",
            bridge.execute("SET\tjoystick_led_light_picker_color\t#ff00ff,#ff00aaee"));
    }

    private static void rejectUnknownOperations() {
        MemoryStore store = new MemoryStore();
        OdinHardwareBridge bridge = bridge(store);
        String[] invalid = {
            "SET\tperformance_mode\t1", "PERFORMANCE\t3", "PERFORMANCE\t-1",
            "PERFORMANCE_FAN\t3\t0", "PERFORMANCE_FAN\t0\t6", "FAN_GET\tfan_mode",
            "SET\tfan_mode\t6", "SET\tfan_mode\t2", "SET\tfan_mode\t3", "SET\tfan_mode\t5;id",
            "SET\tairplane_mode_on\t1", "SET\t../token\t1", "SETPROP\tother\t1",
            "FORCE_STOP\tcom.example", "CMD\tid", "CHARGE\t2", "LIGHTS\t1,0",
            "SET\tjoystick_led_light_picker_color\tred,red", "SET\tfan_mode\t4\nPING",
            "PING\textra", "STOP\textra", "SET\tfan_mode\t4\tignored",
            "PERFORMANCE_GET\tpersist.vendor.debug.mode", "PERFORMANCE_GET\t0", "GETPROP\tpersist.vendor.debug.mode"
        };
        for (String command : invalid) equal("ERR\tBAD_REQUEST", bridge.execute(command));
        equal(0, store.writes);
    }

    private static void performanceReadOnly() {
        MemoryStore store = new MemoryStore();
        OdinHardwareBridge bridge = bridge(store);
        for (String value : Arrays.asList("0", "1", "2")) {
            store.mode = value;
            equal("OK\tPERFORMANCE\t" + value, bridge.execute("PERFORMANCE_GET"));
        }
        // A stale System mirror never substitutes for the actual active property.
        equal("0", store.values.get(OdinHardwareBridge.PERFORMANCE));
        for (String value : Arrays.asList("", "3", "-1", "unavailable")) {
            store.mode = value;
            equal("ERR\tREAD_UNAVAILABLE", bridge.execute("PERFORMANCE_GET"));
        }
        equal(0, store.writes);
        MemoryStore rejected = new MemoryStore() {
            @Override public String property() { throw new IllegalStateException("simulated read rejection"); }
        };
        equal("ERR\tREAD_UNAVAILABLE", bridge(rejected).execute("PERFORMANCE_GET"));
        equal(0, rejected.writes);
    }

    private static void partialChargeRollback() {
        MemoryStore store = new MemoryStore() {
            boolean failed;
            @Override public void put(String name, String value) throws Exception {
                if (OdinHardwareBridge.POWER.equals(name) && "1".equals(value) && !failed) {
                    failed = true; throw new IOException("simulated rejection");
                }
                super.put(name, value);
            }
        };
        equal("ERR\tWRITE_REJECTED", bridge(store).execute("CHARGE\t1"));
        equal("0", store.values.get(OdinHardwareBridge.CHARGE));
        equal("0", store.values.get(OdinHardwareBridge.POWER));
    }

    private static void incompleteRollbackIsReported() {
        MemoryStore store = new MemoryStore() {
            @Override public void put(String name, String value) throws Exception {
                if ((OdinHardwareBridge.POWER.equals(name) && "1".equals(value)) ||
                    (OdinHardwareBridge.CHARGE.equals(name) && "0".equals(value))) {
                    throw new IOException("simulated rejection");
                }
                super.put(name, value);
            }
        };
        equal("ERR\tROLLBACK_INCOMPLETE", bridge(store).execute("CHARGE\t1"));
        equal("1", store.values.get(OdinHardwareBridge.CHARGE));
        equal("0", store.values.get(OdinHardwareBridge.POWER));
    }

    private static void propertyReadbackRollback() {
        MemoryStore store = new MemoryStore() {
            @Override public void property(String value) {
                // Setter returns successfully, but the selected mode is not applied.
            }
        };
        equal("ERR\tREADBACK_MISMATCH", bridge(store).execute("PERFORMANCE\t1"));
        equal("0", store.mode);
        equal("0", store.values.get(OdinHardwareBridge.PERFORMANCE));
    }

    private static void propertyPartialFailureRollback() {
        MemoryStore store = new MemoryStore() {
            @Override public void property(String value) throws Exception {
                mode = value;
                if ("1".equals(value)) throw new IOException("write happened before failure");
            }
        };
        equal("ERR\tWRITE_REJECTED", bridge(store).execute("PERFORMANCE\t1"));
        equal("0", store.mode);
        equal("0", store.values.get(OdinHardwareBridge.PERFORMANCE));
    }

    private static void rejectedSnapshotDoesNotWrite() {
        MemoryStore store = new MemoryStore();
        store.values.put(OdinHardwareBridge.FAN, "unrecognized");
        equal("ERR\tWRITE_REJECTED", bridge(store).execute("SET\tfan_mode\t4"));
        equal(0, store.writes);
        store.values.put(OdinHardwareBridge.FAN, "6");
        equal("OK\tfan_mode\t4", bridge(store).execute("SET\tfan_mode\t4"));
    }

    private static void protocolPrimitives() throws Exception {
        byte[] key = new byte[20]; Arrays.fill(key, (byte) 0x0b);
        equal("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            OdinHardwareBridge.mac(key, "Hi There"));
        equal(false, OdinHardwareBridge.equal("abcd", "abce"));
        equal(false, OdinHardwareBridge.equal("abcd", "abc"));
        equal("PING\tvalue", OdinHardwareBridge.readLine(new ByteArrayInputStream("PING\tvalue\n".getBytes(StandardCharsets.US_ASCII))));
        try {
            OdinHardwareBridge.readLine(new ByteArrayInputStream(new byte[1025]));
            throw new AssertionError("Binary protocol input was accepted");
        } catch (IOException expected) { checks++; }
        byte[] oversized = new byte[1025]; Arrays.fill(oversized, (byte) 'x');
        try {
            OdinHardwareBridge.readLine(new ByteArrayInputStream(oversized));
            throw new AssertionError("Oversized protocol input was accepted");
        } catch (IOException expected) { checks++; }
    }

    private static OdinHardwareBridge bridge(MemoryStore store) {
        return new OdinHardwareBridge(new byte[32], store);
    }
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
        checks++;
    }
    private static class MemoryStore implements OdinHardwareBridge.Store {
        final Map<String, String> values = new HashMap<>();
        String mode = "0";
        int writes;
        MemoryStore() {
            for (String name : Arrays.asList(OdinHardwareBridge.PERFORMANCE, OdinHardwareBridge.FAN,
                    OdinHardwareBridge.CHARGE, OdinHardwareBridge.POWER, OdinHardwareBridge.CHARGING_SEPARATION)) values.put(name, "0");
            values.put(OdinHardwareBridge.LIGHT, "0,0");
            values.put(OdinHardwareBridge.HANDLE_LIGHT, "0,0");
        }
        public String get(String name) { return values.get(name); }
        public void put(String name, String value) throws Exception {
            writes++;
            if (value == null) values.remove(name); else values.put(name, value);
        }
        public String property() { return mode; }
        public void property(String value) throws Exception { mode = value; writes++; }
        public boolean fanMatches(String value) { return Objects.equals(value, values.get(OdinHardwareBridge.FAN)); }
    }
}
