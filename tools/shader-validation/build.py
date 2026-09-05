#!/usr/bin/env python3
"""Build an isolated overlay-validation target with the project-local SDK. Does not install it."""
from pathlib import Path
import os
import subprocess
import zipfile
root = Path(__file__).resolve().parents[2]
source = Path(__file__).resolve().parent
output = root / ".android-local/shader-validation"
classes = output / "classes"
classes.mkdir(parents=True, exist_ok=True)
sdk = Path(os.environ["ANDROID_HOME"])
tools = sdk / "build-tools/35.0.0"
android = sdk / "platforms/android-35/android.jar"
subprocess.run(["javac", "--release", "8", "-cp", str(android), "-d", str(classes), str(source / "TargetActivity.java")], check=True)
subprocess.run([str(tools / "d8"), "--min-api", "29", "--lib", str(android), "--output", str(output), *map(str, classes.rglob("*.class"))], check=True)
apk = output / "target.apk"
subprocess.run([str(tools / "aapt2"), "link", "-I", str(android), "--manifest", str(source / "AndroidManifest.xml"), "--min-sdk-version", "29", "--target-sdk-version", "35", "-o", str(apk)], check=True)
with zipfile.ZipFile(apk, "a") as archive:
    archive.write(output / "classes.dex", "classes.dex")
subprocess.run([str(tools / "apksigner"), "sign", "--ks", str(root / ".android-local/android-user/debug.keystore"), "--ks-pass", "pass:android", str(apk)], check=True)
print(apk)
