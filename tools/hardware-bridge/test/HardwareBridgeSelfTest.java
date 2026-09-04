package com.odin.hardware;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exercises rejection and partial-write recovery without an Android device or shell commands. */
public final class HardwareBridgeSelfTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        normalOperations();
        performanceReadOnly();
        rejectUnknownOperations();
        partialChargeRollback();
        incompleteRollbackIsReported();
        propertyReadbackRollback();
        propertyPartialFailureRollback();
        rejectedSnapshotDoesNotWrite();
        protocolPrimitives();
        System.out.println("Hardware bridge self-test passed (" + checks + " checks).");
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
    }
}
