#!/usr/bin/env python3
"""Method-level actual-source regression for optimistic cooling UI and its queue.

Extracts the current method bodies plus their real state field declarations from
LauncherHardwareControls.kt. Only Android dependencies/hardware are fake. Scheduling uses
real kotlinx.coroutines and a single-thread Main dispatcher; latches force stale
writes/readbacks. Does not test Android lifecycle, Binder, sensors, or physical PWM.
"""
from pathlib import Path
import argparse
import hashlib
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
SOURCE = ROOT / "app/src/main/java/com/odin/desktop/ui/viewmodel/LauncherHardwareControls.kt"
parser = argparse.ArgumentParser(description=__doc__)
variants = parser.add_mutually_exclusive_group()
variants.add_argument("--legacy-policy-variant", action="store_true",
                      help="In temporary compiled source only, reproduce old backend-recomputed performance fan policy")
variants.add_argument("--unguarded-readback-variant", action="store_true",
                      help="In temporary compiled source only, remove the guard against publishing stale cooling readback")
args = parser.parse_args()
METHODS = ("enqueueCoolingAction", "cyclePerformanceMode", "cycleFanMode",
           "toggleAutoFanControl", "refreshHardwareStates")
# changeHardware shares the hardware mutex/readback and is extracted as the sixth
# production entry point, even though the cooling-specific tests do not invoke it.
METHODS += ("changeHardware", "toggleJoystickLight", "setJoystickColor")


def jar(group, name, version):
    matches = sorted((CACHE / group / name / version).glob("*/*.jar"))
    if not matches:
        raise SystemExit(f"Run the Gradle build first: missing {name}:{version}")
    return matches[0]


source = SOURCE.read_text()
# Mask strings/comments while preserving offsets; braces inside them cannot end a
# method. The text compiled below is the original, not this masked representation.
masked = re.sub(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
                lambda match: " " * len(match.group()), source)


def method(name):
    match = re.search(rf"^    (?:private )?(?:suspend )?fun {name}\(", masked, re.M)
    if not match:
        raise SystemExit(f"Production method missing: {name}")
    opening = masked.index("{", match.start())
    depth = 1
    end = opening + 1
    while depth and end < len(masked):
        depth += (masked[end] == "{") - (masked[end] == "}")
        end += 1
    if depth:
        raise SystemExit(f"Unbalanced method: {name}")
    text = source[match.start():end]
    return text


fields = ("_performanceMode", "_fanMode", "_autoFanControlEnabled", "hardwareLock",
          "coolingJob", "coolingIntentPending", "coolingIntentRevision", "pendingCoolingActions",
          "_joystickLightEnabled", "_joystickColor", "_chargingSeparation", "_chargePowerLimit",
          "_chargeLimit80", "_airplaneMode", "_orientationMode", "_isDefaultHome",
          "_bootAutoStartEnabled", "_currentSocTemp", "lightJob", "colorJob")
declarations = []
for name in fields:
    match = re.search(rf"^    (?:@Volatile )?private (?:val|var) {name}\b[^\n]*", source, re.M)
    if not match:
        raise SystemExit(f"Production field missing: {name}")
    declarations.append(match.group())
production = "\n\n".join([*declarations, *(method(name) for name in METHODS)])
print("Actual-source methods: " + ", ".join(METHODS), flush=True)
print("Extracted source SHA256: " + hashlib.sha256(production.encode()).hexdigest(), flush=True)
if args.legacy_policy_variant:
    new_call = "HardwareController.setPerformanceAndFan(context, next, fanTarget)"
    if production.count(new_call) != 1:
        raise SystemExit("Cannot reproduce legacy policy: expected exact production call is missing")
    production = production.replace(new_call, "HardwareController.setPerformanceMode(context, next)")
    print("NEGATIVE CONTROL: temporary source calls old setPerformanceMode without captured fanTarget", flush=True)
if args.unguarded_readback_variant:
    guard = "if (!coolingIntentPending && revision == coolingIntentRevision.get())"
    if production.count(guard) != 1:
        raise SystemExit("Cannot reproduce stale readback: expected exact production guard is missing")
    production = production.replace(guard, "if (true)")
    print("NEGATIVE CONTROL: temporary source publishes readback without checking newer selection", flush=True)

