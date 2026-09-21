#!/usr/bin/env python3
"""Capture the unchanged webOS distribution with a test-only deterministic fetch adapter."""
import functools
import http.server
import json
from pathlib import Path
import subprocess
import tempfile
import threading
import time

ROOT = Path(__file__).resolve().parents[2]
REF = ROOT / 'docs/webos-parity/v1'
DIST = ROOT / 'webosApp/build/dist/js/productionExecutable'
CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'

if __name__ == '__main__':
    fixture = json.loads((REF / 'fixture.json').read_text())
    m3u = '#EXTM3U\n' + ''.join(f'#EXTINF:-1 tvg-id="{c["tvgId"]}" tvg-chno="{c["tvgChno"]}" group-title="{c["group"]}",{c["name"]}\n{c["streamUrl"]}\n' for c in fixture['channels'])
    # Inject before application startup; never connect to a real playlist or media endpoint.
    adapter = '<script>window.fetch=function(){return Promise.resolve(new Response(' + json.dumps(m3u) + ',{status:200}));};Date.now=function(){return 1790006400000;};</script>'
    with tempfile.TemporaryDirectory(prefix='wukki-parity-') as temp:
        root = Path(temp)
        for source in DIST.iterdir():
            if source.is_file():
                (root / source.name).write_bytes(source.read_bytes())
        index = root / 'index.html'
        index.write_text(index.read_text().replace('<script src="wukki-tv-webos.js"', adapter + '<script src="wukki-tv-webos.js"'))
        server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(http.server.SimpleHTTPRequestHandler, directory=root))
        threading.Thread(target=server.serve_forever, daemon=True).start()
        out = REF / 'screenshots/webos-browser'
        out.mkdir(parents=True, exist_ok=True)
        try:
            command = [CHROME, '--headless', '--disable-background-networking', '--disable-component-update', '--no-first-run', '--no-default-browser-check', '--hide-scrollbars', '--force-device-scale-factor=1', '--window-size=1920,1080', '--virtual-time-budget=3000', f'--user-data-dir={root / "chrome-profile"}', f'--screenshot={out / "channels.png"}', f'http://127.0.0.1:{server.server_port}/index.html']
            target = out / 'channels.png'
            target.unlink(missing_ok=True)
            with subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) as process:
                try:
                    deadline = time.monotonic() + 40
                    while not target.exists() and process.poll() is None and time.monotonic() < deadline:
                        time.sleep(.2)
                    if not target.exists():
                        raise RuntimeError('Chrome did not produce the reference screenshot')
                    time.sleep(.5)
                finally:
                    if process.poll() is None:
                        process.terminate()
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()

        finally:
            server.shutdown()
