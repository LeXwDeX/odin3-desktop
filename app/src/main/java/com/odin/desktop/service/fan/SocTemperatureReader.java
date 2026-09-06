package com.odin.desktop.service.fan;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/** Shared sensor discovery and a short sample cache; no thread, timer, or wake lock. */
public final class SocTemperatureReader {
    private final File root;
    private final LongSupplier clock;
    private final List<File> cpuFiles = new ArrayList<>(), gpuFiles = new ArrayList<>();
    private long discoveredAt = -1, sampledAt = -1;
    private Sample sample = new Sample(Float.NaN, Float.NaN);

    public static final class Sample {
        public final float cpu, gpu;
        Sample(float cpu, float gpu) { this.cpu = cpu; this.gpu = gpu; }
        public float maximum() { return Math.max(cpu, gpu); }
    }

    public SocTemperatureReader(File root, LongSupplier clock) { this.root = root; this.clock = clock; }

    public synchronized Sample read() {
        long now = clock.getAsLong();
        if (sampledAt >= 0 && now >= sampledAt && now - sampledAt < 1_000) return sample;
        if (discoveredAt < 0 || now < discoveredAt || now - discoveredAt >= 60_000) {
            cpuFiles.clear(); gpuFiles.clear();
            File[] zones;
            try { zones = root.listFiles(); }
            catch (SecurityException unavailable) { zones = null; }
            if (zones != null) for (File zone : zones) {
                if (!zone.getName().startsWith("thermal_zone")) continue;
                try {
                    String type = text(new File(zone, "type")).toLowerCase(Locale.ROOT);
                    if (type.contains("cpu")) cpuFiles.add(new File(zone, "temp"));
                    if (type.contains("gpu")) gpuFiles.add(new File(zone, "temp"));
                } catch (IOException | SecurityException ignored) { }
            }
            discoveredAt = now;
        }
        sample = new Sample(maximum(cpuFiles), maximum(gpuFiles));
        sampledAt = now;
        return sample;
    }

    private static float maximum(List<File> files) {
        float result = Float.NaN;
        for (File file : files) try {
            float value = Float.parseFloat(text(file));
            if (value > 1000) value /= 1000;
            if (Float.isFinite(value) && value >= 10 && value <= 120) {
                result = Float.isNaN(result) ? value : Math.max(result, value);
            }
        } catch (IOException | SecurityException | NumberFormatException ignored) { }
        return result;
    }

    private static String text(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
    }
}
