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
        LinkedHashMap<String, String> changes = new LinkedHashMap<>();
        String property = null;
        String reply;
        if (parts.length == 3 && "SET".equals(parts[0]) && !PERFORMANCE.equals(parts[1]) && allowed(parts[1], parts[2])) {
            changes.put(parts[1], parts[2]);
            reply = parts[1] + "\t" + parts[2];
        } else if (parts.length == 2 && "PERFORMANCE".equals(parts[0]) && parts[1].matches("[012]")) {
            changes.put(PERFORMANCE, parts[1]);
            property = parts[1];
            reply = "PERFORMANCE\t" + parts[1];
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
        boolean propertyAttempted = false;
        try {
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
            for (Map.Entry<String, String> entry : changes.entrySet()) store.put(entry.getKey(), entry.getValue());
            if (property != null) {
                propertyAttempted = true;
                store.property(property);
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
                if (propertyAttempted) {
                    try { store.property(oldProperty); restored &= oldProperty.equals(store.property()); }
                    catch (Exception ignored) { restored = false; }
                }
                List<String> names = new ArrayList<>(previous.keySet());
                Collections.reverse(names);
                for (String name : names) {
                    try {
                        store.put(name, previous.get(name));
                        restored &= Objects.equals(previous.get(name), store.get(name));
                    } catch (Exception ignored) { restored = false; }
                }
            }
            return "ERR\t" + (!restored ? "ROLLBACK_INCOMPLETE" :
                failure instanceof ReadbackMismatch ? "READBACK_MISMATCH" : "WRITE_REJECTED");
        }
    }

    static boolean allowed(String name, String value) {
        if (PERFORMANCE.equals(name)) return value.matches("[012]");
        if (FAN.equals(name)) return value.matches("[045]");
        if (LIGHT.equals(name) || HANDLE_LIGHT.equals(name)) return value.matches("(?:0,0|1,1)");
        if (CHARGE.equals(name) || POWER.equals(name)) return value.matches("[01]");
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
        String property() throws Exception;
        void property(String value) throws Exception;
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
        public String property() throws Exception { return run("/system/bin/getprop", PROPERTY); }
        public void property(String value) throws Exception { run("/system/bin/setprop", PROPERTY, value); }
    }

    private static String run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Command timeout");
            }
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