compiler = [jar("org.jetbrains.kotlin", name, version) for name, version in [
    ("kotlin-compiler-embeddable", "2.0.0"), ("kotlin-stdlib", "2.0.0"),
    ("kotlin-script-runtime", "2.0.0"), ("kotlin-reflect", "1.6.10")]]
compiler += [jar("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330")]
annotations = next((CACHE / "org.jetbrains/annotations").glob("*/*/*.jar"))
coroutines = jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.1")
compiler += [annotations, coroutines]
java = Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@17")) / "bin/java"

resource_names = sorted(set(re.findall(r"R\.string\.(\w+)", production)))
resource_stub = "object R { object string { " + "; ".join(
    f"const val {name} = {index}" for index, name in enumerate(resource_names)) + " } }"

sources = {
    "resources": "package regression\n" + resource_stub +
        '\nfun Any.getString(id: Int): String = "Test resource $id"\n',
    "viewmodel": '''package regression
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.widget.Toast

class CoolingViewModelHarness {
    private val context = Any()
    private val scopeFailures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate +
        CoroutineExceptionHandler { _, failure -> scopeFailures += failure })
''' + production + '''
    fun seed(performance: Int, fan: Int, auto: Boolean) {
        check(MainThread.isCurrent())
        _performanceMode.value = performance
        _fanMode.value = fan
        _autoFanControlEnabled.value = auto
    }
    fun selection() = Selection(_performanceMode.value, _fanMode.value, _autoFanControlEnabled.value)
    fun pendingKinds() = pendingCoolingActions.keys.toSet()
    suspend fun awaitIdle() {
        withTimeout(5_000) { coolingJob?.join() }
        check(scopeFailures.isEmpty()) { "Uncaught coroutine failure: $scopeFailures" }
        check(!coolingIntentPending && pendingCoolingActions.isEmpty()) { "Queue did not settle" }
    }
    fun observerRefresh() = viewModelScope.launch(Dispatchers.IO) {
        hardwareLock.withLock { refreshHardwareStates(refreshPerformance = true) }
    }
    suspend fun awaitLights() {
        withTimeout(5_000) { lightJob?.join(); colorJob?.join() }
        check(scopeFailures.isEmpty())
    }
    fun close() { viewModelScope.cancel() }
}
data class Selection(val performance: Int, val fan: Int, val auto: Boolean)
''',
    "main_dispatcher": '''package regression
import kotlinx.coroutines.*
import kotlinx.coroutines.internal.MainDispatcherFactory
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.Executors

object MainThread {
    @Volatile private var thread: Thread? = null
    private val executor = Executors.newSingleThreadExecutor { action ->
        Thread(action, "cooling-test-main").apply { isDaemon = true; thread = this }
    }
    private val delegate = executor.asCoroutineDispatcher()
    fun isCurrent() = Thread.currentThread() === thread
    val dispatcher = object : MainCoroutineDispatcher() {
        override val immediate: MainCoroutineDispatcher get() = this
        override fun isDispatchNeeded(context: CoroutineContext) = !isCurrent()
        override fun dispatch(context: CoroutineContext, block: Runnable) = delegate.dispatch(context, block)
    }
    fun close() { delegate.close() }
}
@OptIn(InternalCoroutinesApi::class)
class TestMainDispatcherFactory : MainDispatcherFactory {
    override val loadPriority = Int.MAX_VALUE
    override fun createDispatcher(allFactories: List<MainDispatcherFactory>) = MainThread.dispatcher
    override fun hintOnError() = "Cooling test Main dispatcher"
}
''',
    "android_log": '''package android.util
object Log { fun w(tag: String, message: String, failure: Throwable) = 0 }
''',
    "android_toast": '''package android.widget
class Toast {
    fun show() { check(regression.MainThread.isCurrent()); shown++ }
    companion object {
        var shown = 0
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        fun makeText(context: Any, message: String, length: Int) = Toast()
    }
}
''',
    "hardware": '''package regression
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Gate {
    val entered = CountDownLatch(1)
    val released = CountDownLatch(1)
    fun block() {
        check(!MainThread.isCurrent()) { "Blocking hardware operation ran on Main" }
        entered.countDown()
        check(released.await(5, TimeUnit.SECONDS)) { "Hardware test gate was not released" }
    }
    fun release() { released.countDown() }
}
object HardwareController {
    const val PERF_NORMAL = 0
    const val FAN_OFF = 0
    const val FAN_SMART = 4
    const val FAN_SPORT = 5
    const val ORIENTATION_LANDSCAPE = 0
    @Volatile var performance = 0
    @Volatile var fan = 0
    @Volatile var auto = false
    @Volatile var lights = false
    @Volatile var color = "#ff00e5ff"
    val calls = CopyOnWriteArrayList<String>()
    val performanceTargets = CopyOnWriteArrayList<Pair<Int, Int?>>()
    private val gates = ConcurrentHashMap<String, Gate>()
    private val failures = ConcurrentHashMap.newKeySet<String>()
    fun reset(performance: Int = 0, fan: Int = 0, auto: Boolean = false) {
        gates.values.forEach { it.release() }; gates.clear(); failures.clear(); calls.clear()
        performanceTargets.clear()
        this.performance = performance; this.fan = fan; this.auto = auto
        lights = false; color = "#ff00e5ff"
        android.widget.Toast.shown = 0
    }
    fun blockNext(operation: String) = Gate().also { gates[operation] = it }
    fun failNext(operation: String) { failures += operation }
    private fun before(operation: String, target: String) {
        check(!MainThread.isCurrent()) { "Hardware write ran on Main" }
        calls += "$operation:$target"
        gates.remove(operation)?.block()
        if (failures.remove(operation)) error("Injected $operation failure")
    }
    fun getPerformanceMode(context: Any): Int {
        val captured = performance
        gates.remove("readPerformance")?.block()
        return captured
    }
    fun getFanMode(context: Any) = fan
    fun isAutoFanControlEnabled(context: Any) = auto
    fun setPerformanceMode(context: Any, mode: Int) {
        before("performance", mode.toString())
        performanceTargets += mode to null
        performance = mode
        // Hardware fixture: preserve manual maximum, otherwise apply mode default.
        // Sensor/OEM policy itself is covered by FanControlCoordinator's own tests.
        fan = if (!auto && fan == FAN_SPORT) FAN_SPORT else if (auto || mode != 0) FAN_SMART else FAN_OFF
    }
    fun setPerformanceAndFan(context: Any, mode: Int, fanTarget: Int) {
        before("performance", mode.toString())
        performanceTargets += mode to fanTarget
        performance = mode; fan = fanTarget
    }
    fun setManualFanMode(context: Any, mode: Int) {
        before("fan", mode.toString()); auto = false; fan = mode
    }
    fun setAutoFanControlEnabled(context: Any, enabled: Boolean) {
        before("automation", enabled.toString()); auto = enabled
        if (enabled) fan = FAN_SMART
    }
    fun isJoystickLightEnabled(context: Any) = lights
    fun getJoystickColor(context: Any) = color
    fun setJoystickLightEnabled(context: Any, enabled: Boolean) { before("lights", enabled.toString()); lights = enabled }
    fun setJoystickColor(context: Any, hex: String) { before("color", hex); color = hex }
    fun isChargingSeparationEnabled(context: Any) = false
    fun isChargePowerLimit5V(context: Any) = true
    fun isChargeLimit80Enabled(context: Any) = false
    fun isAirplaneModeOn(context: Any) = false
    fun getOrientationMode(context: Any) = 0
    fun isDefaultHome(context: Any) = true
    fun isBootAutoStartEnabled(context: Any) = true
    fun getMaxCpuGpuTemp() = 40f
    fun state() = Selection(performance, fan, auto)
}
''',
    "tests": '''package regression
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

private suspend fun Gate.awaitEntered() = withContext(Dispatchers.IO) {
    check(entered.await(5, TimeUnit.SECONDS)) { "Expected hardware operation never started" }
}
private suspend fun fixture(performance: Int = 0, fan: Int = 0, auto: Boolean = false,
                            body: suspend (CoolingViewModelHarness) -> Unit) {
    HardwareController.reset(performance, fan, auto)
    val vm = withContext(Dispatchers.Main) {
        CoolingViewModelHarness().apply { seed(performance, fan, auto) }
    }
    try { withTimeout(10_000) { body(vm) } } finally {
        withContext(Dispatchers.Main) { vm.close() }
        HardwareController.reset()
    }
}
private fun expect(vm: CoolingViewModelHarness, expected: Selection, label: String) {
    check(MainThread.isCurrent())
    check(vm.selection() == expected) { "$label: expected=$expected actual=${vm.selection()}" }
}

fun main() = runBlocking {
    try {
        fixture { vm ->
            withContext(Dispatchers.Main) {
                vm.cyclePerformanceMode()
                expect(vm, Selection(1, 4, false), "Performance must display before hardware starts")
                check(HardwareController.calls.isEmpty())
                vm.cycleFanMode()
                expect(vm, Selection(1, 5, false), "Fan cycles from the visible smart choice")
                vm.toggleAutoFanControl()
                check(vm.selection().auto) { "Automation flag must display immediately" }
                vm.toggleAutoFanControl()
                check(!vm.selection().auto)
                check(vm.pendingKinds().size <= 3)
                vm.awaitIdle()
                expect(vm, Selection(1, 5, false), "Settled final selection")
                check(HardwareController.state() == vm.selection())
            }
            println("PASS immediate performance/fan/auto selection and final hardware intent")
        }
        fixture(fan = 5) { vm ->
            withContext(Dispatchers.Main) {
                repeat(1_000) { index ->
                    vm.cyclePerformanceMode()
                    expect(vm, Selection((index + 1) % 3, 5, false), "Rapid visible performance cycle")
                    check(vm.pendingKinds() == setOf("performance")) { "Same-kind queue grew" }
                }
                vm.awaitIdle()
                check(HardwareController.calls == listOf("performance:1")) {
                    "Expected one coalesced write, got ${HardwareController.calls}"
                }
                expect(vm, Selection(1, 5, false), "Manual maximum survives rapid performance cycles")
            }
            println("PASS 1,000 rapid cycles use current display and collapse to one pending write")
        }
        fixture(fan = 5) { vm ->
            withContext(Dispatchers.Main) {
                vm.toggleAutoFanControl()
                vm.cyclePerformanceMode()
                vm.toggleAutoFanControl()
                expect(vm, Selection(1, 4, false), "Chosen smart fan after auto-on/performance/auto-off")
                vm.awaitIdle()
                expect(vm, Selection(1, 4, false), "Coalescing automation must not recompute manual maximum")
                check(HardwareController.state() == Selection(1, 4, false))
                check(HardwareController.performanceTargets == listOf(1 to 4)) {
                    "Performance must carry the exact fan selection observed at the press"
                }
            }
            println("PASS auto-on/performance/auto-off preserves captured SMART target from manual MAX")
        }
        fixture { vm ->
            val oldPerformance = HardwareController.blockNext("performance")
            withContext(Dispatchers.Main) { vm.cyclePerformanceMode() }
            oldPerformance.awaitEntered()
            val newFan = HardwareController.blockNext("fan")
            try {
                withContext(Dispatchers.Main) {
                    vm.cycleFanMode()
                    vm.toggleAutoFanControl()
                    vm.toggleAutoFanControl()
                    expect(vm, Selection(1, 5, false), "New choices while old performance blocks")
                }
                oldPerformance.release()
                newFan.awaitEntered()
                withContext(Dispatchers.Main) {
                    expect(vm, Selection(1, 5, false), "Old performance completion must not overwrite new fan")
                }
                newFan.release()
                withContext(Dispatchers.Main) {
                    vm.awaitIdle()
                    expect(vm, Selection(1, 5, false), "Last interleaved intent wins")
                    check(HardwareController.state() == vm.selection())
                }
            } finally { oldPerformance.release(); newFan.release() }
            println("PASS in-flight performance plus newer fan/auto choices cannot publish stale state")
        }
        fixture { vm ->
            val staleRead = HardwareController.blockNext("readPerformance")
            val observer = withContext(Dispatchers.Main) { vm.observerRefresh() }
            staleRead.awaitEntered()
            val newWrite = HardwareController.blockNext("performance")
            try {
                withContext(Dispatchers.Main) {
                    vm.cyclePerformanceMode()
                    expect(vm, Selection(1, 4, false), "Selection during stale observer read")
                }
                staleRead.release()
                newWrite.awaitEntered()
                observer.join()
                withContext(Dispatchers.Main) {
                    expect(vm, Selection(1, 4, false), "Stale readback must not overwrite new revision")
                }
                newWrite.release()
                withContext(Dispatchers.Main) {
                    vm.awaitIdle()
                    check(HardwareController.state() == vm.selection())
                }
            } finally { staleRead.release(); newWrite.release() }
            println("PASS observer read started before a new press cannot overwrite its visible choice")
        }
        fixture(fan = 5) { vm ->
            HardwareController.failNext("fan")
            withContext(Dispatchers.Main) {
                vm.cycleFanMode()
                expect(vm, Selection(0, 0, false), "Failed request still selects immediately")
                vm.awaitIdle()
                expect(vm, Selection(0, 5, false), "Failure reconciles to actual hardware")
                check(android.widget.Toast.shown == 1) { "Failure feedback missing" }
            }
            println("PASS failure reports once and reconciles optimistic choice to actual hardware")
        }
        fixture { vm ->
            HardwareController.failNext("performance")
            withContext(Dispatchers.Main) {
                vm.cyclePerformanceMode(); vm.cycleFanMode()
                vm.awaitIdle()
                expect(vm, Selection(0, 5, false), "Later manual fan survives earlier failed performance")
                check(HardwareController.calls == listOf("performance:1", "fan:5"))
                check(android.widget.Toast.shown == 1)
            }
            println("PASS one failed queued command does not discard a later command")
        }
        fixture { vm ->
            val inFlight = HardwareController.blockNext("performance")
            withContext(Dispatchers.Main) { vm.cyclePerformanceMode() }
            inFlight.awaitEntered()
            try {
                withContext(Dispatchers.Main) {
                    repeat(1_000) {
                        vm.cyclePerformanceMode()
                        vm.toggleAutoFanControl()
                        vm.cycleFanMode()
                        check(vm.pendingKinds().size <= 3)
                        check("automation" !in vm.pendingKinds()) { "Manual choice must supersede pending automation" }
                    }
                    vm.cyclePerformanceMode()
                    vm.toggleAutoFanControl()
                    check(vm.pendingKinds().size == 3)
                }
                inFlight.release()
                withContext(Dispatchers.Main) {
                    vm.awaitIdle()
                    check(HardwareController.calls.size <= 4) { "Queued writes scaled with press count" }
                    check(HardwareController.state() == vm.selection())
                    check(vm.selection().auto) { "Final automation intent was lost" }
                }
            } finally { inFlight.release() }
            println("PASS mixed 3,000-press storm keeps at most three pending command kinds")
        }
        for (colorFirst in listOf(true, false)) {
            fixture { vm ->
                withContext(Dispatchers.Main) {
                    if (colorFirst) {
                        vm.setJoystickColor("#ffff5252"); vm.toggleJoystickLight()
                    } else {
                        vm.toggleJoystickLight(); vm.setJoystickColor("#ffff5252")
                    }
                    vm.awaitLights()
                    check(HardwareController.lights && HardwareController.color == "#ffff5252") {
                        "Color and light toggle must both commit regardless of input order"
                    }
                }
            }
        }
        println("PASS light color and toggle retain both intentions in either order")
        println("PASS all actual-source cooling UI regressions")
    } finally { MainThread.close() }
}
''',
}

with tempfile.TemporaryDirectory(prefix="odin-cooling-ui-") as folder:
    folder = Path(folder)
    paths = []
    for name, contents in sources.items():
        path = folder / f"{name}.kt"
        path.write_text(contents)
        paths.append(path)
    cp = os.pathsep.join(map(str, compiler))
    runtime = os.pathsep.join(map(str, [compiler[1], annotations, coroutines]))
    output = folder / "classes"
    compiled = subprocess.run([str(java), "-cp", cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                               "-no-stdlib", "-no-reflect", "-nowarn", "-classpath", runtime,
                               "-d", str(output), *map(str, paths)])
    if compiled.returncode:
        raise SystemExit(compiled.returncode)
    service = output / "META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory"
    service.parent.mkdir(parents=True, exist_ok=True)
    service.write_text("regression.TestMainDispatcherFactory\n")
    raise SystemExit(subprocess.run([str(java), "-cp", str(output) + os.pathsep + runtime,
                                    "regression.TestsKt"], timeout=60).returncode)
