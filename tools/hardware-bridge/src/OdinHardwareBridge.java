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
public final class OdinHardwareBridge extends HardwareOperations {
    static final int PORT = 18889;
    private static final ScheduledExecutorService COMMAND_TIMEOUTS = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "odin-command-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private final byte[] key;
    private final Object transaction = new Object();
    private final SecureRandom random = new SecureRandom();
    private volatile boolean running = true;
    private ServerSocket server;

    OdinHardwareBridge(byte[] key, Store store) { super(store); this.key = key.clone(); }

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

    @Override public synchronized String execute(String body) {
        if ("PING".equals(body)) return "OK\tREADY";
        if ("STOP".equals(body)) return "OK\tSTOPPED";
        return super.execute(body);
    }

    private static final class ShellStore extends CommandStore {
        @Override protected String command(String... arguments) throws Exception {
            return run(arguments);
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
