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
        boolean failNextFanWrite;
        int writes;
        Runnable duringPerformanceWrite = () -> {};
        public int readPerformance() { return performance; }
        public int readFan() { return fan; }
        public int readConfiguredFan() { return configuredFan; }
        public void writeFan(int mode) {
            writes++;
            if (failNextFanWrite) { failNextFanWrite = false; throw new IllegalStateException("failed write"); }
            fan = configuredFan = mode;
        }
        public void writePerformanceAndFan(int mode, int target) {
            duringPerformanceWrite.run();
            writes++;
            performance = mode;
            fan = configuredFan = target;
        }
    }

    public static void main(String[] args) throws Exception {
        for (int fan : new int[]{0, 4, 5}) {
            for (int performance = 0; performance < 3; performance++) {
                FakeBackend backend = new FakeBackend();
                backend.fan = backend.configuredFan = fan;
                var result = new FanControlCoordinator().setPerformance(backend, performance);
                check(result.performanceMode == performance, "performance readback");
                check(result.fanMode == (fan == 5 ? 5 : performance == 0 ? 0 : 4), "existing manual performance linkage");
                check(backend.writes == 1, "one user request, one transaction");
            }
        }
        FakeBackend backend = new FakeBackend();
        var controller = new FanControlCoordinator();
        backend.configuredFan = 5; backend.fan = 0;
        check(controller.setPerformance(backend, 0).fanMode == 5, "configured MAX survives OEM mismatch");
        check(controller.setPerformance(backend, 1, 4).fanMode == 4, "explicit captured target is honored");
        for (int fan : new int[]{0, 4, 5, 0}) {
            check(controller.setManualFan(backend, fan) == fan, "manual fan choice is retained");
        }
        int writes = backend.writes;
        for (int invalid : new int[]{-1, 1, 2, 3, 6}) {
            try { controller.setManualFan(backend, invalid); throw new AssertionError("invalid mode accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        for (int performance : new int[]{1, 2}) {
            try { controller.setPerformance(backend, performance, 0); throw new AssertionError("unsafe combined request accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        check(backend.writes == writes, "invalid requests do not touch hardware");
        backend.failNextFanWrite = true;
        expectFailure(() -> controller.setManualFan(backend, 5));
        check(backend.fan == 0, "failed manual request does not start an automatic fallback");
        check(controller.setManualFan(backend, 4) == 4, "next explicit choice still works");
        serializedUserActions();
        System.out.println("PASS FanControlCoordinatorSelfTest (" + checks + " checks)");
    }

    private static void serializedUserActions() throws Exception {
        FakeBackend backend = new FakeBackend();
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
        check(backend.performance == 1 && backend.fan == 5, "latest manual maximum wins after performance");
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
