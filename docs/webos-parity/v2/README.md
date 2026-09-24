# WOS-24 – háromplatformos paritási és kiadási kapu

Ez a csomag a WOS-14 változatlan desktop- és Android TV-referenciáit hasonlítja a WOS-24 webOS production bundle 14 azonos forgatókönyvéhez. Minden PNG 1920×1080, 1× skála, magyar nyelv, Europe/Budapest időzóna és a `v1/fixture.json` rögzített adata mellett készül. A macOS headless Chrome 87 képpontot fenntart a DOM viewportból; ezt a `chromeDomViewport` külön rögzíti, miközben a kiértékelt képi felület változatlanul 1920×1080.

A webOS bizonyíték két részből áll: forgatókönyvenkénti PNG és géppel ellenőrizhető DOM-mérés. A [vizuális szerződés](visual-contract.json) név szerint sorolja fel a kötelező elemeket, fókuszt és szöveget. A validator külön ellenőrzi a felső menü vízszintes sorrendjét, a Csatornák 62/38 és a Beállítások kétpaneles geometriáját, az EPG programblokkjait, a dialógusok teljes hátterét, a viewportot és a fókuszt. Nincs teljes képre alkalmazott, elrendezési hibát elfedő pixelküszöb. Az eltérések állapota az [exceptions.md](exceptions.md) fájlban van.

```sh
./gradlew :webosApp:jsBrowserDistribution
python3 tools/parity/capture_webos.py
python3 tools/parity/verify_wos24.py --record  # csak felülvizsgált képekhez
python3 tools/parity/verify_wos24.py
```

A `--record` tudatos baseline-módosítás; CI-ben tilos. A normál ellenőrzés a `manifest.json` hash-eit és a tételes vizuális szerződést ellenőrzi.

## Fizikai TV kapu

A [tv-validation.json](tv-validation.json) a készülékteszt egyetlen hiteles eredményforrása. A hardvert igénylő kiadási kapu csak akkor zárható, ha a készülék és firmware ki van töltve, minden ellenőrzés `passed`, a mért idők rögzítettek, és ezután sikeresen lefut:

```sh
python3 tools/parity/verify_wos24.py --require-tv
```

A böngészős kép és az automatizált teszt nem helyettesíti a kétórás lejátszást, a 100 csatornaváltást, a 20 lifecycle-ciklust vagy a Magic Remote próbáját. Amíg ez a fájl `pending`, a WOS-24 implementáció telepíthető tesztkiadás, a fizikai TV-s elfogadás nyitott.
