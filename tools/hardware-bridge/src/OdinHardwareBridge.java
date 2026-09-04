package com.odin.hardware;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Shell UID only. No arbitrary commands, paths, properties, packages, or setting namespaces. */
public final class OdinHardwareBridge {
    static final int PORT = 18889;
    static final String PERFORMANCE = "performance_mode";
    static final String PROPERTY = "persist.vendor.debug.mode";
    static final String FAN = "fan_mode";
    static final String LIGHT = "joystick_light_enabled";
    static final String HANDLE_LIGHT = "joystick_handle_light_enabled";
    static final String COLOR = "joystick_led_light_picker_color";
    static final String CHARGE = "percent_80_charge_limit";
    static final String POWER = "charging_limit_power_limit";
    static final String CHARGING_SEPARATION = "is_charging_separation";
    private static final ScheduledExecutorService COMMAND_TIMEOUTS = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "odin-command-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private final byte[] key;
    private final Store store;
    private final Object transaction = new Object();
    private final SecureRandom random = new SecureRandom();
    private volatile boolean running = true;
    private ServerSocket server;

    OdinHardwareBridge(byte[] key, Store store) { this.key = key.clone(); this.store = store; }

    public static void main(String[] args) {
        try {
            if (args.length != 1 || !"2000".equals(run("/system/bin/id", "-u"))) {
                throw new IOException("Shell UID required");
            }
            Path file = Paths.get(args[0]);
            if (!file.toString().equals("/data/local/tmp/odin-hardware-bridge/token") || Files.isSymbolicLink(file)) {
                throw new IOException("Invalid token path");
            }
            Set<PosixFilePermission> expected = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            if (!Files.getPosixFilePermissions(file).equals(expected)) throw new IOException("Token must be mode 0600");
            Set<PosixFilePermission> directoryMode = EnumSet.copyOf(expected);
            directoryMode.add(PosixFilePermission.OWNER_EXECUTE);
            if (Files.isSymbolicLink(file.getParent()) || !Files.getPosixFilePermissions(file.getParent()).equals(directoryMode)) {
                throw new IOException("Token directory must be mode 0700");
            }
            if (Files.size(file) < 64 || Files.size(file) > 65) throw new IOException("Invalid token length");
            String token = new String(Files.readAllBytes(file), StandardCharsets.US_ASCII).trim();
            if (!token.matches("[0-9a-f]{64}")) throw new IOException("Invalid token");
            new OdinHardwareBridge(unhex(token), new ShellStore()).serve();
        } catch (Exception error) {
            // Never print a command, token, request, or exception payload.
            System.err.println("Hardware bridge stopped: initialization or listener failure.");
            System.exit(1);
        }
    }

    private void serve() throws IOException {
        ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(8));
        try (ServerSocket listener = new ServerSocket()) {
            server = listener;
            listener.bind(new InetSocketAddress("127.0.0.1", PORT));
            System.out.println("Hardware bridge ready on loopback, protocol ODIN1.");
            while (running) {
                final Socket client;
                try { client = listener.accept(); }
                catch (SocketException closed) { if (!running) break; throw closed; }
                try { workers.execute(() -> handle(client)); }
                catch (RejectedExecutionException busy) { client.close(); }
            }
        } finally { workers.shutdownNow(); }
    }

    private void handle(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(2000);
            byte[] salt = new byte[32];
            random.nextBytes(salt);
            String nonce = hex(salt);
            writeLine(socket, "ODIN1\t" + nonce + "\t" + mac(key, "SERVER\n" + nonce));
            String wire = readLine(socket.getInputStream());
            int cut = wire.lastIndexOf('\t');
            String body = cut < 0 ? "" : wire.substring(0, cut);
            String signature = cut < 0 ? "" : wire.substring(cut + 1);
            String result;
            if (!equal(mac(key, "CLIENT\n" + nonce + "\n" + body), signature)) {
                result = "ERR\tAUTH_FAILED";
            } else {
                synchronized (transaction) {
                    result = running ? execute(body) : "ERR\tSTOPPING";
                    if ("OK\tSTOPPED".equals(result)) running = false;
                }
            }
            try {
                writeLine(socket, result + "\t" + mac(key, "RESPONSE\n" + nonce + "\n" + result));
            } finally {
                if ("OK\tSTOPPED".equals(result)) server.close();
            }
        } catch (Exception ignored) { /* A malformed or disconnected client gets no successful acknowledgement. */ }
    }

    String execute(String body) {
        String[] parts = body.split("\t", -1);
        if (body.length() > 384 || body.indexOf('\n') >= 0 || body.indexOf('\r') >= 0) return "ERR\tBAD_REQUEST";
        if (parts.length == 1 && "PING".equals(parts[0])) return "OK\tREADY";
        if (parts.length == 1 && "STOP".equals(parts[0])) return "OK\tSTOPPED";
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

    interface Store {
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

    private static final class ShellStore implements Store {
        public String get(String key) throws Exception {
            String value = run("/system/bin/cmd", "settings", "--user", "0", "get", "system", key);
            return "null".equals(value) ? null : value;
        }
        public void put(String key, String value) throws Exception {
            if (value == null) run("/system/bin/cmd", "settings", "--user", "0", "delete", "system", key);
            else run("/system/bin/cmd", "settings", "--user", "0", "put", "system", key, value);
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
            run("/system/bin/sh", "-c", sb.toString());
        }
        public String property() throws Exception { return run("/system/bin/getprop", PROPERTY); }
        public void property(String value) throws Exception { run("/system/bin/setprop", PROPERTY, value); }
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
            String[] values = run("/system/bin/cat", "/sys/class/gpio5_pwm2/state",
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

    static String run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ScheduledFuture<?> deadline = COMMAND_TIMEOUTS.schedule(process::destroyForcibly, 2, TimeUnit.SECONDS);
        try {
            // Android's timed wait polls at ~100ms intervals, adding that cost to every tiny
            // settings/getprop command. Native wait returns at exit; the separate deadline
            // retains the same two-second limit without quantizing all successful requests.
            process.waitFor();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            InputStream stream = process.getInputStream();
            int b;
            while ((b = stream.read()) != -1) {
                if (output.size() >= 4096) throw new IOException("Oversized command output");
                output.write(b);
            }
            if (process.exitValue() != 0) throw new IOException("Command rejected");
            return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
        } finally {
            deadline.cancel(false);
            process.getInputStream().close(); process.getErrorStream().close(); process.getOutputStream().close();
        }
    }

    private static final class ReadbackMismatch extends IOException { }

    static String mac(byte[] key, String payload) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return hex(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
    }
    static boolean equal(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    }
    static byte[] unhex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) text.append(String.format(Locale.ROOT, "%02x", b & 255));
        return text.toString();
    }
    static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int n = 0; n < 1024; n++) {
            int b = input.read();
            if (b == '\n') return new String(bytes.toByteArray(), StandardCharsets.US_ASCII);
            if (b < 0 || (b != '\t' && (b < 32 || b > 126))) throw new IOException("Invalid request");
            bytes.write(b);
        }
        throw new IOException("Request too long");
    }
    private static void writeLine(Socket socket, String text) throws IOException {
        socket.getOutputStream().write((text + "\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }
}
