#!/usr/bin/env python3
"""Provision or stop the authenticated Odin hardware bridge through an authorized ADB connection."""
import argparse
import contextlib
import datetime
import hashlib
import hmac
from pathlib import Path
import re
import secrets
import shlex
import socket
import subprocess
import time

APP = "com.odin.desktop"
REMOTE = "/data/local/tmp/odin-hardware-bridge"
PRIVATE = "no_backup/hardware_bridge"
PORT = 18889


class BridgeError(RuntimeError):
    pass


class BridgeOffline(BridgeError):
    pass


def adb(serial, *arguments, data=None):
    try:
        result = subprocess.run(["adb", "-s", serial, *arguments], input=data, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=20, check=False)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise BridgeError("ADB operation unavailable or timed out.") from error
    if result.returncode:
        # Never expose subprocess output: token reads are deliberately captured only in memory.
        raise BridgeError("ADB operation rejected; verify the target and debug app installation.")
    return result.stdout


def shell(serial, script, data=None):
    return adb(serial, "shell", "-T", shlex.join(["sh", "-c", script]), data=data)


def app_shell(serial, script, data=None):
    return adb(serial, "shell", "-T", shlex.join(["run-as", APP, "sh", "-c", script]), data=data)


def verify_target(serial):
    devices = subprocess.run(["adb", "devices"], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                             timeout=10, check=True).stdout.decode("utf-8", "replace")
    if not any(line.split() == [serial, "device"] for line in devices.splitlines()):
        raise BridgeError("The selected ADB serial is not online and authorized.")
    if shell(serial, "/system/bin/am get-current-user").strip() != b"0":
        raise BridgeError("This bridge is restricted to Android user 0.")
    if shell(serial, "/system/bin/id -u").strip() != b"2000":
        raise BridgeError("Require normal ADB shell UID 2000; root ADB is not supported.")
    if not shell(serial, "/system/bin/pm path " + APP).startswith(b"package:"):
        raise BridgeError("com.odin.desktop is not installed.")


def read_token(serial):
    script = f"set -eu; test ! -L {REMOTE}; test ! -L {REMOTE}/token; if [ -f {REMOTE}/token ]; then cat {REMOTE}/token; fi"
    token = shell(serial, script).decode("ascii").strip()
    if not token:
        return None
    if not re.fullmatch(r"[0-9a-f]{64}", token):
        raise BridgeError("Existing bridge token is invalid; it has not been overwritten.")
    return bytes.fromhex(token)


@contextlib.contextmanager
def forwarded(serial):
    port = adb(serial, "forward", "tcp:0", f"tcp:{PORT}").strip().decode("ascii")
    if not port.isdigit():
        raise BridgeError("Could not create the temporary ADB forward.")
    try:
        yield int(port)
    finally:
        adb(serial, "forward", "--remove", "tcp:" + port)


def read_line(stream):
    try:
        value = stream.readline(1025)
    except ConnectionResetError as error:
        raise BridgeOffline("Hardware bridge is offline; reconnect using start.") from error
    if not value:
        raise BridgeOffline("Hardware bridge is offline; reconnect using start.")
    if len(value) > 1024 or not value.endswith(b"\n") or any(c != 9 and not 32 <= c <= 126 for c in value[:-1]):
        raise BridgeError("Bridge sent an invalid response.")
    return value[:-1].decode("ascii")


def signature(token, payload):
    return hmac.new(token, payload.encode("ascii"), hashlib.sha256).hexdigest()


def request(serial, token, body):
    with forwarded(serial) as port:
        try:
            connection = socket.create_connection(("127.0.0.1", port), timeout=3)
        except OSError as error:
            raise BridgeOffline("Hardware bridge is offline; reconnect using start.") from error
        with connection:
            connection.settimeout(30)
            with connection.makefile("rb") as stream:
                greeting = read_line(stream).split("\t")
                if (token is None or len(greeting) != 3 or greeting[0] != "ODIN1" or
                        not re.fullmatch(r"[0-9a-f]{64}", greeting[1]) or
                        not hmac.compare_digest(signature(token, "SERVER\n" + greeting[1]), greeting[2])):
                    raise BridgeError("Port 18889 has an unauthenticated listener; no changes were made.")
                nonce = greeting[1]
                wire = body + "\t" + signature(token, "CLIENT\n" + nonce + "\n" + body) + "\n"
                connection.sendall(wire.encode("ascii"))
                response = read_line(stream).rsplit("\t", 1)
                if len(response) != 2 or not hmac.compare_digest(signature(token, "RESPONSE\n" + nonce + "\n" + response[0]), response[1]):
                    raise BridgeError("Bridge reply authentication failed.")
                return response[0]


