#!/usr/bin/env python3
"""Measure production UI Kit components on an explicitly selected Debug device.

Run through tools/android. Only temporary locale/font settings and the Debug
component board are changed; both settings are restored in finally.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    adb = ['adb', '-s', args.serial]
    package = 'com.odin.desktop'
    activity = package + '/.ui.UiKitPreviewActivity'
    args.output.mkdir(parents=True, exist_ok=True)

    def shell(*command):
        return subprocess.check_output(adb + ['shell', *command], text=True).strip()

    assert subprocess.check_output(adb + ['get-state'], text=True).strip() == 'device'
    assert 'DEBUGGABLE' in shell('dumpsys', 'package', package), 'A Debug build is required'
    original_locale = re.search(r'\[(.*?)\]', shell('cmd', 'locale', 'get-app-locales', package)).group(1)
    original_scale = shell('settings', 'get', 'system', 'font_scale')
    results = []

    def read_bounds():
        return json.loads(shell('run-as', package, 'cat', 'files/ui-kit-bounds.json'))

    try:
        for scale in (1.0, 1.3):
            shell('settings', 'put', 'system', 'font_scale', str(scale))
            for locale in ('en', 'ja', 'zh-Hans'):
                shell('cmd', 'locale', 'set-app-locales', package, '--user', '0', '--locales', locale)
                shell('input', 'keyevent', 'KEYCODE_WAKEUP')
                shell('am', 'force-stop', package)
                shell('am', 'start', '-W', '-n', activity)
                for attempt in range(30):
                    try:
                        bounds = read_bounds()
                        if (bounds.get('locale', '').startswith(locale)
                                and abs(bounds.get('font_scale', 0) - scale) < 0.01
                                and 'time_readout' in bounds):
                            break
                    except (ValueError, subprocess.CalledProcessError):
                        pass
                    time.sleep(0.1)
                else:
                    raise AssertionError('Current board measurements were not available')
                assert 'com.odin.desktop/com.odin.desktop.ui.UiKitPreviewActivity' in shell('dumpsys', 'window', 'displays')
                for group in (('input', 'input_action'), ('choice', 'action', 'disabled'),
                              ('default_tag', 'home_tag', 'category_tag', 'key_hint'), ('long_option', 'two_line')):
                    assert len({(bounds[key]['y'], bounds[key]['height']) for key in group}) == 1, (group, bounds)
                assert bounds['input']['height'] == bounds['choice']['height'], bounds
                readouts = [bounds[key] for key in ('battery_readout', 'fan_readout', 'time_readout')]
                assert len({(item['y'], item['height'], item['first_baseline'], item['last_baseline'])
                            for item in readouts}) == 1, readouts
                disabled = bounds['disabled']
                shell('input', 'tap', str(round(disabled['x'] + disabled['width'] / 2)),
                      str(round(disabled['y'] + disabled['height'] / 2)))
                assert read_bounds()['disabled_clicks'] == 0
                stem = f'kit-{locale}-{scale}'
                (args.output / (stem + '.json')).write_text(json.dumps(bounds, indent=2))
                subprocess.run(['tools/android', 'android', 'screen', 'capture', '--device=' + args.serial,
                                '--output=' + str(args.output / (stem + '.png'))], check=True)
                results.append({'locale': locale, 'font_scale': scale,
                                'control_height_px': bounds['input']['height'],
                                'tag_height_px': bounds['home_tag']['height'],
                                'readout_baselines_aligned': True,
                                'aligned': True, 'disabled_clicks': 0})
                print(json.dumps(results[-1]), flush=True)
    finally:
        if original_scale == 'null':
            shell('settings', 'delete', 'system', 'font_scale')
        else:
            shell('settings', 'put', 'system', 'font_scale', original_scale)
        shell('cmd', 'locale', 'set-app-locales', package, '--user', '0', '--locales', original_locale)
        shell('am', 'force-stop', package)
        shell('am', 'start', '-W', '-n', package + '/.ui.MainActivity')
        shell('am', 'force-stop', 'com.android.cli.interact.instrumentation')
    (args.output / 'results.json').write_text(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
