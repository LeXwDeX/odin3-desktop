#!/usr/bin/env python3
"""Install the project toolchain on Apple Silicon macOS without Homebrew."""
import hashlib
import argparse
import platform
from pathlib import Path
import runpy
import subprocess
import tempfile
import shutil

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / ".android-local"
DOWNLOADS = LOCAL / "downloads"
PACKAGES = [
    ("jdk17.tar.gz", "https://cdn.azul.com/zulu/bin/zulu17.66.19-ca-jdk17.0.19-macosx_aarch64.tar.gz",
     "f2bd5afaaaa4c23eb4bf2c78913c7eb7d3d228e44209ffec652fb72388a2f25c"),
    ("commandlinetools.zip", "https://dl.google.com/android/repository/commandlinetools-mac_arm64-15859902_latest.zip",
     "835b62a26162b229b441d1f6d4680383815a270809eb33522c0d480fa5002c4e"),
]


def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--install-skill", action="store_true", help="Install Google's android-cli skill for Codex in this project")
    args = parser.parse_args()
    if (platform.system(), platform.machine()) != ("Darwin", "arm64"):
        raise SystemExit("This bootstrap supports Apple Silicon macOS. Other hosts: docs/development.md")
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    for name, url, expected in PACKAGES:
        target = DOWNLOADS / name
        if not target.exists() or digest(target) != expected:
            partial = target.with_suffix(".partial")
            subprocess.run(["curl", "-fL", "--retry", "3", "--connect-timeout", "20", "--max-time", "600",
                            url, "-o", str(partial)], check=True)
            if digest(partial) != expected:
                raise SystemExit(f"Checksum mismatch: {name}; archive not installed")
            partial.replace(target)
    if not (LOCAL / "jdk/Contents/Home/bin/java").exists():
        (LOCAL / "jdk").mkdir(exist_ok=True)
        subprocess.run(["tar", "-xzf", str(DOWNLOADS / "jdk17.tar.gz"), "-C", str(LOCAL / "jdk"),
                        "--strip-components=1"], check=True)
    cmdline = LOCAL / "sdk/cmdline-tools/latest"
    if not (cmdline / "bin/sdkmanager").exists():
        cmdline.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=LOCAL) as stage:
            subprocess.run(["unzip", "-q", str(DOWNLOADS / "commandlinetools.zip"), "-d", stage], check=True)
            shutil.move(str(Path(stage) / "cmdline-tools"), cmdline)
    env = runpy.run_path(str(ROOT / "tools/android"))["environment"]()
    (LOCAL / "android-user").mkdir(exist_ok=True)
    cli = LOCAL / "bin/android"
    if not cli.is_file():
        cli.parent.mkdir(exist_ok=True)
        partial = DOWNLOADS / "android-cli.partial"
        subprocess.run(["curl", "-fL", "--retry", "3", "--connect-timeout", "20", "--max-time", "600",
                        "https://dl.google.com/android/cli/latest/darwin_arm64/android-cli",
                        "-o", str(partial)], check=True)
        partial.chmod(0o755)
        subprocess.run([str(partial), "--version"], env=env, check=True)
        partial.replace(cli)
    required = {
        "platform-tools": "platform-tools/adb",
        "platforms;android-35": "platforms/android-35/android.jar",
        "build-tools;34.0.0": "build-tools/34.0.0/aapt2",
        "build-tools;35.0.0": "build-tools/35.0.0/d8",
    }
    missing = [name for name, file in required.items()
               if not (LOCAL / "sdk" / file).is_file()
               or not (LOCAL / "sdk" / file).parent.joinpath("package.xml").is_file()]
    # Only contact the package server when something is missing. sdkmanager
    # displays licenses and asks interactively on first installation.
    if missing:
        subprocess.run([str(cmdline / "bin/sdkmanager"), "--sdk_root=" + env["ANDROID_HOME"],
                        *missing], env=env, check=True)
    if args.install_skill:
        subprocess.run([str(cli), "skills", "add", "android-cli", "--agent=codex", "--project=" + str(ROOT)],
                       env=env, check=True)
    print("Ready: tools/android ./gradlew :app:assembleDebug")


if __name__ == "__main__":
    main()
