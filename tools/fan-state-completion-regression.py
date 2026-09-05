#!/usr/bin/env python3
"""Execute the current HC completion method with the real fan coordinator and counting fakes.

This checks completion ordering and rejection paths offline, not Android broadcast delivery.
"""
import os
from pathlib import Path
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
compiler.append(jar("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330"))
annotations = next((CACHE / "org.jetbrains/annotations").glob("*/*/*.jar"))
compiler.append(annotations)
java_bin = Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@17")) / "bin"
controller = ROOT / "app/src/main/java/com/odin/desktop/service/fan/HardwareController.kt"
coordinator = ROOT / "app/src/main/java/com/odin/desktop/service/fan/FanControlCoordinator.java"
source = controller.read_text()
method = re.search(r"    @Synchronized\n    fun applyFanPolicy\([\s\S]*?(?=\n    private fun fanBackend)", source)
constant = re.search(r'    const val ACTION_FAN_STATE_CHANGED = "[^"\n]+"', source)
if not method or not constant:
    raise SystemExit("HC completion entry point changed; update extraction without copying its implementation")

stubs = {
    "content": '''package android.content
import com.odin.desktop.service.fan.FanControlCoordinator
class Context(val backend: FanControlCoordinator.Backend) {
    val packageName = "com.odin.desktop"
    val broadcasts = mutableListOf<Intent>()
    val fanAtBroadcast = mutableListOf<Int>()
    fun sendBroadcast(intent: Intent) { broadcasts += intent; fanAtBroadcast += backend.readFan() }
}
class Intent(val action: String) {
    var targetPackage: String? = null
    fun setPackage(value: String): Intent { targetPackage = value; return this }
}
''',
    "controller": '''package com.odin.desktop.service.fan
import android.content.Context
import android.content.Intent
object HardwareController {
    const val FAN_OFF = 0
    val fanControl = FanControlCoordinator()
    private fun fanBackend(context: Context) = context.backend
''' + constant.group() + "\n" + method.group() + "\n}\n",
    "test": '''package com.odin.desktop.service.fan
import android.content.Context
class Backend: FanControlCoordinator.Backend {
    var performance = 0
    var fan = 4
    var auto = true
    var failWrite = false
    override fun readPerformance() = performance
    override fun readFan() = fan
    override fun readConfiguredFan() = fan
    override fun readAutoEnabled() = auto
    override fun writeAutoEnabled(enabled: Boolean) { auto = enabled }
    override fun writeFan(mode: Int) { check(!failWrite) { "simulated rejected driver write" }; fan = mode }
    override fun writePerformanceAndFan(perf: Int, target: Int) { performance = perf; fan = target }
}
fun main() {
    val backend = Backend()
    val context = Context(backend)
    val control = HardwareController.fanControl
    check(HardwareController.applyFanPolicy(context, control.snapshot(backend), 0) { true })
    check(context.broadcasts.size == 1) { "A completed automatic OFF must publish a final refresh signal" }
    check(context.broadcasts.single().action == HardwareController.ACTION_FAN_STATE_CHANGED)
    check(context.broadcasts.single().targetPackage == context.packageName)
    check(context.fanAtBroadcast.single() == 0) { "Completion must follow the verified driver write" }
    check(HardwareController.applyFanPolicy(context, control.snapshot(backend), 0) { true })
    check(context.broadcasts.size == 1) { "Unchanged periodic policy must not broadcast a write completion" }
    val stale = control.snapshot(backend)
    control.setManualFan(backend, 5)
    check(!HardwareController.applyFanPolicy(context, stale, 0) { true })
    check(context.broadcasts.size == 1) { "Rejected old policy must not announce completion" }
    control.setAutoEnabled(backend, true)
    val current = control.snapshot(backend)
    check(!HardwareController.applyFanPolicy(context, current, 0) { false })
    check(context.broadcasts.size == 1) { "Superseded foreground policy must not announce completion" }
    backend.failWrite = true
    val failed = runCatching { HardwareController.applyFanPolicy(context, current, 0) { true } }
    check(failed.isFailure)
    check(context.broadcasts.size == 1) { "Failed hardware writes must not claim verified completion" }
    backend.failWrite = false
    check(HardwareController.applyFanPolicy(context, control.snapshot(backend), 0) { true })
    check(context.broadcasts.size == 2 && context.fanAtBroadcast.last() == 0)
    backend.auto = false
    backend.fan = 5
    check(HardwareController.applyFanPolicy(context, control.snapshot(backend), 4) { true })
    check(backend.fan == 5 && context.broadcasts.size == 2) { "Releasing stale ownership without a write is not a completion" }
    println("PASS: actual HC completion method; verified ordering, package scope, no-op, stale request and failure paths")
}
''',
}

with tempfile.TemporaryDirectory(prefix="odin-fan-completion-") as output:
    workspace = Path(output)
    classes = workspace / "classes"
    classes.mkdir()
    subprocess.run([str(java_bin / "javac"), "-d", str(classes), str(coordinator)], check=True)
    paths = []
    for name, content in stubs.items():
        path = workspace / f"{name}.kt"
        path.write_text(content)
        paths.append(str(path))
    runtime = os.pathsep.join(map(str, [classes, compiler[1], annotations]))
    subprocess.run([str(java_bin / "java"), "-cp", os.pathsep.join(map(str, compiler)),
                    "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-nowarn",
                    "-classpath", runtime, "-d", str(classes), *paths], check=True)
    subprocess.run([str(java_bin / "java"), "-cp", runtime, "com.odin.desktop.service.fan.TestKt"], check=True)
