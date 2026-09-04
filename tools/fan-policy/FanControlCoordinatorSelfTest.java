import com.odin.desktop.service.fan.FanControlCoordinator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class FanControlCoordinatorSelfTest {
    private static int checks;

    private static final class FakeBackend implements FanControlCoordinator.Backend {
        volatile int performance;
        volatile int fan = 4;
        volatile int configuredFan = 4;
        volatile boolean auto = true;
        volatile boolean failNextFanWrite;
        volatile boolean partialWrite;
        Runnable duringRead = () -> {};
        Runnable duringPerformanceWrite = () -> {};

        public int readPerformance() { duringRead.run(); return performance; }
        public int readFan() { return fan; }
        public int readConfiguredFan() { return configuredFan; }
        public boolean readAutoEnabled() { return auto; }
        public void writeAutoEnabled(boolean enabled) { auto = enabled; }
        public void writeFan(int value) {
            if (failNextFanWrite) {
                failNextFanWrite = false;
                if (partialWrite) fan = configuredFan = value;
                throw new IllegalStateException("simulated failed fan write");
            }
            fan = configuredFan = value;
        }
        public void writePerformanceAndFan(int value, int target) {
            duringPerformanceWrite.run();
            performance = value;
            fan = configuredFan = target;
        }
    }

    public static void main(String[] args) throws Exception {
        performanceTargets();
        elevatedPerformanceCooling();
        policyOwnership();
        outdatedDecisions();
        partialWriteRecovery();
        serializedUserActions();
        System.out.println("PASS FanControlCoordinatorSelfTest (" + checks + " checks)");
    }

    private static void elevatedPerformanceCooling() {
        for (int performance : new int[]{1, 2}) {
            FakeBackend backend = new FakeBackend();
            backend.auto = false;
            backend.performance = performance;
            backend.fan = backend.configuredFan = 0;
            FanControlCoordinator controller = new FanControlCoordinator();
            controller.setAutoEnabled(backend, true);
            check(backend.auto && backend.fan == 4, "enabling sensors at elevated performance immediately restores SMART");
            // An OEM write may subsequently stop the fan without giving this process ownership.
            backend.fan = backend.configuredFan = 0;
            for (boolean charging : new boolean[]{false, true}) {
                for (boolean known : new boolean[]{false, true}) {
                    Integer target = FanControlCoordinator.policyTarget(controller.snapshot(backend), 40, charging, false, known);
                    check(target != null && target == 4, "elevated performance always requests SMART regardless of charging or foreground");
                }
            }
            backend.fan = backend.configuredFan = 5;
            controller.setAutoEnabled(backend, true);
            check(backend.fan == 5, "enabling sensors never downgrades maximum cooling");
        }
    }

    private static void performanceTargets() {
        int[] modes = {0, 4, 5};
        int[][] manualTargets = {{0, 4, 4}, {0, 4, 4}, {5, 5, 5}};
        for (boolean auto : new boolean[]{false, true}) {
            for (int fanIndex = 0; fanIndex < modes.length; fanIndex++) {
                for (int performance = 0; performance < 3; performance++) {
                    FakeBackend backend = new FakeBackend();
                    backend.auto = auto;
                    backend.fan = backend.configuredFan = modes[fanIndex];
                    FanControlCoordinator controller = new FanControlCoordinator();
                    FanControlCoordinator.Snapshot result = controller.setPerformance(backend, performance);
                    check(result.performanceMode == performance, "performance readback");
                    check(result.fanMode == (auto ? 4 : manualTargets[fanIndex][performance]), "performance fan target");
                    check(backend.auto == auto, "performance retains sensor preference");
                }
            }
        }
        FakeBackend inconsistent = new FakeBackend();
        inconsistent.auto = false;
        inconsistent.configuredFan = 5;
        inconsistent.fan = 0;
        new FanControlCoordinator().setPerformance(inconsistent, 0);
        check(inconsistent.fan == 5, "manual maximum intent repairs OEM PWM mismatch");
        new FanControlCoordinator().setPerformance(inconsistent, 1, 4);
        check(inconsistent.fan == 4, "explicit visible fan target survives coalesced automation changes");
    }

    private static void policyOwnership() {
        FakeBackend backend = new FakeBackend();
        FanControlCoordinator controller = new FanControlCoordinator();
        check(controller.applyPolicy(backend, controller.snapshot(backend), 0, () -> true), "policy mute applies");
        check(controller.snapshot(backend).mutedByPolicy, "policy owns its OFF");
        controller.setAutoEnabled(backend, false);
        check(backend.fan == 4 && !backend.auto, "disabling auto restores owned cooling");

        controller.setAutoEnabled(backend, true);
        controller.applyPolicy(backend, controller.snapshot(backend), 0, () -> true);
        controller.setManualFan(backend, 0);
        controller.setAutoEnabled(backend, false);
        check(backend.fan == 0, "manual OFF survives disable recovery");
        check(!controller.snapshot(backend).mutedByPolicy, "manual OFF releases ownership");

        controller.setAutoEnabled(backend, true);
        backend.fan = backend.configuredFan = 4;
        controller.applyPolicy(backend, controller.snapshot(backend), 0, () -> true);
        backend.fan = backend.configuredFan = 5;
        controller.setAutoEnabled(backend, false);
        check(backend.fan == 5, "disable never downgrades a newer external maximum");
    }

    private static void outdatedDecisions() {
        FakeBackend backend = new FakeBackend();
        FanControlCoordinator controller = new FanControlCoordinator();
        FanControlCoordinator.Snapshot old = controller.snapshot(backend);
        controller.setManualFan(backend, 5);
        check(!controller.applyPolicy(backend, old, 0, () -> true), "manual change invalidates policy snapshot");
        check(backend.fan == 5, "outdated policy cannot overwrite maximum");
        controller.setAutoEnabled(backend, true);
        old = controller.snapshot(backend);
        backend.performance = 1;
        check(!controller.applyPolicy(backend, old, 0, () -> true), "external performance change rejects old snapshot");
        check(!controller.applyPolicy(backend, controller.snapshot(backend), 0, () -> true), "high performance cannot be muted");
        backend.performance = 0;
        old = controller.snapshot(backend);
        backend.fan = backend.configuredFan = 4;
        check(!controller.applyPolicy(backend, old, 0, () -> true), "external fan change rejects old snapshot");
        old = controller.snapshot(backend);
        AtomicBoolean current = new AtomicBoolean(true);
        backend.duringRead = () -> current.set(false);
        check(!controller.applyPolicy(backend, old, 0, current::get), "foreground update during final read rejects policy");
        check(backend.fan == 4, "superseded foreground request leaves fan unchanged");
    }

    private static void partialWriteRecovery() {
        FakeBackend backend = new FakeBackend();
        FanControlCoordinator controller = new FanControlCoordinator();
        backend.failNextFanWrite = backend.partialWrite = true;
        expectFailure(() -> controller.applyPolicy(backend, controller.snapshot(backend), 0, () -> true));
        check(backend.fan == 0 && controller.snapshot(backend).mutedByPolicy, "partial OFF retains recovery ownership");
        backend.partialWrite = false;
        backend.failNextFanWrite = true;
        expectFailure(() -> controller.setAutoEnabled(backend, false));
        check(!backend.auto && controller.snapshot(backend).mutedByPolicy, "failed disable recovery remains retryable");
        check(controller.applyPolicy(backend, controller.snapshot(backend), 4, () -> true), "disabled policy may retry owned recovery");
        check(backend.fan == 4 && !controller.snapshot(backend).mutedByPolicy, "retry restores cooling and releases ownership");
    }

    private static void serializedUserActions() throws Exception {
        FakeBackend backend = new FakeBackend();
        backend.auto = false;
        FanControlCoordinator controller = new FanControlCoordinator();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch manualStarted = new CountDownLatch(1);
        AtomicBoolean manualDone = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        backend.duringPerformanceWrite = () -> {
            entered.countDown();
            await(release);
        };
        Thread performance = new Thread(() -> {
            try { controller.setPerformance(backend, 1); } catch (Throwable error) { failure.set(error); }
        });
        Thread manual = new Thread(() -> {
            manualStarted.countDown();
            try { controller.setManualFan(backend, 5); manualDone.set(true); }
            catch (Throwable error) { failure.set(error); }
        });
        performance.start();
        await(entered);
        manual.start();
        await(manualStarted);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (manual.isAlive() && manual.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.yield();
        }
        check(manual.getState() == Thread.State.BLOCKED, "manual writer blocks on the coordinator monitor");
        check(!manualDone.get(), "manual transaction waits for in-flight performance transaction");
        release.countDown();
        performance.join(5000);
        manual.join(5000);
        check(!performance.isAlive() && !manual.isAlive() && failure.get() == null, "serialized transactions complete");
        check(backend.performance == 1 && backend.fan == 5 && !backend.auto, "latest manual maximum wins after performance");
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("test synchronization timed out"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private static void expectFailure(Runnable operation) {
        try { operation.run(); throw new AssertionError("expected failed backend operation"); }
        catch (IllegalStateException expected) { checks++; }
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
