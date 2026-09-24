#!/usr/bin/env python3
"""Capture every WOS-24 webOS parity scenario with deterministic data and DOM evidence."""

import argparse
import hashlib
import html
import http.server
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import threading
import time


ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / "docs/webos-parity/v1"
OUTPUT = ROOT / "docs/webos-parity/v2"
DIST = ROOT / "webosApp/build/dist/js/productionExecutable"
FIXED_NOW = 1790006400000
SCENARIOS = [
    "live", "live-empty", "channels", "search", "no-results", "no-data", "favorites", "recent",
    "guide", "guide-details", "settings", "settings-playback", "quick-settings", "offline",
]


def chrome_path(explicit=None):
    candidates = [
        explicit,
        os.environ.get("CHROME_BIN"),
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        shutil.which("google-chrome"),
        shutil.which("chromium"),
        shutil.which("chromium-browser"),
    ]
    return next((value for value in candidates if value and Path(value).exists()), None)


def stable_channel_id(playlist_id, stream_url, name):
    digest = bytearray(hashlib.md5(f"{playlist_id}|{stream_url}|{name}".encode()).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    value = digest.hex()
    return f"{value[:8]}-{value[8:12]}-{value[12:16]}-{value[16:20]}-{value[20:]}"


def timestamp(epoch_millis):
    from datetime import datetime, timezone
    return datetime.fromtimestamp(epoch_millis / 1000, timezone.utc).strftime("%Y%m%d%H%M%S +0000")


def parity_data():
    fixture = json.loads((REFERENCE / "fixture.json").read_text())
    channels = []
    for source in fixture["channels"]:
        channel = dict(source)
        channel["playlistId"] = "webos-playlist"
        channel["id"] = stable_channel_id("webos-playlist", channel["streamUrl"], channel["name"])
        channels.append(channel)
    id_by_tvg = {channel["tvgId"]: channel["id"] for channel in channels}
    programmes = [dict(programme) for programme in fixture["epgProgrammesBySource"]["reference-epg"]]
    state = {
        "schemaVersion": 2,
        "state": {
            "playlistUrl": "https://example.invalid/reference.m3u",
            "playlistUpdatedAt": FIXED_NOW,
            "channels": channels,
            "lastChannelId": id_by_tvg["ref-1"],
            "recentChannelIds": [id_by_tvg["ref-2"], id_by_tvg["ref-1"]],
            "settings": {
                "language": "HUNGARIAN", "playlistRefreshHours": 0, "epgRefreshHours": 0,
                "volume": 100, "bufferProfile": "BALANCED", "autoPlayOnLaunch": False,
                "autoReconnect": True, "reconnectAttempts": 3, "aspectRatio": "AUTO", "uiScale": 1.0,
                "channelListMode": "NORMAL", "showChannelProgramme": True, "showMiniGuide": True,
                "showLogos": False, "showProgrammeImages": False,
            },
        },
    }
    epg_cache = {
        "schemaVersion": 1,
        "sourceUrl": "https://example.invalid/epg.xml",
        "updatedAt": FIXED_NOW,
        "programmes": programmes,
    }
    m3u_lines = ['#EXTM3U url-tvg="https://example.invalid/epg.xml"']
    for channel in channels:
        m3u_lines += [
            f'#EXTINF:-1 tvg-id="{channel["tvgId"]}" tvg-name="{channel["name"]}" '
            f'tvg-chno="{channel["tvgChno"]}" group-title="{channel["group"]}",{channel["name"]}',
            channel["streamUrl"],
        ]
    xml_lines = ['<?xml version="1.0" encoding="UTF-8"?><tv>']
    for programme in fixture["epgProgrammesBySource"]["reference-epg"]:
        xml_lines.append(
            f'<programme channel="{programme["channelId"]}" start="{timestamp(programme["start"])}" '
            f'stop="{timestamp(programme["end"])}"><title>{html.escape(programme["title"])}</title>'
            f'<desc>{html.escape(programme["description"])}</desc></programme>'
        )
    xml_lines.append("</tv>")
    return state, epg_cache, "\n".join(m3u_lines), "".join(xml_lines)


def scenario_actions(scenario):
    return {
        "live": "click('#nav-live'); focus('#nav-live');",
        "live-empty": "click('#nav-live'); focus('#nav-live');",
        "channels": "focus('#channel-list button');",
        "search": "click('#open-channel-search'); input('hir'); focus('#channel-search');",
        "no-results": "click('#open-channel-search'); input('nincsilyen'); focus('#channel-search');",
        "no-data": "focus('#channel-empty-action');",
        "favorites": "clickText('#channel-tabs button','Kedvencek'); focus('#channel-list button');",
        "recent": "clickText('#channel-tabs button','Legutóbb nézett'); focus('#channel-list button');",
        "guide": "click('#nav-guide'); focus('#guide-actions button');",
        "guide-details": "click('#nav-guide'); click('.guide-programme'); focus('#guide-dialog-open');",
        "settings": "click('#nav-settings'); focus('[data-settings-section]');",
        "settings-playback": "click('#nav-settings'); click('[data-settings-section=PLAYBACK]'); focus('.settings-option');",
        "quick-settings": "click('#nav-live'); click('#quick-settings'); focus('#close-quick-settings');",
        "offline": "focus('#channel-list button');",
    }[scenario]


def adapter_script(scenario, selectors):
    state, epg_cache, m3u, xml = parity_data()
    empty = scenario in {"no-data", "live-empty"}
    offline = scenario == "offline"
    setup = "localStorage.clear();"
    if not empty:
        setup += (
            "localStorage.setItem('hu.wukki.tv.webos.state.v1'," + json.dumps(json.dumps(state)) + ");"
            "localStorage.setItem('hu.wukki.tv.webos.epg.v1'," + json.dumps(json.dumps(epg_cache)) + ");"
        )
    if offline:
        fetch_body = "return Promise.reject(new Error('offline parity fixture'));"
    elif empty:
        fetch_body = "return Promise.resolve(new Response('#EXTM3U',{status:200}));"
    else:
        fetch_body = "return Promise.resolve(new Response(String(url).indexOf('epg.xml')>=0?PARITY_XML:PARITY_M3U,{status:200}));"
    before = f"""
<script>
Date.now=function(){{return {FIXED_NOW};}};
{setup}
var PARITY_M3U={json.dumps(m3u)};
var PARITY_XML={json.dumps(xml)};
window.fetch=function(url){{{fetch_body}}};
HTMLMediaElement.prototype.load=function(){{}};
HTMLMediaElement.prototype.play=function(){{return Promise.resolve();}};
</script>
"""
    after = f"""
<script>
(function(){{
  function one(selector){{return document.querySelector(selector);}}
  function click(selector){{var node=one(selector);if(node)node.click();}}
  function clickText(selector,text){{var nodes=document.querySelectorAll(selector);for(var i=0;i<nodes.length;i++){{if(nodes[i].textContent.indexOf(text)>=0){{nodes[i].click();return;}}}}}}
  function focus(selector){{var node=one(selector);if(node)node.focus();}}
  function input(value){{var node=one('#channel-search');if(node){{node.value=value;node.dispatchEvent(new Event('input',{{bubbles:true}}));}}}}
  setTimeout(function(){{
    {scenario_actions(scenario)}
    setTimeout(function(){{
      var selectors={json.dumps(selectors)};
      var evidence={{scenario:{json.dumps(scenario)},viewport:[innerWidth,innerHeight],active:(document.activeElement&&document.activeElement.id)||'',elements:{{}}}};
      selectors.forEach(function(selector){{
        var node=one(selector);if(!node)return;
        var rect=node.getBoundingClientRect();var style=getComputedStyle(node);
        evidence.elements[selector]={{rect:[rect.x,rect.y,rect.width,rect.height],text:(node.textContent||'').trim().replace(/\\s+/g,' ').slice(0,500),backgroundColor:style.backgroundColor,color:style.color,borderColor:style.borderColor,display:style.display,focused:node===document.activeElement}};
      }});
      var output=document.createElement('pre');output.id='wukki-parity-evidence';output.hidden=true;output.textContent=JSON.stringify(evidence);document.body.appendChild(output);
      document.documentElement.setAttribute('data-parity-ready','true');
      var request=new XMLHttpRequest();request.open('POST','/__evidence/{scenario}');request.send(JSON.stringify(evidence));
    }},400);
  }},1800);
}})();
</script>
"""
    return before, after


def chrome_command(chrome, root, url, extra):
    profile = hashlib.sha1(" ".join(extra).encode()).hexdigest()[:10]
    return [
        chrome, "--headless", "--disable-background-networking", "--disable-component-update", "--no-first-run",
        "--no-default-browser-check", "--hide-scrollbars", "--force-device-scale-factor=1", "--window-size=1920,1080",
        "--run-all-compositor-stages-before-draw", "--virtual-time-budget=5000", "--disable-gpu",
        f"--user-data-dir={root / ('chrome-' + profile)}", *extra, url,
    ]


def capture(chrome, root, server_port, scenario, selectors, output, evidence_store):
    source = (DIST / "index.html").read_text()
    before, after = adapter_script(scenario, selectors)
    source = source.replace('<script src="wukki-tv-webos.js"', before + '<script src="wukki-tv-webos.js"')
    source = source.replace("</body>", after + "</body>")
    page = root / f"{scenario}.html"
    page.write_text(source)
    url = f"http://127.0.0.1:{server_port}/{scenario}.html"
    screenshot = output / "screenshots/webos-browser" / f"{scenario}.png"
    screenshot.parent.mkdir(parents=True, exist_ok=True)
    screenshot.unlink(missing_ok=True)
    process = subprocess.Popen(
        chrome_command(chrome, root, url, [f"--screenshot={screenshot}"]),
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    deadline = time.monotonic() + 45
    try:
        while (not screenshot.exists() or scenario not in evidence_store) and process.poll() is None and time.monotonic() < deadline:
            time.sleep(0.1)
        if not screenshot.exists() or scenario not in evidence_store:
            raise RuntimeError(f"Chrome did not complete parity capture for {scenario}")
    finally:
        if process.poll() is None:
            process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
    data = screenshot.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack(">II", data[16:24]) != (1920, 1080):
        raise RuntimeError(f"Invalid screenshot for {scenario}")
    evidence = evidence_store.pop(scenario)
    evidence_dir = output / "evidence"
    evidence_dir.mkdir(parents=True, exist_ok=True)
    (evidence_dir / f"{scenario}.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--chrome", help="Chrome/Chromium executable")
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    chrome = chrome_path(args.chrome)
    if not chrome:
        raise SystemExit("Chrome/Chromium is required (use --chrome or CHROME_BIN)")
    contract = json.loads((args.output / "visual-contract.json").read_text())
    with tempfile.TemporaryDirectory(prefix="wukki-parity-") as temp:
        root = Path(temp)
        for source in DIST.iterdir():
            if source.is_file():
                (root / source.name).write_bytes(source.read_bytes())
        evidence_store = {}

        class Handler(http.server.SimpleHTTPRequestHandler):
            def __init__(self, *handler_args, **handler_kwargs):
                super().__init__(*handler_args, directory=root, **handler_kwargs)

            def do_POST(self):
                if not self.path.startswith("/__evidence/"):
                    self.send_error(404)
                    return
                length = int(self.headers.get("Content-Length", "0"))
                scenario = self.path.rsplit("/", 1)[-1]
                evidence_store[scenario] = json.loads(self.rfile.read(length))
                self.send_response(204)
                self.end_headers()

            def log_message(self, *_args):
                return

        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            for scenario in SCENARIOS:
                definition = contract["scenarios"][scenario]
                selectors = list(dict.fromkeys(contract["globalSelectors"] + definition["selectors"] + [definition["focusSelector"]]))
                capture(chrome, root, server.server_port, scenario, selectors, args.output, evidence_store)
                print(scenario, flush=True)
        finally:
            server.shutdown()


if __name__ == "__main__":
    main()
