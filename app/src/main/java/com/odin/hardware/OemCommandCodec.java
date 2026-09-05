package com.odin.hardware;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Wire adaptation for the Odin 3 PServerBinder command length and first-line response limits. */
public final class OemCommandCodec {
    private OemCommandCodec() { }

    public static String encode(String... arguments) throws IOException {
        StringBuilder invocation = new StringBuilder("/system/bin/timeout 2");
        for (String argument : arguments) {
            invocation.append(" '").append(argument.replace("'", "'\\''")).append("'");
        }
        // Capture the exit status before flattening stdout/stderr into one response line.
        String command = "o=$(" + invocation + " 2>&1); s=$?; " +
            "printf 'ODIN:%s %s' \"$s\" \"$o\" | /system/bin/tr '\\n' ' '";
        if (command.getBytes(StandardCharsets.UTF_8).length > 255) {
            throw new IOException("OEM command exceeds firmware limit");
        }
        return command;
    }

    public static String decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > 4096) throw new IOException("Invalid OEM response length");
        String output = new String(bytes, StandardCharsets.UTF_8);
        if (!output.startsWith("ODIN:0 ")) throw new IOException("OEM command failed or timed out");
        return output.substring("ODIN:0 ".length()).trim();
    }
}
