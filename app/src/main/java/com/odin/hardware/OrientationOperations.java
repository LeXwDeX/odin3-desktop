package com.odin.hardware;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Odin's Android 15 rotation preference: landscape grip, with portrait requests allowed. */
public final class OrientationOperations {
    public interface Commands { String run(String... args) throws Exception; }
    private final Commands commands;
    private static final String LOCK = "persist.demo.rotationlock";
    private static final String DIRECTION = "persist.demo.remoterotation";
    private static final String[] SETTINGS = {"force_landscape", "accelerometer_rotation", "user_rotation"};

    public OrientationOperations(Commands commands) { this.commands = commands; }

    public String apply(String mode) {
        if (!"0".equals(mode) && !"1".equals(mode)) return "ERR\tBAD_REQUEST";
        Map<String, String> previous = new LinkedHashMap<>();
        String oldLock, oldDirection, oldFixed, size;
        try {
            for (String key : SETTINGS) {
                String value = setting(key);
                if (!value.matches("null|[0-3]")) throw new IOException("Unknown rotation setting");
                previous.put(key, value);
            }
            oldLock = property(LOCK); oldDirection = property(DIRECTION);
            if (!oldLock.matches("|true|false|0|1") || !oldDirection.matches("|portrait|landscape"))
                throw new IOException("Unknown rotation preference");
            oldFixed = commands.run("/system/bin/wm", "fixed-to-user-rotation");
            if (!oldFixed.matches("default|enabled|disabled|enabled_if_no_auto_rotation"))
                throw new IOException("Unknown fixed rotation mode");
            size = existingSize(commands.run("/system/bin/wm", "size"));
        } catch (Exception unavailable) { return "ERR\tREAD_UNAVAILABLE"; }
        boolean fixed = "0".equals(mode);
        Map<String, String> wanted = new LinkedHashMap<>(previous);
        wanted.put("force_landscape", "0"); // OEM's overlay would also override portrait apps.
        wanted.put("accelerometer_rotation", fixed ? "0" : "1");
        if (fixed) wanted.put("user_rotation", "1");
        String lock = fixed ? "true" : "false";
        try {
            writeSettings(wanted);
            property(DIRECTION, "landscape"); property(LOCK, lock);
            commands.run("/system/bin/wm", "fixed-to-user-rotation", "disabled");
            // DisplayRotation.configure reads the two properties. Reapply the EXISTING dimensions
            // to refresh it, preserving custom sizes; do not resize by a pixel or restart Android.
            commands.run("/system/bin/wm", "size", size);
            verify(wanted, lock, "landscape", "disabled");
            return "OK\tORIENTATION\t" + mode;
        } catch (Exception failure) {
            boolean restored = true;
            for (Map.Entry<String, String> entry : previous.entrySet()) {
                try { putSetting(entry.getKey(), entry.getValue()); } catch (Exception ignored) { restored = false; }
            }
            try { property(DIRECTION, oldDirection); } catch (Exception ignored) { restored = false; }
            try { property(LOCK, oldLock); } catch (Exception ignored) { restored = false; }
            try { commands.run("/system/bin/wm", "fixed-to-user-rotation", oldFixed); } catch (Exception ignored) { restored = false; }
            try { commands.run("/system/bin/wm", "size", size); } catch (Exception ignored) { restored = false; }
            try { verify(previous, oldLock, oldDirection, oldFixed); } catch (Exception ignored) { restored = false; }
            return restored ? "ERR\tWRITE_REJECTED" : "ERR\tROLLBACK_INCOMPLETE";
        }
    }

    static String existingSize(String value) throws IOException {
        Matcher physical = Pattern.compile("Physical size: ([0-9]{3,5}x[0-9]{3,5})").matcher(value);
        if (!physical.find()) throw new IOException("Display dimensions unavailable");
        Matcher override = Pattern.compile("Override size: ([0-9]{3,5}x[0-9]{3,5})").matcher(value);
        return override.find() ? override.group(1) : "reset";
    }
    private String setting(String key) throws Exception {
        return commands.run("/system/bin/settings", "--user", "0", "get", "system", key);
    }
    private void putSetting(String key, String value) throws Exception {
        if (value.equals(setting(key))) return;
        if ("null".equals(value)) commands.run("/system/bin/settings", "--user", "0", "delete", "system", key);
        else commands.run("/system/bin/settings", "--user", "0", "put", "system", key, value);
    }
    private void writeSettings(Map<String, String> values) throws Exception {
        for (Map.Entry<String, String> entry : values.entrySet()) putSetting(entry.getKey(), entry.getValue());
    }
    private String property(String key) throws Exception { return commands.run("/system/bin/getprop", key); }
    private void property(String key, String value) throws Exception {
        if (!value.equals(property(key))) commands.run("/system/bin/setprop", key, value);
    }
    private void verify(Map<String, String> values, String lock, String direction, String fixed) throws Exception {
        for (Map.Entry<String, String> entry : values.entrySet())
            if (!entry.getValue().equals(setting(entry.getKey()))) throw new IOException("Rotation setting mismatch");
        if (!lock.equals(property(LOCK)) || !direction.equals(property(DIRECTION)) ||
            !fixed.equals(commands.run("/system/bin/wm", "fixed-to-user-rotation")))
            throw new IOException("Rotation preference mismatch");
    }
}
