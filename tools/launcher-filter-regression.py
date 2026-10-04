#!/usr/bin/env python3
"""Replay app-filter publication races using current production Kotlin methods.

Compiles unmodified filterAppsForCurrentTab, enterReorderMode and exitReorderMode,
plus the actual app ordering helpers. Coroutines are real. A local Dispatchers
fixture supplies single-thread Main and a manually released Default queue.
Android resources, Room repository and order persistence are limited fakes.
This checks stale publication and focus, not Android lifecycle or database writes.
--unguarded-variant removes both new guards only in temporary compiled source.
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
SOURCE = ROOT / "app/src/main/java/com/odin/desktop/ui/viewmodel/LauncherViewModel.kt"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--unguarded-variant", action="store_true")
args = parser.parse_args()
source = SOURCE.read_text()
masked = re.sub(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
                lambda match: " " * len(match.group()), source)


def method(name):
    match = re.search(rf"^    (?:private )?fun {name}\(", masked, re.M)
    if not match:
        raise SystemExit(f"Production method missing: {name}")
    opening = masked.index("{", match.start())
    depth, end = 1, opening + 1
    while depth and end < len(masked):
        depth += (masked[end] == "{") - (masked[end] == "}")
        end += 1
    if depth:
        raise SystemExit(f"Unbalanced production method: {name}")
    return source[match.start():end]


methods = {name: method(name) for name in
           ("filterAppsForCurrentTab", "enterReorderMode", "exitReorderMode")}
production = "\n\n".join(methods.values())
print("Actual-source methods: " + ", ".join(methods), flush=True)
print("Extracted source SHA256: " + hashlib.sha256(production.encode()).hexdigest(), flush=True)
if args.unguarded_variant:
    cancel = "        filterJob?.cancel()\n        filterJob = null\n"
    reference = " || _currentTabApps.value !== currentBeforeCalculation"
    if methods["enterReorderMode"].count(cancel) != 1 or production.count(reference) != 1:
        raise SystemExit("Negative control requires the exact cancellation and reference guards")
    methods["enterReorderMode"] = methods["enterReorderMode"].replace(cancel, "")
    production = "\n\n".join(methods.values()).replace(reference, "")
    print("NEGATIVE CONTROL: temporary source removes reorder cancellation and list reference guard", flush=True)


def jar(group, name, version):
    matches = sorted((CACHE / group / name / version).glob("*/*.jar"))
    if not matches:
        raise SystemExit(f"Run the Gradle build first: missing {name}:{version}")
    return matches[0]


compiler = [jar("org.jetbrains.kotlin", name, version) for name, version in [
    ("kotlin-compiler-embeddable", "2.0.0"), ("kotlin-stdlib", "2.0.0"),
    ("kotlin-script-runtime", "2.0.0"), ("kotlin-reflect", "1.6.10")]]
compiler += [jar("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330")]
annotations = next((CACHE / "org.jetbrains/annotations").glob("*/*/*.jar"))
coroutines = jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.1")
compiler += [annotations, coroutines]
java = Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@17")) / "bin/java"

sources = {
    "drawable": "package android.graphics.drawable\nopen class Drawable\n",
    "tab": "package com.odin.desktop.data.entity\nenum class TabKind { ALL_APPS, USER }\n",
    "fixture": '''package regression
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.odin.desktop.data.model.*
import com.odin.desktop.ui.navigation.FocusZone

// Scheduling fixture only. Production method bodies resolve their unchanged
// Dispatchers.Default reference to this queue; coroutine cancellation remains real.
object Dispatchers {
    val Main = Executors.newSingleThreadExecutor { action -> Thread(action, "filter-main") }
        .asCoroutineDispatcher()
    val Default = ControlledDefault()
    fun close() { Main.close(); Default.close() }
}
class ControlledDefault : CoroutineDispatcher() {
    private val queue = LinkedBlockingQueue<Runnable>()
    private val worker = Executors.newSingleThreadExecutor { action -> Thread(action, "filter-worker") }
    override fun dispatch(context: CoroutineContext, block: Runnable) { queue.put(block) }
    fun take(): Runnable = requireNotNull(queue.poll(5, TimeUnit.SECONDS)) { "Default calculation was not queued" }
    fun release(task: Runnable) { worker.submit(task).get(5, TimeUnit.SECONDS) }
    fun close() { worker.shutdownNow() }
}
data class Tab(val id: Long = 1, val kind: com.odin.desktop.data.entity.TabKind = com.odin.desktop.data.entity.TabKind.ALL_APPS)
data class Mapping(val packageName: String)
class Repository {
    var rows = listOf("a", "b", "c").map(::Mapping)
    var mode = AppSortMode.MANUAL
    fun getSortMode(id: Long) = mode
    fun setSortMode(id: Long, value: AppSortMode) { mode = value }
    fun getAppsForTabFlow(id: Long) = flowOf(rows.toList())
}
class Configuration { val locales = arrayOf(Locale.ENGLISH) }
class Resources { val configuration = Configuration() }
class Context { val resources = Resources() }
class Fixture {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val appRepository = Repository()
    private val context = Context()
    private val _allInstalledApps = MutableStateFlow(listOf("a", "b", "c").map { InstalledApp(it, "Main", it) })
    private val _currentTabApps = MutableStateFlow(_allInstalledApps.value.toList())
    private val _currentTabAppPackages = MutableStateFlow(emptySet<String>())
    private val _selectedAppIndex = MutableStateFlow(9)
    private val _sortMode = MutableStateFlow(AppSortMode.MANUAL)
    private val _isReorderingApps = MutableStateFlow(false)
    private val _focusZone = MutableStateFlow(FocusZone.APPS)
    private val _isSortMenuOpen = MutableStateFlow(false)
    private val _isAllAppsOpen = MutableStateFlow(false)
    private val _pickedAppIndex = MutableStateFlow<Int?>(null)
    private val pendingOrders = mutableMapOf<Long, List<String>>()
    private var filterJob: kotlinx.coroutines.Job? = null
    private fun activeAppTab() = Tab()
    private fun visibleAppCount() = homeAppCount(_currentTabApps.value.size, _isReorderingApps.value)
    private fun saveCurrentTabAppOrder() {
        pendingOrders[1] = _currentTabApps.value.map { it.packageName }
    }
    fun startFilter() { filterAppsForCurrentTab() }
    fun replacePackages(vararg packages: String) {
        _currentTabApps.value = packages.map { name -> _allInstalledApps.value.single { it.packageName == name } }
    }
    fun select(index: Int) { _selectedAppIndex.value = index }
    val packages get() = _currentTabApps.value.map { it.packageName }
    val selected get() = _selectedAppIndex.value
    val memberships get() = _currentTabAppPackages.value
    fun close() { viewModelScope.cancel() }
''' + production + "\n}\n",
    "tests": '''package regression
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

suspend fun start(): Pair<Fixture, Runnable> {
    val vm = withContext(Dispatchers.Main) { Fixture().also { it.startFilter() } }
    return vm to Dispatchers.Default.take()
}
suspend fun release(task: Runnable) {
    Dispatchers.Default.release(task)
    // FIFO Main barrier waits for the withContext continuation to publish or cancel.
    withContext(Dispatchers.Main) {}
}
fun main() = runBlocking {
    val failures = mutableListOf<String>()
    suspend fun case(name: String, test: suspend () -> Unit) {
        try { test(); println("PASS: $name") }
        catch (failure: Throwable) { failures += name; println("FAIL: $name: ${failure.message}") }
    }
    try {
        case("normal result publishes apps, membership and bounded focus") {
            val (vm, task) = start()
            try {
                withContext(Dispatchers.Main) { vm.appRepository.rows = listOf(Mapping("b"), Mapping("a"), Mapping("c")) }
                // The already queued calculation captured the original mapping order.
                release(task)
                withContext(Dispatchers.Main) {
                    check(vm.packages == listOf("a", "b", "c"))
                    check(vm.memberships == setOf("a", "b", "c"))
                    check(vm.selected == 2)
                }
            } finally { withContext(Dispatchers.Main) { vm.close() } }
        }
        case("enter, move and exit reorder cannot be overwritten by the suspended old filter") {
            val (vm, task) = start()
            try {
                withContext(Dispatchers.Main) {
                    vm.select(1)
                    vm.enterReorderMode()
                    vm.replacePackages("c", "a", "b")
                    vm.select(0)
                    vm.exitReorderMode()
                }
                release(task)
                withContext(Dispatchers.Main) {
                    check(vm.packages == listOf("c", "a", "b")) { "Old filter overwrote the edited order: ${vm.packages}" }
                    check(vm.selected == 0) { "Old filter changed the edited focus" }
                }
            } finally { withContext(Dispatchers.Main) { vm.close() } }
        }
        case("optimistic app removal cannot be overwritten by a suspended filter") {
            val (vm, task) = start()
            try {
                withContext(Dispatchers.Main) { vm.replacePackages("b", "c"); vm.select(1) }
                release(task)
                withContext(Dispatchers.Main) {
                    check(vm.packages == listOf("b", "c")) { "Old filter resurrected an optimistically removed app: ${vm.packages}" }
                    check(vm.selected == 1)
                }
            } finally { withContext(Dispatchers.Main) { vm.close() } }
        }
        case("replacing the filter task prevents the cancelled task from publishing later") {
            val (vm, old) = start()
            try {
                withContext(Dispatchers.Main) {
                    vm.appRepository.rows = listOf(Mapping("c"), Mapping("b"), Mapping("a"))
                    vm.startFilter()
                }
                val fresh = Dispatchers.Default.take()
                release(fresh)
                release(old)
                withContext(Dispatchers.Main) {
                    check(vm.packages == listOf("c", "b", "a")) { "Cancelled filter published its old order" }
                }
            } finally { withContext(Dispatchers.Main) { vm.close() } }
        }
        check(failures.isEmpty()) { "Filter publication regressions: $failures" }
    } finally { Dispatchers.close() }
}
''',
}

with tempfile.TemporaryDirectory(prefix="odin-launcher-filter-") as folder:
    folder = Path(folder)
    paths = [ROOT / "app/src/main/java/com/odin/desktop/data/model/InstalledApp.kt",
             ROOT / "app/src/main/java/com/odin/desktop/data/model/AppOrdering.kt",
             ROOT / "app/src/main/java/com/odin/desktop/ui/navigation/FocusZone.kt"]
    for name, contents in sources.items():
        path = folder / f"{name}.kt"
        path.write_text(contents)
        paths.append(path)
    cp = os.pathsep.join(map(str, compiler))
    runtime = os.pathsep.join(map(str, [compiler[1], annotations, coroutines]))
    output = folder / "classes"
    compiled = subprocess.run([str(java), "-cp", cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                               "-no-stdlib", "-no-reflect", "-nowarn", "-classpath", runtime,
                               "-d", str(output), *map(str, paths)], timeout=60)
    if compiled.returncode:
        raise SystemExit(compiled.returncode)
    raise SystemExit(subprocess.run([str(java), "-cp", str(output) + os.pathsep + runtime,
                                    "regression.TestsKt"], timeout=30).returncode)
