import com.odin.desktop.service.fan.SocTemperatureReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;

public final class SocTemperatureReaderSelfTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("odin-thermal-test-");
        try {
            Path cpu = Files.createDirectory(root.resolve("thermal_zone0"));
            Path gpu = Files.createDirectory(root.resolve("thermal_zone1"));
            Files.writeString(cpu.resolve("type"), "cpu-0");
            Files.writeString(gpu.resolve("type"), "gpu-0");
            Files.writeString(cpu.resolve("temp"), "60000");
            Files.writeString(gpu.resolve("temp"), "45");
            AtomicLong clock = new AtomicLong();
            SocTemperatureReader reader = new SocTemperatureReader(root.toFile(), clock::get);
            check(reader.read().maximum() == 60, "mixed milli-degree and degree sensors");
            Files.writeString(cpu.resolve("temp"), "51000");
            check(reader.read().maximum() == 60, "consumers share one bounded cached sample");
            clock.set(1000);
            check(reader.read().maximum() == 51, "cache expires at one second");
            Files.writeString(gpu.resolve("temp"), "NaN");
            clock.set(2000);
            check(Float.isNaN(reader.read().maximum()), "missing GPU cannot masquerade as valid maximum");
            Files.writeString(gpu.resolve("temp"), "1200000");
            clock.set(3000);
            check(Float.isNaN(reader.read().maximum()), "invalid sensor range remains unknown");
            Files.writeString(gpu.resolve("temp"), "40000");
            clock.set(4000);
            check(reader.read().maximum() == 51, "sensor recovery updates sample");
            clock.set(0);
            Files.writeString(cpu.resolve("temp"), "52000");
            check(reader.read().maximum() == 52, "clock reset invalidates cache");
            System.out.println("PASS SocTemperatureReader: cache, units, incomplete sensors, invalid values and recovery");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
