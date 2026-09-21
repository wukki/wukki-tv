#!/usr/bin/env python3
"""Validate committed WOS-14 artifacts. --record explicitly seals reviewed captures, never on CI."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[2]
REF = ROOT / 'docs/webos-parity/v1'
COMMIT = 'b7e323cac36a18eb5b566bdf0c6d6eddd556150e'
SCENARIOS = ['live', 'live-empty', 'channels', 'search', 'no-results', 'no-data', 'favorites', 'recent', 'guide', 'guide-details', 'settings', 'settings-playback', 'quick-settings', 'offline']


def digest(data):
    return hashlib.sha256(data).hexdigest()


def sources():
    paths = list((ROOT / 'shared/src/commonMain/kotlin/hu/wukki/tv/ui').rglob('*.kt'))
    paths += list((ROOT / 'shared/src/commonMain/resources/i18n').glob('*.properties'))
    paths += [ROOT / p for p in [
        'shared/src/commonMain/kotlin/hu/wukki/tv/player/PlaybackInfoPanelStyle.kt',
        'desktopApp/src/main/kotlin/hu/wukki/tv/PlaybackOverlayRenderer.kt',
        'androidApp/src/main/kotlin/hu/wukki/tv/AndroidPlaybackOverlay.kt',
        'webosApp/src/jsMain/kotlin/hu/wukki/tv/webos/Main.kt',
        'webosApp/src/jsMain/resources/index.html',
        'webosApp/src/jsMain/resources/styles.css',
    ]]
    return {str(p.relative_to(ROOT)): digest(p.read_bytes()) for p in sorted(paths)}


def artifacts():
    paths = sorted(REF.rglob('*.png')) + [REF / 'fixture.json', REF / 'input-traces.json', REF / 'visual-tokens.json']
    return {str(p.relative_to(REF)): digest(p.read_bytes()) for p in paths}


def check_structure():
    expected = {f'screenshots/{platform}/{scenario}.png' for platform in ['desktop', 'android-tv'] for scenario in SCENARIOS}
    expected.add('screenshots/webos-browser/channels.png')
    actual = {str(p.relative_to(REF)) for p in REF.rglob('*.png')}
    assert expected == actual, f'Capture inventory mismatch: {expected ^ actual}'
    for name in expected:
        data = (REF / name).read_bytes()
        assert data[:8] == b'\x89PNG\r\n\x1a\n', name
        assert struct.unpack('>II', data[16:24]) == (1920, 1080), name
    trace = json.loads((REF / 'input-traces.json').read_text())
    ids = [case['id'] for case in trace['cases']]
    assert len(ids) == len(set(ids)) == 13
    fixture = json.loads((REF / 'fixture.json').read_text())
    assert [c['id'] for c in fixture['channels']] == trace['channelIds']
    assert fixture['settings']['language'] == 'HUNGARIAN'
    for case in trace['cases']:
        keys = set(case['initial'])
        assert case['steps'], case['id']
        for step in case['steps']:
            assert set(step['expected']) == keys, case['id']
            assert isinstance(step['handled'], bool) and isinstance(step['effects'], list)
    for doc in [REF / 'README.md', REF / 'exceptions.md']:
        for link in re.findall(r'\]\(([^)]+)\)', doc.read_text()):
            if '://' not in link and not link.startswith('#'):
                assert (doc.parent / link.split('#')[0]).exists(), link


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--record', action='store_true', help='Seal reviewed images and pinned source hashes; explicit baseline mutation')
    args = parser.parse_args()
    check_structure()
    path = REF / 'manifest.json'
    current_sources = sources()
    if args.record:
        for name, sha in current_sources.items():
            original = subprocess.check_output(['git', 'show', f'{COMMIT}:{name}'], cwd=ROOT)
            assert digest(original) == sha, f'Reference source differs from pinned commit: {name}'
        manifest = {
            'schemaVersion': 1, 'referenceCommit': COMMIT, 'capturedOn': '2026-09-21',
            'epochMillis': 1790006400000, 'timezone': 'Europe/Budapest', 'viewport': [1920, 1080],
            'density': 1, 'fontScale': 1, 'scenarios': SCENARIOS,
            'evidence': {'desktop': 'Compose ImageComposeScene, no decoder', 'android-tv': 'Television_1080p emulator, debug reference Activity, no decoder', 'webos-browser': '0.6.0 production bundle, Chrome 150 headless, fetch fixture adapter; not LG hardware'},
            'sources': current_sources, 'artifacts': artifacts(),
        }
        path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    manifest = json.loads(path.read_text())
    assert manifest['referenceCommit'] == COMMIT
    assert manifest['sources'] == current_sources, 'Reference source drift: review and version the contract; do not silently replace it'
    assert manifest['artifacts'] == artifacts(), 'Artifact drift: review captures and fixture changes before recording a baseline'
    print(f'WOS-14: {len(manifest["sources"])} pinned sources, 29 PNGs, 13 input traces and fixture hashes verified.')


if __name__ == '__main__':
    main()
