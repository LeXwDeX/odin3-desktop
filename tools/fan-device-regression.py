#!/usr/bin/env python3
"""Verify manual MAX across OEM performance changes; restore the exact initial settings."""
import argparse
import importlib.util
import json
from pathlib import Path
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
spec = importlib.util.spec_from_file_location('bridge_manage', Path(__file__).parent / 'hardware-bridge/manage.py')
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)
bridge.verify_target(args.serial)
prefs = ET.fromstring(bridge.app_shell(args.serial, 'cat shared_prefs/odin_desktop_prefs.xml'))
auto = prefs.find("boolean[@name='auto_fan_control_enabled']")
if auto is None or auto.attrib['value'] != 'false':
    parser.error('Select manual fan control in the desktop before this manual-mode regression.')
token = bridge.read_token(args.serial)  # Remains in memory; never printed or persisted.
report = {'samples': []}


def request(body):
    reply = bridge.request(args.serial, token, body)
    if not reply.startswith('OK\t'):
        raise RuntimeError(reply)


def sample(label):
    values = bridge.shell(args.serial, 'getprop persist.vendor.debug.mode; settings get system performance_mode; '
                          'settings get system fan_mode; cat /sys/class/gpio5_pwm2/state '
                          '/sys/class/gpio5_pwm2/duty /sys/class/gpio5_pwm2/period /sys/class/gpio5_pwm2/speed').decode().splitlines()
    result = dict(zip(('performance', 'mirror', 'fan', 'state', 'duty', 'period', 'speed'), values))
    result['label'] = label
    report['samples'].append(result)
    print(json.dumps(result), flush=True)
    return result


baseline = sample('baseline')
if baseline['fan'] not in ('0', '4', '5') or baseline['performance'] != baseline['mirror']:
    parser.error('Require a supported fan preset and matching performance mirror before mutating the device.')
report['baseline'] = baseline
args.output.write_text(json.dumps(report, indent=2))  # Save recovery values before any mutation.
try:
    request('SET\tfan_mode\t5')
    for performance in (1, 2, 0, 2, 1, 0):
        request('PERFORMANCE\t' + str(performance))
        for pause in (0, 0.8, 4.2):
            time.sleep(pause)
            state = sample(f'performance={performance}, additional_wait={pause}')
            assert state['performance'] == state['mirror'] == str(performance), state
            assert state['fan'] == '5' and state['state'] == '1' and state['duty'] == '25000', state
    report['result'] = 'PASS'
    print('PASS: all six directed transitions retain MAX through the OEM 4-second control period.')
except BaseException:
    report['result'] = 'FAIL'
    raise
finally:
    try:
        request('PERFORMANCE_FAN\t' + baseline['performance'] + '\t' +
                ('4' if baseline['performance'] != '0' and baseline['fan'] == '0' else baseline['fan']))
        request('SET\tfan_mode\t' + baseline['fan'])
        report['restored'] = sample('restored')
    finally:
        args.output.write_text(json.dumps(report, indent=2))
