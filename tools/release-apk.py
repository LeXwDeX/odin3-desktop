#!/usr/bin/env python3
"""Validate release tags and sign an APK using ephemeral, environment-only secrets."""
import argparse
import base64
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def version_info(tag):
    match = re.fullmatch(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", tag)
    if not match:
        raise ValueError("Use vMAJOR.MINOR.PATCH without leading zeroes or prerelease suffixes")
    major, minor, patch = map(int, match.groups())
    if major > 2099 or minor > 999 or patch > 999:
        raise ValueError("Version exceeds Android versionCode range (major <= 2099, minor/patch <= 999)")
    return tag[1:], major * 1_000_000 + minor * 1_000 + patch + 1


def sign(tag):
    version, code = version_info(tag)
    required = ("ANDROID_KEYSTORE_BASE64", "ANDROID_STORE_PASSWORD", "ANDROID_KEY_ALIAS",
                "ANDROID_KEY_PASSWORD", "ANDROID_SIGNING_CERT_SHA256", "ANDROID_HOME")
    missing = [name for name in required if not os.environ.get(name)]
    if missing:
        raise ValueError("Missing signing configuration: " + ", ".join(missing))
    expected_cert = os.environ["ANDROID_SIGNING_CERT_SHA256"].replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected_cert):
        raise ValueError("ANDROID_SIGNING_CERT_SHA256 must be a SHA-256 certificate fingerprint")
    sdk = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    unsigned = ROOT / "app/build/outputs/apk/release/app-release-unsigned.apk"
    output = ROOT / "dist"
    output.mkdir(exist_ok=True)
    apk = output / f"odin3-desktop-{tag}.apk"
    if apk.exists():
        raise ValueError("Output APK already exists; use a clean output directory")
    with tempfile.TemporaryDirectory(prefix="odin-sign-", dir=os.environ.get("RUNNER_TEMP")) as temp:
        temp = Path(temp)
        key = temp / "signing.keystore"
        key.write_bytes(base64.b64decode(os.environ["ANDROID_KEYSTORE_BASE64"], validate=True))
        key.chmod(0o600)
        aligned = temp / "aligned.apk"
        signed = temp / "signed.apk"
        subprocess.run([str(sdk / "zipalign"), "-P", "16", "-f", "4", str(unsigned), str(aligned)], check=True)
        subprocess.run([str(sdk / "apksigner"), "sign", "--ks", str(key),
                        "--ks-key-alias", os.environ["ANDROID_KEY_ALIAS"],
                        "--ks-pass", "env:ANDROID_STORE_PASSWORD", "--key-pass", "env:ANDROID_KEY_PASSWORD",
                        "--v4-signing-enabled", "false", "--out", str(signed), str(aligned)], check=True)
        verification = subprocess.check_output(
            [str(sdk / "apksigner"), "verify", "--verbose", "--print-certs", str(signed)], text=True)
        certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", verification)
        if [cert.lower() for cert in certificates] != [expected_cert]:
            raise ValueError("APK signer does not match the configured certificate")
        badging = subprocess.check_output([str(sdk / "aapt"), "dump", "badging", str(signed)], text=True)
        package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
        if not package or package.groups() != ("com.odin.desktop", str(code), version):
            raise ValueError("APK package/version does not match the release tag")
        if "application-debuggable" in badging:
            raise ValueError("Refusing to publish a debuggable APK")
        # Only a verified APK leaves the temporary directory; private material is always removed.
        apk.write_bytes(signed.read_bytes())
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    apk.with_suffix(".apk.sha256").write_text(f"{digest}  {apk.name}\n")
    (output / "release-notes.md").write_text(
        f"Android 10+ · `com.odin.desktop` · versionCode `{code}`\n\n"
        f"下载 **{apk.name}** 安装；同签名旧版可以直接覆盖并保留数据。\n\n"
        f"APK SHA-256: `{digest}`\n\n"
        f"签名证书 SHA-256: `{expected_cert}`\n\n"
        f"构建提交: `{os.environ.get('GITHUB_SHA', 'local')}`\n")
    print(f"Verified {apk.name}: versionName={version}, versionCode={code}, non-debuggable")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("prepare", "sign"))
    parser.add_argument("tag")
    args = parser.parse_args()
    version, code = version_info(args.tag)
    if args.command == "prepare":
        print(f"tag={args.tag}\nversion={version}\nversion_code={code}")
    else:
        sign(args.tag)


if __name__ == "__main__":
    main()
