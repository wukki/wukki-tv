# Wukki TV

A Wukki TV Kotlin Multiplatform / Compose alapú IPTV alkalmazás. A hivatalos Wukki csatornalistát, XMLTV műsorújságot és alkalmazáson belüli HLS lejátszást biztosít desktopon, Android TV-n és fekvő Android telefonon/tableten.

## Projektfelépítés

- `shared`: közös KMP modul — Compose UI, domain, playlist- és EPG-logika, lokalizáció és közös tesztek.
- `core`: UI-független KMP doménmag Android, JVM és JavaScript célplatformmal.
- `desktopApp`: desktop belépési pont, libVLC lejátszó, helyi állapot, kijelző-ébrentartás és natív csomagolás.
- `androidApp`: Android belépési pont, Media3 lejátszó, DataStore, WorkManager és Android TV integráció.
- `webosApp`: Kotlin/JS alapú webOS lejátszási próba natív HTML-videóval és távirányító-kezeléssel.

A közös üzleti műveleteket a Compose-független `WukkiApplication`, valamint a csatorna-, EPG- és
beállítás-repositoryk kezelik. A `WukkiModel` a meglévő UI kompatibilis adaptere. Részletek:
[application/domain réteghatár](docs/application-layer.md) és
[UI selector-/életciklus-kezelés](docs/ui-lifecycle.md).

## Funkciók

