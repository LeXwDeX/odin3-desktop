#!/usr/bin/env python3
"""Verify the installed grip policy with an isolated fixture; never changes a game's data."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
adb = [str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb'), '-s', args.serial]
fixture = 'com.odin.desktop.validationtarget'

def shell(*command):
    return subprocess.check_output(adb + ['shell', *command], text=True, timeout=15).strip()

assert shell('getprop', 'persist.demo.rotationlock') == 'true', 'Select grip orientation in CONFIG first'
assert shell('settings', 'get', 'system', 'force_landscape') == '0', 'OEM force-landscape overlay must be off'
assert shell('wm', 'fixed-to-user-rotation') == 'disabled', 'Portrait requests must be allowed'
cases = [(0, 'LANDSCAPE', 1), (6, 'SENSOR_LANDSCAPE', 1), (8, 'REVERSE_LANDSCAPE', 1),
         (1, 'PORTRAIT', 0), (7, 'SENSOR_PORTRAIT', 0), (9, 'REVERSE_PORTRAIT', 2)]
report = []
try:
    for value, name, expected in cases:
        shell('am', 'force-stop', fixture)
        shell('am', 'start', '-W', '-n', fixture + '/.TargetActivity', '--ei', 'orientation', str(value))
        deadline = time.monotonic() + 8
        while True:
            state = shell('dumpsys', 'window', 'displays')
            rotation = re.search(r'^    mRotation=(\d)', state, re.M)
            if 'mCurrentAppOrientation=SCREEN_ORIENTATION_' + name + '\n' in state and rotation:
                actual = int(rotation[1])
                if actual == expected: break
            if time.monotonic() > deadline: raise AssertionError(f'{name}: expected rotation {expected}\n{state}')
            time.sleep(.2)
        report.append(dict(request=name, rotation=actual, passed=True))
        print(report[-1], flush=True)
finally:
    shell('am', 'force-stop', fixture)
    shell('am', 'start', '-W', '-n', 'com.odin.desktop/.ui.MainActivity')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
