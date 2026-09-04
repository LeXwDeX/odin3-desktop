#!/usr/bin/env python3
"""Authorized live check: wakes the selected device, opens Home, sends HOME/BACK.

Reports process CPU ticks and Android lifecycle counts. This includes normal input
handling; compare the same sequence on the old and new APK. No fan settings change.
"""
import argparse
import json
import re
import subprocess
import time
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", required=True)
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--count", type=int, default=60)
parser.add_argument("--interval", type=float, default=0.05)
args = parser.parse_args()
if not 1 <= args.count <= 100 or not 0.05 <= args.interval <= 2:
    raise SystemExit("Use 1–100 presses with a 0.05–2 second gap")
adb = ["adb", "-s", args.serial]


def shell(*command):
    return subprocess.check_output([*adb, "shell", *command], text=True).strip()


def foreground_activity():
    return next((line.strip() for line in shell("dumpsys", "activity", "activities").splitlines()
                 if line.lstrip().startswith("ResumedActivity:")), "unknown")


devices = subprocess.check_output(["adb", "devices"], text=True)
if not re.search(rf"^{re.escape(args.serial)}\s+device$", devices, re.M):
    raise SystemExit("Expected authorized device is not connected")
shell("input", "keyevent", "WAKEUP")
shell("input", "keyevent", "HOME")
shell("input", "keyevent", "BACK", "BACK", "BACK")
time.sleep(1)
pids = {name: shell("pidof", name).split()[0] for name in
        ("com.odin.desktop", "system_server", "surfaceflinger")}
hz = int(shell("getconf", "CLK_TCK"))


def sample():
    stats = shell("cat", *(f"/proc/{pid}/stat" for pid in pids.values())).splitlines()
    ticks = {}
    for name, stat in zip(pids, stats):
        fields = stat.rsplit(")", 1)[1].split()
        ticks[name] = int(fields[11]) + int(fields[12])
    return ticks


def phase(name, key=None):
    logger = subprocess.Popen([*adb, "logcat", "-b", "events", "-v", "brief", "-T", "1"],
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    before = sample()
    started = time.monotonic()
    try:
        if key:
            # One input process per key plus a bounded pause avoids an unrealistic
            # zero-delay Binder flood; use the identical cadence for before/after.
            shell(f'for key in {" ".join([key] * args.count)}; do input keyevent "$key"; sleep {args.interval}; done')
            time.sleep(1)
        else:
            time.sleep(3)
        after = sample()
        elapsed = time.monotonic() - started
        time.sleep(0.2)
    finally:
        logger.terminate()
    logs, _ = logger.communicate(timeout=5)
    events = {}
    for line in logs.splitlines():
        if "com.odin.desktop.ui.MainActivity" in line:
            match = re.match(r"I/(\w+)\(", line)
            if match:
                event = match.group(1)
                events[event] = events.get(event, 0) + 1
    result = {"phase": name, "presses": args.count if key else 0,
              "seconds": round(elapsed, 3), "lifecycle": events,
              "cpu_ticks": {name: after[name] - before[name] for name in pids},
              "cpu_percent_one_core": {name: round((after[name] - before[name]) / hz / elapsed * 100, 2)
                                       for name in pids}}
    result["foreground_activity"] = foreground_activity()
    result["valid_launcher_sample"] = "com.odin.desktop/.ui.MainActivity" in result["foreground_activity"]
    print(json.dumps(result, ensure_ascii=False), flush=True)
    return result


results = {"serial": args.serial, "pids": pids, "clock_ticks_per_second": hz,
           "interval_seconds": args.interval, "samples": []}
for name, key in [("idle", None), ("HOME", "HOME"), ("BACK", "BACK")]:
    try:
        results["samples"].append(phase(name, key))
        if not results["samples"][-1]["valid_launcher_sample"]:
            results["error"] = "Launcher lost foreground; sample is invalid (for example a system resolver appeared)"
            args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n")
            raise SystemExit(results["error"])
    except (subprocess.CalledProcessError, ValueError) as failure:
        results["error"] = str(failure)
        args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n")
        raise
results["final_activity"] = [line.strip() for line in shell("dumpsys", "activity", "activities").splitlines()
                             if "mResumedActivity" in line or "topResumedActivity" in line]
args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n")
print(f"Saved {args.output}")
