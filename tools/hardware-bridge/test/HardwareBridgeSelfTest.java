package com.odin.hardware;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
        commandDeadline();
        System.out.println("Hardware bridge self-test passed (" + checks + " checks).");
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
        equal("0", store.values.get(OdinHardwareBridge.FAN));
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
            "PERFORMANCE_FAN\t1\t0", "PERFORMANCE_FAN\t2\t0", "PERFORMANCE_FAN\t0\t6", "FAN_GET\tfan_mode",
            "SET\tfan_mode\t6", "SET\tfan_mode\t1", "SET\tfan_mode\t5;id",
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