- Rögzített, hivatalos Wukki TV playlist: [`wukki-tv.m3u`](https://raw.githubusercontent.com/wukki/wukki-tv/refs/heads/main/wukki-tv.m3u); kézi, 6, 12 vagy 24 órás frissítéssel
- Automatikus playlist-normalizálás: `tvg-chno` szerinti rendezés, csatornalogók, kategóriák, kedvencek és keresés
- Beágyazott HLS lejátszás: indításkor az utoljára nézett, ennek hiányában az első csatorna automatikusan elindul
- Csatornaváltás `PageUp` / `PageDown`, nyilak, számbillentyűk és csatornalista segítségével
- Legfeljebb 10 sikeresen nézett csatorna tartós előzménye a Csatornák oldal „Legutóbb nézett” szűrőjében; a sikertelen nyitások nem módosítják az előzményt
- Az „Előző csatorna” gomb az előző sikeresen nézett csatornára vált; élő adás közben az `F8` vagy a média „előző” gomb ugyanezt teszi
- Élő adásnál az információs panel „Gyorsbeállítások” gombja vagy a Menü/`F9` nyitja a képarány-, hangsáv- és feliratválasztót; távirányítóval az OK → jobbra → jobbra útvonal képarányt vált
- A gyorsbeállítások csak az aktuális lejátszásra érvényesek, nem módosítják a mentett alapbeállításokat; hangsáv- és feliratválasztó csak lekérdezhető, támogatott sávoknál jelenik meg
- Újracsatlakozás, hangerő, pufferprofil és képarány beállítása (`Automatikus`, `16:9`, `4:3`, `21:9`, `Kitöltés`)
- Az M3U fejlécéből automatikusan felismert, rögzített XMLTV-forrás (`url-tvg` / `x-tvg-url` / `tvg-url`) és csatorna–EPG párosítás; egyéni playlist- és EPG-források nem használhatók
- Memóriatakarékos, gzip-kompatibilis streaming XMLTV-feldolgozás és gyors, bináris keresésű műsorindex
- Csatornahelyes „most megy” és következő műsor, az EPG-lefedettséghez igazodó, időarányos, kétirányban navigálható műsorújság
- Magyar és angol felület; a beállítások és az alkalmazásállapot helyben tárolódik
- Android 8+ támogatás Android TV launcherrel, D-pad- és érintéses navigációval, valamint közvetlenül telepíthető APK-val
- Androidon Media3/ExoPlayer, desktopon libVLC gondoskodik a HLS lejátszásról
- Android Élő adás nézetben a kijelző ébren marad; képernyőzárnál a stream megáll, feloldáskor folytatódik
- Androidon rövid érintés megjeleníti vagy elrejti az információs panelt, felfelé/lefelé pöccintés pedig csatornát vált
- A manuális frissítések rövid, automatikusan eltűnő visszajelzést adnak; siker esetén 3, hiba esetén 8 másodpercig

## Helyi állapot és hibakezelés

Az állapot betöltése aszinkron történik, betöltési visszajelzéssel. Sérült vagy nem olvasható
felhasználói állapotnál az alkalmazás újrapróbálást kínál; nem írja felül automatikusan üres adatokkal.
A gyors egymás utáni módosításokat közös mentési sor vonja össze 200 ms alatt, mindig a legújabb
állapotot megtartva. Mentési hibánál tartós, újrapróbálható értesítés jelenik meg.

- Desktopon a beállítások a `~/.wukki-tv/state.json`, az EPG-adatok külön
  `~/.wukki-tv/epg_cache.json.gz` fájlban tárolódnak. A régi, EPG-t tartalmazó JSON és `state.bin`
  automatikusan migrálódik. A normál ablakbezárás megvárja a mentést; hiba esetén nyitva marad.
- Androidon a meglévő DataStore és tömörített EPG-cache marad használatban. A felület és a
  WorkManager ugyanazt a process-szintű alkalmazásállapotot és mentőt használja, de a háttérworker
  nem hoz létre UI-modellt. A playlist- és EPG-munka csak a releváns ütemezési adat változásakor
  kerül újra beadásra, átmeneti hibánál korlátozott, exponenciális újrapróbálással. Háttérbe kerüléskor az alkalmazás
  azonnali mentést kér; kényszerleállítás vagy a folyamat rendszer általi megszüntetése ezt megszakíthatja.
- Sérült vagy hiányzó EPG-cache nem törli a beállításokat és kedvenceket: az alkalmazás figyelmeztet,
  és újratölthető műsoradatok nélkül indul. Automatikus frissítésnél a forrás újra esedékessé válik;
  kézi módban kézi frissítés szükséges.

## Indítás fejlesztőként

Először töltsd le a Gradle wrappert, majd indítsd az alkalmazást:

```sh
./bootstrap-gradle.sh
./gradlew :desktopApp:run
```

Az alkalmazáson belüli lejátszáshoz elérhető VLC/libVLC runtime szükséges. Fejlesztés közben a Wukki TV először az alábbi helyeken keresi:

1. `WUKKI_VLC_HOME` környezeti változó vagy `-Dwukki.vlc.home=...` JVM paraméter
2. `runtime/vlc` a projekt gyökerében
3. platform alapértelmezett VLC telepítési helye (macOS-en például `/Applications/VLC.app`)

Példa egyedi VLC runtime-mal:

```sh
WUKKI_VLC_HOME="/Applications/VLC.app/Contents/MacOS" ./gradlew :desktopApp:run
```

## Függőségek és ellátási lánc

Minden közvetlen plugin- és könyvtárverzió a `gradle/libs.versions.toml` katalógusban egyetlen,
pontos verzióra van rögzítve. A Compose Multiplatform 1.12 stabil kiadása jelenleg a külön kiadott
`org.jetbrains.compose.material3:material3:1.12.0-alpha03` artifactot használja, ezért az alpha
verziót a Compose 1.12 kompatibilitása miatt tartjuk meg. A rögzítést minden Compose Multiplatform
frissítéskor és minden kiadás előtt felül kell vizsgálni. Stabil Material3 kiadásra akkor válthatunk,
amikor a JetBrains a használt Compose verzióval kompatibilis stabil artifactot ad ki, és a teljes
desktop- és Android-ellenőrzés sikeresen lefut vele.

A `shared` és `androidApp` modul szigorú Gradle dependency lockfile-t használ. Függőségfrissítéskor
az új feloldást és ellenőrzőösszegeket csak a diff átnézése mellett szabad elfogadni:

```sh
./gradlew verifyAll cyclonedxBom --write-locks --write-verification-metadata sha256
git diff -- core/gradle.lockfile shared/gradle.lockfile androidApp/gradle.lockfile webosApp/gradle.lockfile kotlin-js-store/package-lock.json gradle/verification-metadata.xml
```

A `gradle/verification-metadata.xml` SHA-256 alapján ellenőrzi a letöltött Gradle artifactokat. A
`verifyAll` ezen felül elutasítja a hiányzó lockfile-okat és a nem teljes commit SHA-ra rögzített
GitHub Action hivatkozásokat.

## Verziózás

A CI/CD minden buildhez UTC alapján egységes verziót számol:

```text
YY.DDD.sha8
```

- `YY`: az év utolsó két számjegye;
- `DDD`: az év napja három számjegyen;
- `sha8`: a Git commitazonosító első nyolc karaktere.

Például a 2026. év 245. napján, a `8001345f…` commitból készült verzió: `26.245.8001345f`. Ez jelenik meg a Névjegy oldalon, az Android `versionName` mezőjében, a GitHub Release címében és az artifactok fájlnevében. A desktop telepítők és az Android ezen felül platformkompatibilis, numerikus belső verziót kapnak.

A helyileg számított értékek megtekinthetők ezzel:

```sh
./gradlew printWukkiVersion
```

Reprodukálható buildhez a dátum, a commit és a futásszám explicit is megadható:

```sh
./gradlew printWukkiVersion \
  -PwukkiBuildDate=2026-09-02 \
  -PwukkiGitSha=8001345f00000000000000000000000000000000 \
  -PwukkiRunNumber=42
```

## Csomagolás

Natív telepítő készíthető DMG, MSI vagy DEB formátumban. A kiadásba szánt VLC runtime-ot a `WUKKI_VLC_RUNTIME` változóval lehet az alkalmazás erőforrásai közé másolni; a licencek a `LICENSES` könyvtárból kerülnek be.

```sh
WUKKI_VLC_RUNTIME="/elérési/út/vlc-runtime" ./gradlew :desktopApp:packageDistributionForCurrentOS
```

A release workflow a VLC runtime-ot is a desktop telepítőkbe csomagolja, ezért a kiadott alkalmazásokhoz nem szükséges külön VLC telepítés.

> A helyben készített vagy Apple secretek nélkül kiadott macOS DMG nincs Developer ID tanúsítvánnyal aláírva és notarizálva. Első indításkor Finderben jobb klikk → **Megnyitás** szükséges lehet.

## webOS próbaalkalmazás

A Kotlin/JS alapú első mérési alkalmazás production csomagja ezzel készül:

```sh
./gradlew :webosApp:jsBrowserDistribution
```

A webOS-csomagolásra előkészített fájlok a `webosApp/build/dist/js/productionExecutable`
könyvtárba kerülnek. A próba egy megadott stream URL-t nyit meg natív HTML-videóban, kezeli a
D-pad fókuszt és a platform Back gombját. Még nem tölti le a hivatalos playlistet, és fizikai LG
TV-n nem lett ellenőrizve.

Az LG webOS CLI telepítése után telepíthető IPK is készíthető:

```sh
npm install -g @webos-tools/cli
./gradlew :webosApp:packageWebOs
```

Az IPK a `webosApp/build/outputs/webos` könyvtárba kerül. A próbaalkalmazás előre kitölti az M2
tesztstream URL-jét; másik stream továbbra is megadható a mezőben vagy a `?stream=` paraméterrel.

A forrásbeli `webosApp/src/jsMain/resources/index.html` közvetlen helyi megnyitásához előbb le kell
futtatni a `jsBrowserDistribution` feladatot. Az oldal ilyenkor a `build` könyvtárból tölti be a
lefordított Kotlin/JS bundle-t; az IPK-ban továbbra is a csomag gyökerében lévő bundle használatos.

## Android APK

Az Android alkalmazás minimum Android 8.0-t (API 26) igényel, fekvő tájolásra optimalizált. Android TV-n a rendszer Leanback launcherében is megjelenik.

Az első build előtt telepíts Android SDK Platform 36-ot és Build Tools 36-ot, majd állítsd be a helyi SDK útvonalát az egyik módon:

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

vagy hozd létre a gitből kizárt `local.properties` fájlt:

```properties
sdk.dir=/Users/saját-felhasználó/Library/Android/sdk
```

Debug APK készítése:

```sh
./gradlew :androidApp:assembleDebug
```

Az APK a következő helyen készül el: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

Telepítés USB-n vagy Android Debug Bridge-en keresztül:

```sh
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Release APK saját aláírással készíthető. Másold az `androidApp/keystore.properties.example` fájlt `androidApp/keystore.properties` néven, töltsd ki a helyi keystore adataival, majd futtasd az `:androidApp:assembleRelease` feladatot. A keystore és a jelszavak nem kerülnek a repóba.

## CI/CD és kiadás

Helyben és a CI/release ellenőrzési lépésében ugyanaz a közös minőségi kapu fut:

```sh
./gradlew verifyAll
```

Ez futtatja a verziószámítás `buildSrc` tesztjeit, mindhárom alkalmazásmodul Detekt- és
ktlint-ellenőrzését, a shared desktop/Android host teszteket, a desktop és Android unit
teszteket, a lokalizáció-ellenőrzést, a desktop fordítást és az Android debug APK buildjét.
Java 21 és beállított Android SDK szükséges hozzá. A workflow-k YAML-ellenőrzése
(`actionlint`) külön CI-lépés marad; a telepítők csomagolása a release jobokban történik.

A `.github/workflows/ci.yml` pull requestnél, valamint a `main` vagy `master` ágra történő pushnál fut. Ellenőrzi a közös és platformspecifikus teszteket, a lokalizációt, a desktop fordítást és az Android debug APK-t. Kézi indításkor a debug APK Actions artifactként is letölthető.

A `.github/workflows/release.yml` `v*` tag pusholásakor vagy kézi indítással egyetlen, ellenőrzött forráscommitból készíti el az összes kiadási csomagot:

| Platform | Kimenet |
| --- | --- |
| macOS Apple Silicon | `Wukki-TV-<verzió>-macos-arm64.dmg` |
| macOS Intel | `Wukki-TV-<verzió>-macos-x64.dmg` |
| Windows x64 | `Wukki-TV-<verzió>-windows-x64.msi` |
| Linux x64 | `Wukki-TV-<verzió>-linux-x64.deb` |
| Android | `Wukki-TV-<verzió>-android-release.apk` |
| CycloneDX SBOM | `Wukki-TV-<verzió>-sbom.cdx.json` |

A kiadás tartalmaz egy `SHA256SUMS.txt` ellenőrzőösszeg-fájlt, egy `release-metadata.json` leírást,
a Linux VLC runtime fájlonkénti ellenőrzőmanifestjét és egy CycloneDX JSON SBOM-ot is. A publish job
GitHub artifact attestationt készít az SBOM-ról és minden release fájl build provenance-áról. Az
installerek Actions artifactként 30 napig megmaradnak, sikeres teljes build után pedig GitHub
Release-hez csatolódnak. Kézi indításnál a workflow létrehozza a `v<verzió>` taget; már létező, más
commitra mutató taget nem ír felül.

Az Android release kötelező aláírásához a repositoryban az alábbi Actions secretek szükségesek:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

A macOS aláírás és notarizálás opcionális. Ha használod, a teljes készletet meg kell adni:

- `APPLE_CERTIFICATE_BASE64`
- `APPLE_CERTIFICATE_PASSWORD`
- `APPLE_SIGNING_IDENTITY`
- `APPLE_ID`
- `APPLE_APP_SPECIFIC_PASSWORD`
- `APPLE_TEAM_ID`

A Windows Authenticode-aláírás szintén opcionális, és mindkét secretet együtt igényli:

- `WINDOWS_CERTIFICATE_BASE64`
- `WINDOWS_CERTIFICATE_PASSWORD`

A tanúsítványokat és keystore-t Base64-kódolt binárisként kell megadni. A workflow ezeket csak a runner ideiglenes könyvtárában állítja helyre, majd a futás végén eltávolítja. Desktop aláírási secretek nélkül unsigned/ad-hoc csomagok készülnek; hiányzó Android aláírási secret esetén a release még a csomagolás előtt leáll.

Helyi, azonos verziómetaadatot használó release build például:

```sh
./gradlew \
  :desktopApp:packageDistributionForCurrentOS \
  :androidApp:assembleRelease \
  -PwukkiBuildDate=2026-09-02 \
  -PwukkiGitSha=8001345f00000000000000000000000000000000 \
  -PwukkiRunNumber=42
```

A desktop csomagoláshoz `WUKKI_VLC_RUNTIME`, az Android release buildhez pedig a fent ismertetett helyi `androidApp/keystore.properties` szükséges.

### Android használat

- A telefonos felület fekvő tájolásra készült.
- Élő adás közben felfelé pöccintés a következő, lefelé pöccintés az előző csatornára vált. Egy rövid érintés az információs panelt kapcsolja.
- Android TV-n a D-pad nyilai mozgatják a fókuszt, az OK/Enter aktivál, a Vissza a korábbi szintre lép.
- A Beállításokban a kategória megnyitása külön, teljes tartalmú és görgethető oldalra visz. A felső visszanyíl vagy a rendszer Vissza gomb tér vissza a kategórialistához.
- A Csatornák oldali műsorelőnézet és a Műsorújság részletpanelje is görgethető, ha a tartalom nem fér el.

## Kijelző-ébrentartás

- Androidon csak az **Élő adás** képernyő tartja ébren a kijelzőt. Más menüben ismét a rendszer saját időzítője érvényesül.
- Desktopon az előtérben lévő, fókuszban levő Wukki TV ablak kéri a kijelző ébrentartását. Minimalizáláskor, fókuszvesztéskor vagy bezáráskor ezt azonnal feloldja.

## Korlátok

- Elsődleges cél a HLS (`.m3u8`) streamek támogatása; más streamprotokollok eredménye a használt VLC runtime-tól függ.
- A PIN-alapú szülői felügyelet jelenleg előkészített felület, még nem tartalomzárolási funkció.