def wait_stopped(serial, token):
    for _ in range(20):
        try:
            request(serial, token, "PING")
        except BridgeOffline:
            return
        time.sleep(0.1)
    raise BridgeError("The previous bridge has not stopped; token was not rotated.")


def provision_script(directory, suffix):
    # The secret is delivered over stdin, never interpolated into shell text or argv.
    return f"""set -eu
umask 077
test ! -L {directory}
mkdir -p {directory}
chmod 700 {directory}
test ! -L {directory}/token
test ! -L {directory}/token.new
if [ -e {directory}/token ]; then
  test -f {directory}/token
  cp -p {directory}/token {directory}/token.bak.{suffix}
  chmod 600 {directory}/token.bak.{suffix}
fi
cat > {directory}/token.new
chmod 600 {directory}/token.new
mv {directory}/token.new {directory}/token
"""


def start(serial, jar):
    if not jar.is_file():
        raise BridgeError("Build bridge.jar before starting the bridge.")
    # Verify run-as before stopping an existing bridge or modifying either token.
    app_uid = app_shell(serial, "id -u").strip()
    if not app_uid.isdigit() or int(app_uid) < 10000:
        raise BridgeError("The installed debug app does not permit private token provisioning.")
    old = read_token(serial)
    try:
        result = request(serial, old, "PING")
    except BridgeOffline:
        result = None
    if result is not None:
        if result != "OK\tREADY" or request(serial, old, "STOP") != "OK\tSTOPPED":
            raise BridgeError("Existing bridge did not acknowledge stop; nothing overwritten.")
        wait_stopped(serial, old)

    suffix = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    token = secrets.token_bytes(32)
    payload = token.hex().encode("ascii") + b"\n"
    shell(serial, provision_script(REMOTE, suffix), data=payload)
    app_shell(serial, provision_script(PRIVATE, suffix), data=payload)
    paired = app_shell(serial, f"cat {PRIVATE}/token").strip()
    if not hmac.compare_digest(paired, payload.strip()):
        raise BridgeError("Private token pairing did not read back correctly; bridge was not started.")
    # Capture exact previous artifact inside the shell-private directory before replacing it.
    shell(serial, f"set -eu; test ! -L {REMOTE}/bridge.jar; test ! -L {REMOTE}/bridge.jar.new; "
                  f"if [ -e {REMOTE}/bridge.jar ]; then test -f {REMOTE}/bridge.jar; "
                  f"cp -p {REMOTE}/bridge.jar {REMOTE}/bridge.jar.bak.{suffix}; fi")
    adb(serial, "push", str(jar.resolve()), REMOTE + "/bridge.jar.new")
    shell(serial, f"set -eu; chmod 400 {REMOTE}/bridge.jar.new; mv {REMOTE}/bridge.jar.new {REMOTE}/bridge.jar; "
                  f"umask 077; CLASSPATH={REMOTE}/bridge.jar nohup /system/bin/app_process /system/bin "
                  f"--nice-name=odin-hardware-bridge com.odin.hardware.OdinHardwareBridge {REMOTE}/token "
                  f"</dev/null >{REMOTE}/bridge.log 2>&1 &")
    for _ in range(30):
        try:
            if request(serial, token, "PING") == "OK\tREADY":
                print("Hardware bridge connected on loopback 18889. No hardware settings were changed.")
                print(f"Existing tokens/artifact, if any, backed up in their private directories with suffix .bak.{suffix}")
                return
        except BridgeOffline:
            pass
        time.sleep(0.2)
    raise BridgeError("Bridge did not start. Inspect the shell-private bridge.log; no hardware action was requested.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("start", "status", "stop"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--jar", type=Path, default=Path(__file__).resolve().parent / "build/bridge.jar")
    args = parser.parse_args()
    try:
        verify_target(args.serial)
        if args.action == "start":
            start(args.serial, args.jar)
        else:
            token = read_token(args.serial)
            if token is None:
                raise BridgeOffline("Hardware bridge has not been provisioned.")
            body = "PING" if args.action == "status" else "STOP"
            expected = "OK\tREADY" if args.action == "status" else "OK\tSTOPPED"
            if request(args.serial, token, body) != expected:
                raise BridgeError("Hardware bridge did not acknowledge the management request.")
            print("Hardware bridge connected." if args.action == "status" else "Hardware bridge stopped; hardware settings were preserved.")
    except (BridgeError, OSError, subprocess.SubprocessError) as error:
        # Only BridgeError messages are controlled and safe; other errors may contain subprocess details.
        parser.exit(1, (str(error) if isinstance(error, BridgeError) else "ADB/bridge operation failed.") + "\n")


if __name__ == "__main__":
    main()
