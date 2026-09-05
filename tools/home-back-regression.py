#!/usr/bin/env python3
"""Replay launcher lifecycle/key storms against real Kotlin entry points.

Android services are counting fakes: this is a call-amplification regression,
not a replacement for physical-device CPU/frame-time acceptance.
Uses the Kotlin compiler already downloaded by this project's Gradle build.
"""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"


def jar(group, name, version):
    matches = list((CACHE / group / name / version).glob("*/*.jar"))
    if not matches:
        raise SystemExit(f"Run the Gradle build first: missing {name}:{version}")
    return matches[0]


compiler = [jar("org.jetbrains.kotlin", name, version) for name, version in [
    ("kotlin-compiler-embeddable", "2.0.0"), ("kotlin-stdlib", "2.0.0"),
    ("kotlin-script-runtime", "2.0.0"), ("kotlin-reflect", "1.6.10")]]
compiler += [jar("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330")]
annotations = next((CACHE / "org.jetbrains/annotations").glob("*/*/*.jar"))
compiler.append(annotations)
java = Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@17")) / "bin/java"
main = ROOT / "app/src/main/java/com/odin/desktop/ui/MainActivity.kt"
gamepad = ROOT / "app/src/main/java/com/odin/desktop/ui/navigation/GamepadKeyHandler.kt"
keys = sorted(set(re.findall(r"KeyEvent\.(KEYCODE_\w+)", gamepad.read_text() + main.read_text())))
stubs = {
    "appcompat": "package androidx.appcompat.app\nopen class AppCompatActivity : androidx.activity.ComponentActivity()\n",
    "content": '''package android.content
open class Context {
    val packageName = "com.odin.desktop"
    val contentResolver = Any()
    fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {}
    fun unregisterReceiver(receiver: BroadcastReceiver) {}
    fun startForegroundService(intent: Intent) {}
    fun startService(intent: Intent) {}
}
abstract class BroadcastReceiver { abstract fun onReceive(context: Context?, intent: Intent?) }
class Intent(context: Context? = null, cls: Class<*>? = null) {
    companion object { const val ACTION_PACKAGE_ADDED = "added"; const val ACTION_PACKAGE_REMOVED = "removed"; const val ACTION_PACKAGE_REPLACED = "replaced" }
}
class IntentFilter { fun addAction(action: String) {}; fun addDataScheme(scheme: String) {} }
''',
    "os": '''package android.os
class Bundle
object Build { object VERSION { const val SDK_INT = 35 }; object VERSION_CODES { const val O = 26 } }
''',
    "keys": '''package android.view
class KeyEvent(val action: Int, val keyCode: Int, val repeatCount: Int = 0, val eventTime: Long = 1L) {
    companion object { const val ACTION_DOWN = 0; const val ACTION_UP = 1;
''' + "\n".join(f"const val {key} = {index + 3}" for index, key in enumerate(keys)) + '''
    }
}
class Window { val decorView = Any() }
''',
    "settings": '''package android.provider
object Settings { object Secure {
    var reads = 0
    const val ENABLED_ACCESSIBILITY_SERVICES = "enabled"
    const val ACCESSIBILITY_ENABLED = "accessibility"
    fun getString(resolver: Any, key: String): String { reads++; return "com.odin.desktop/com.odin.desktop.service.fan.AppMonitorAccessibilityService" }
    fun putString(resolver: Any, key: String, value: String) {}
} }
''',
    "activity": '''package androidx.activity
import com.odin.desktop.ui.viewmodel.LauncherViewModel
open class ComponentActivity: android.content.Context() {
    val window = android.view.Window()
    val onBackPressedDispatcher = OnBackPressedDispatcher()
    var defaultKeyDispatches = 0
    open fun onCreate(state: android.os.Bundle?) {}
    open fun onStart() {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onStop() {}
    open fun onDestroy() {}
    open fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean { defaultKeyDispatches++; return false }
}
fun ComponentActivity.viewModels(): Lazy<LauncherViewModel> = lazy { LauncherViewModel.instance }
abstract class OnBackPressedCallback(val enabled: Boolean) { abstract fun handleOnBackPressed() }
class OnBackPressedDispatcher {
    val callbacks = mutableListOf<OnBackPressedCallback>()
    fun addCallback(owner: ComponentActivity, callback: OnBackPressedCallback) { callbacks += callback }
    fun onBackPressed() { callbacks.last().handleOnBackPressed() }
}
fun OnBackPressedDispatcher.addCallback(owner: ComponentActivity, enabled: Boolean = true, block: OnBackPressedCallback.() -> Unit) {
    addCallback(owner, object: OnBackPressedCallback(enabled) { override fun handleOnBackPressed() { block() } })
}
''',
    "compose": '''package androidx.activity.compose
fun androidx.activity.ComponentActivity.setContent(content: () -> Unit) {}
''',
    "lifecycle": '''package androidx.lifecycle
class Lifecycle { enum class State { STARTED } }
class Scope
val androidx.activity.ComponentActivity.lifecycleScope: Scope get() = Scope()
fun androidx.activity.ComponentActivity.repeatOnLifecycle(state: Lifecycle.State, block: () -> Unit) {}
''',
    "coroutines": '''package kotlinx.coroutines
fun androidx.lifecycle.Scope.launch(block: () -> Unit) {}
''',
    "window": '''package androidx.core.view
object WindowCompat { fun setDecorFitsSystemWindows(window: android.view.Window, fits: Boolean) {} }
object WindowInsetsCompat { object Type { fun systemBars() = 0 } }
class WindowInsetsControllerCompat(window: android.view.Window, decorView: Any) {
    var systemBarsBehavior = 0
    fun hide(types: Int) {}
    companion object { const val BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE = 0 }
}
''',
    "hardware": '''package com.odin.desktop.service.fan
object HardwareController {
    fun getOrientationMode(context: android.content.Context) = 0
    fun applyOrientation(context: android.content.Context, mode: Int) {}
    fun requestDefaultHomeRole(context: android.content.Context) {}
}
class AppMonitorAccessibilityService { companion object { const val isRunning = true } }
class FanWatchdogService
''',
    "dashboard": '''package com.odin.desktop.dashboard
class DashboardActions(context: android.content.Context) { fun execute(action: Any) {} }
''',
    "screen": '''package com.odin.desktop.ui.screens
fun LauncherScreen(viewModel: com.odin.desktop.ui.viewmodel.LauncherViewModel, onOrientationChange: (Int) -> Unit) {}
''',
    "theme": '''package com.odin.desktop.ui.theme
fun OdinDesktopTheme(content: () -> Unit) {}
''',
    "shader": '''package com.odin.desktop.shader.engine
object VideoShaderEngine {
    var foregroundUpdates = 0
    fun onForegroundPackageChanged(context: android.content.Context, name: String) { foregroundUpdates++ }
}
''',
    "viewmodel": '''package com.odin.desktop.ui.viewmodel
import com.odin.desktop.ui.navigation.FocusZone
class Value<T>(var value: T)
class Flow { fun collect(block: (Any) -> Unit) {} }
class LauncherViewModel {
    val hardware get() = this
    companion object { var instance = LauncherViewModel() }
    val focusZone = Value(FocusZone.APPS)
    val selectedDockIndex = Value(0)
    val isReorderingApps = Value(false)
    val dashboardActions = Flow()
    val requestRoleEvent = Flow()
    var scans = 0; var hardwareLoads = 0; var visibilityChanges = 0; var visible = false
    var backCalls = 0; var modalOpen = false
    fun refreshAppLanguage() {}
    fun scanInstalledApps() { scans++ }
    fun loadHardwareStates() { hardwareLoads++ }
    fun setLauncherVisible(value: Boolean) { if (value != visible) visibilityChanges++; visible = value }
    fun onBack(): Boolean { backCalls++; val handled = modalOpen; modalOpen = false; return handled }
''' + "\n".join(f"fun {name}() {{}}" for name in sorted(set(re.findall(r"viewModel\.(?:hardware\.)?(\w+)\(", gamepad.read_text())) - {"onBack"})) + '''
}
''',
    "test": '''package com.odin.desktop.ui
import android.view.KeyEvent
import com.odin.desktop.ui.viewmodel.LauncherViewModel

fun main() {
    val activity = MainActivity()
    activity.onCreate(null)
    activity.onStart()
    activity.onResume()
    val vm = LauncherViewModel.instance
    val initialScans = vm.scans
    val initialHardware = vm.hardwareLoads
    val initialVisibility = vm.visibilityChanges
    val initialAccessibility = android.provider.Settings.Secure.reads
    val initialShader = com.odin.desktop.shader.engine.VideoShaderEngine.foregroundUpdates
    repeat(100) { activity.onPause(); activity.onResume() }
    val extraScans = vm.scans - initialScans
    val extraHardware = vm.hardwareLoads - initialHardware
    val extraVisibility = vm.visibilityChanges - initialVisibility
    val extraAccessibility = android.provider.Settings.Secure.reads - initialAccessibility
    val extraShader = com.odin.desktop.shader.engine.VideoShaderEngine.foregroundUpdates - initialShader
    println("HOME x100: extra package scans=$extraScans hardware refreshes=$extraHardware visibility changes=$extraVisibility accessibility reads=$extraAccessibility shader refreshes=$extraShader")
    var failed = extraScans + extraHardware + extraVisibility + extraAccessibility + extraShader != 0
    repeat(100) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
    }
    println("BACK x100 at root: default Android dispatches=${activity.defaultKeyDispatches}")
    failed = failed || activity.defaultKeyDispatches != 0
    repeat(100) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B))
    }
    check(activity.defaultKeyDispatches == 0) { "Gamepad B must also stay at the launcher root" }
    vm.modalOpen = true
    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
    check(!vm.modalOpen) { "BACK must still close a modal" }
    val backCalls = vm.backCalls
    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, repeatCount = 5))
    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
    check(vm.backCalls == backCalls) { "Held BACK repeats must not cascade through navigation" }
    activity.onPause(); activity.onStop()
    check(!vm.visible) { "Dashboard work must stop after leaving the desktop" }
    activity.onStart(); activity.onResume()
    check(vm.visible) { "Returning from another app must restore dashboard work" }
    check(vm.hardwareLoads > initialHardware) { "Returning from another app must refresh hardware" }
    if (activity.onBackPressedDispatcher.callbacks.isNotEmpty()) {
        vm.modalOpen = true
        activity.onBackPressedDispatcher.onBackPressed()
        check(!vm.modalOpen) { "System/gesture Back must use the same modal navigation" }
        repeat(100) { activity.onBackPressedDispatcher.onBackPressed() }
    } else { println("System Back has no launcher callback"); failed = true }
    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, 999))
    check(activity.defaultKeyDispatches == 1) { "Unrelated keys must still reach Android" }
    check(!failed) { "Launcher work scales with redundant HOME/BACK input" }
    println("PASS: redundant input bounded; modal Back, held key, real background return verified")
}
''',
}
with tempfile.TemporaryDirectory(prefix="odin-home-back-") as folder:
    folder = Path(folder)
    sources = [main, gamepad, ROOT / "app/src/main/java/com/odin/desktop/ui/navigation/FocusZone.kt"]
    for name, source in stubs.items():
        path = folder / f"{name}.kt"
        path.write_text(source)
        sources.append(path)
    cp = os.pathsep.join(map(str, compiler))
    runtime = os.pathsep.join(map(str, [compiler[1], annotations]))
    output = folder / "classes"
    compile_result = subprocess.run([str(java), "-cp", cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                                    "-no-stdlib", "-no-reflect", "-nowarn", "-classpath", runtime,
                                    "-d", str(output), *map(str, sources)])
    if compile_result.returncode:
        raise SystemExit(compile_result.returncode)
    raise SystemExit(subprocess.run([str(java), "-cp", str(output) + os.pathsep + runtime,
                                    "com.odin.desktop.ui.TestKt"]).returncode)
