#!/usr/bin/env python3
"""Build the fixed-function bridge and run its JVM transaction tests. No device access."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", required=True, type=Path)
    parser.add_argument("--java-home", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    output = root / "build"
    classes = output / "classes"
    tests = output / "tests"
    classes.mkdir(parents=True, exist_ok=True)
    tests.mkdir(parents=True, exist_ok=True)
    jdk = args.java_home or (Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None)
    javac = str(jdk / "bin/javac") if jdk else shutil.which("javac")
    java = str(jdk / "bin/java") if jdk else shutil.which("java")
    d8 = args.sdk / "build-tools/35.0.0/d8"
    android = args.sdk / "platforms/android-35/android.jar"
    if not javac or not java or not d8.is_file() or not android.is_file():
        parser.error("Require a JDK, Android build-tools 35.0.0, and platform android-35.")
    subprocess.run([javac, "--release", "8", "-d", str(classes), str(root / "src/OdinHardwareBridge.java"),
                    str(root.parents[1] / "app/src/main/java/com/odin/hardware/HardwareOperations.java"),
                    str(root.parents[1] / "app/src/main/java/com/odin/hardware/OemCommandCodec.java")], check=True)
    subprocess.run([javac, "--release", "8", "-cp", str(classes), "-d", str(tests), str(root / "test/HardwareBridgeSelfTest.java")], check=True)
    subprocess.run([java, "-cp", os.pathsep.join((str(classes), str(tests))), "com.odin.hardware.HardwareBridgeSelfTest"], check=True)
    environment = dict(os.environ)
    if jdk:
        environment["JAVA_HOME"] = str(jdk)
    jar = output / "bridge.jar"
    # d8 overwrites the artifact; the source/test directories are separate.
    subprocess.run([str(d8), "--min-api", "29", "--lib", str(android), "--output", str(jar)] +
                   [str(path) for path in sorted(classes.rglob("*.class"))], check=True, env=environment)
    print(f"Built {jar}")


if __name__ == "__main__":
    main()
