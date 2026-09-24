#!/usr/bin/env python3
"""Verify the WOS-24 three-platform capture inventory and explicit webOS layout contract."""

import argparse
import hashlib
import json
from pathlib import Path
import struct


ROOT = Path(__file__).resolve().parents[2]
V1 = ROOT / "docs/webos-parity/v1"
V2 = ROOT / "docs/webos-parity/v2"
SCENARIOS = [
    "live", "live-empty", "channels", "search", "no-results", "no-data", "favorites", "recent",
    "guide", "guide-details", "settings", "settings-playback", "quick-settings", "offline",
]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def png_size(path):
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    return struct.unpack(">II", data[16:24])


def rect(evidence, selector):
    return evidence["elements"][selector]["rect"]


def visible(rectangle, viewport):
    x, y, width, height = rectangle
    return width > 0 and height > 0 and x < viewport[0] and y < viewport[1] and x + width > 0 and y + height > 0


def verify_geometry(scenario, evidence):
    elements = evidence["elements"]
    viewport = evidence["viewport"]
    nav = [rect(evidence, selector) for selector in ["#nav-live", "#nav-guide", "#nav-channels", "#nav-settings"]]
    assert all(abs(item[1] - nav[0][1]) <= 1 for item in nav), f"{scenario}: navigation is not horizontal"
    assert [item[0] for item in nav] == sorted(item[0] for item in nav), f"{scenario}: navigation order"
    if scenario in {"channels", "search", "favorites", "recent", "offline"}:
        surface, preview = rect(evidence, ".channel-surface"), rect(evidence, "#channel-preview")
        assert surface[0] < preview[0] and abs(surface[1] - preview[1]) <= 2, f"{scenario}: 62/38 panels"
    if scenario in {"settings", "settings-playback"}:
        categories, detail = rect(evidence, ".settings-categories"), rect(evidence, "#settings-detail")
        assert categories[0] < detail[0] and abs(categories[1] - detail[1]) <= 2, f"{scenario}: settings panels"
    if scenario == "guide":
        row, programme = rect(evidence, ".guide-row"), rect(evidence, ".guide-programme")
        assert row[2] > programme[2] and programme[0] > row[0], "guide: programme geometry"
    if scenario in {"guide-details", "quick-settings"}:
        dialog_selector = "#guide-programme-dialog" if scenario == "guide-details" else "#quick-settings-dialog"
        x, y, width, height = rect(evidence, dialog_selector)
        assert x == 0 and y == 0 and width == viewport[0] and height == viewport[1], f"{scenario}: modal backdrop"
    assert all(visible(item["rect"], viewport) for item in elements.values() if item["display"] != "none"), f"{scenario}: off-screen element"


def artifact_hashes():
    paths = []
    for platform in ["desktop", "android-tv"]:
        paths.extend(V1 / "screenshots" / platform / f"{scenario}.png" for scenario in SCENARIOS)
    paths.extend(V2 / "screenshots/webos-browser" / f"{scenario}.png" for scenario in SCENARIOS)
    paths.extend(V2 / "evidence" / f"{scenario}.json" for scenario in SCENARIOS)
    return {str(path.relative_to(ROOT)): digest(path) for path in paths}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--record", action="store_true", help="Seal reviewed WOS-24 webOS captures and DOM evidence")
    parser.add_argument("--require-tv", action="store_true", help="Require the physical-TV protocol to be signed off")
    args = parser.parse_args()
    contract = json.loads((V2 / "visual-contract.json").read_text())
    assert contract["viewport"] == [1920, 1080]
    observed_colors = set()
    for platform in ["desktop", "android-tv"]:
        for scenario in SCENARIOS:
            assert png_size(V1 / "screenshots" / platform / f"{scenario}.png") == (1920, 1080)
    for scenario in SCENARIOS:
        screenshot = V2 / "screenshots/webos-browser" / f"{scenario}.png"
        evidence_path = V2 / "evidence" / f"{scenario}.json"
        assert png_size(screenshot) == (1920, 1080)
        evidence = json.loads(evidence_path.read_text())
        definition = contract["scenarios"][scenario]
        selectors = set(contract["globalSelectors"] + definition["selectors"] + [definition["focusSelector"]])
        # macOS Chrome reserves 87 px in headless mode even though --screenshot is exactly 1920x1080.
        assert evidence["viewport"] == contract["chromeDomViewport"]
        assert selectors <= set(evidence["elements"]), f"{scenario}: missing selectors"
        assert evidence["elements"][definition["focusSelector"]]["focused"], f"{scenario}: focus mismatch"
        text = " ".join(item["text"] for item in evidence["elements"].values())
        assert definition["containsText"] in text, f"{scenario}: expected copy missing"
        assert [evidence["elements"][selector]["text"] for selector in ["#nav-live", "#nav-guide", "#nav-channels", "#nav-settings"]] == contract["navigationLabels"]
        for element in evidence["elements"].values():
            observed_colors.update([element["backgroundColor"], element["color"], element["borderColor"]])
        verify_geometry(scenario, evidence)
    assert set(contract["palette"]) <= observed_colors, "Wukki semantic palette is incomplete in the captured surfaces"
    hashes = artifact_hashes()
    manifest_path = V2 / "manifest.json"
    if args.record:
        manifest = {
            "schemaVersion": 1,
            "referenceVersion": "v1",
            "capturedOn": "2026-09-24",
            "scenarios": SCENARIOS,
            "artifacts": hashes,
        }
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    manifest = json.loads(manifest_path.read_text())
    assert manifest["artifacts"] == hashes, "WOS-24 capture drift; review and explicitly record the new artifacts"
    tv_report = json.loads((V2 / "tv-validation.json").read_text())
    if args.require_tv:
        assert tv_report["status"] == "passed", "Physical LG TV validation has not been signed off"
        assert all(check["status"] == "passed" for check in tv_report["checks"])
    print(f"WOS-24: {len(SCENARIOS)} scenarios x 3 platforms and explicit DOM geometry verified; TV={tv_report['status']}.")


if __name__ == "__main__":
    main()
