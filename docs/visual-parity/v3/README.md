# Desktop visual reference (v3)

These images are a fixed reference from the current desktop implementation, with the bundled Inter font. The 20 scenarios use the fixture in `docs/webos-parity/v1/fixture.json` and fixed clock. Each scenario was captured at 1920×1080, 1280×720, and 800×450 logical pixels. `manifest.json` records the viewport and SHA-256 of every PNG. The three `desktop-native` images render the real Java2D player overlay over a black video placeholder.

To regenerate a desktop capture in a temporary directory, run:

```sh
./gradlew :desktopApp:captureParityReferences -PparityWidth=1280 -PparityHeight=720 -PparityOutput="$PWD/build/parity/desktop-1280/screenshots/desktop" --offline
```

To capture the webOS browser implementation at the same viewport (after `:webosApp:jsBrowserDistribution`), run:

```sh
python3 tools/parity/capture_webos.py --width 1280 --height 720 --output build/parity/webos-1280
python3 tools/parity/compare_images.py docs/visual-parity/v3/desktop-1280 build/parity/webos-1280/screenshots/webos-browser --output build/parity/diff-1280
```

The webOS capture needs Chrome/Chromium and the Playwright package declared in `tools/parity/package.json`. The comparator writes `report.json` and marked difference PNGs; it exits nonzero on any non-antialiased difference. The DOM evidence and screenshot now come from the same browser session and viewport. Reference images are never updated by the comparator.

This reference is **not** an acceptance certificate. The latest targeted comparison still differs from the desktop in all sampled webOS screens (for example, at 1280×720, `channels`: 67,967 and `settings-playback`: 116,837 differing pixels). Text metrics, some control content, channel state, guide cells, and compact layout still need alignment. The visual CI gate must be enabled only after those differences are fixed. Android TV and physical LG TV also need device checks for focus, font loading, panel boundaries, and the video overlay. Updating this reference requires an intentional review of the new desktop output.
