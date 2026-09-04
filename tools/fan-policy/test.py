#!/usr/bin/env python3
"""Run fan coordination regression tests with a JDK (JAVA_HOME is optional)."""

import os
from pathlib import Path
import subprocess
import tempfile


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    java_home = os.environ.get("JAVA_HOME")
    java_bin = Path(java_home) / "bin" if java_home else None
    javac = str(java_bin / "javac") if java_bin else "javac"
    java = str(java_bin / "java") if java_bin else "java"
    sources = [
        root / "app/src/main/java/com/odin/desktop/service/fan/FanControlCoordinator.java",
        root / "tools/fan-policy/FanControlCoordinatorSelfTest.java",
    ]
    with tempfile.TemporaryDirectory(prefix="odin-fan-policy-") as output:
        subprocess.run([javac, "-d", output, *map(str, sources)], check=True)
        subprocess.run([java, "-cp", output, "FanControlCoordinatorSelfTest"], check=True)


if __name__ == "__main__":
    main()
