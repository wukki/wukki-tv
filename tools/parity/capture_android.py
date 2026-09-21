#!/usr/bin/env python3
"""Capture the debug-only reference activity on an already booted 1080p Android TV emulator."""
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
ADB = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk')) / 'platform-tools/adb'
OUT = ROOT / 'docs/webos-parity/v1/screenshots/android-tv'

def adb(*args):
    return subprocess.check_output([str(ADB), '-e', *args])

if __name__ == '__main__':
    size = adb('shell', 'wm', 'size').decode()
    if '1920x1080' not in size:
        raise SystemExit('Requires a 1920x1080 TV emulator; refusing to change another device configuration')
    OUT.mkdir(parents=True, exist_ok=True)
    adb('install', '-r', str(ROOT / 'androidApp/build/outputs/apk/debug/androidApp-debug.apk'))
    for scenario in ['live', 'live-empty', 'channels', 'search', 'no-results', 'no-data', 'favorites', 'recent', 'guide', 'guide-details', 'settings', 'settings-playback', 'quick-settings', 'offline']:
        adb('shell', 'am', 'start', '-S', '-W', '-n', 'hu.wukki.tv/.parity.ParityReferenceActivity', '--es', 'scenario', scenario)
        time.sleep(2)
        resumed = adb('shell', 'dumpsys', 'activity', 'activities').decode()
        if 'ParityReferenceActivity' not in resumed:
            raise RuntimeError('Reference activity did not start')
        (OUT / f'{scenario}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
        print(scenario, flush=True)
