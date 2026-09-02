# Wukki TV

A Wukki TV Kotlin Multiplatform / Compose alapú IPTV alkalmazás. A hivatalos Wukki csatornalistát, XMLTV műsorújságot és alkalmazáson belüli HLS lejátszást biztosít desktopon, Android TV-n és fekvő Android telefonon/tableten.

## Projektfelépítés

- `shared`: közös KMP modul — Compose UI, domain, playlist- és EPG-logika, lokalizáció és közös tesztek.
- `desktopApp`: desktop belépési pont, libVLC lejátszó, helyi állapot, kijelző-ébrentartás és natív csomagolás.
- `androidApp`: Android belépési pont, Media3 lejátszó, DataStore, WorkManager és Android TV integráció.

## Funkciók

- Rögzített, hivatalos Wukki TV playlist: [`wukki-tv.m3u`](https://raw.githubusercontent.com/wukki/wukki-tv/refs/heads/main/wukki-tv.m3u); kézi, 6, 12 vagy 24 órás frissítéssel
- Automatikus playlist-normalizálás: `tvg-chno` szerinti rendezés, csatornalogók, kategóriák, kedvencek és keresés
- Beágyazott HLS lejátszás: indításkor az utoljára nézett, ennek hiányában az első csatorna automatikusan elindul
- Csatornaváltás `PageUp` / `PageDown`, nyilak, számbillentyűk és csatornalista segítségével
- Újracsatlakozás, hangerő, pufferprofil és képarány beállítása (`Automatikus`, `16:9`, `4:3`, `21:9`, `Kitöltés`)
- Az M3U fejlécéből automatikusan felismert, rögzített XMLTV-forrás (`url-tvg` / `x-tvg-url` / `tvg-url`) és csatorna–EPG párosítás; egyéni playlist- és EPG-források nem használhatók
- Csatornahelyes „most megy” és következő műsor, az EPG-lefedettséghez igazodó, időarányos, kétirányban navigálható műsorújság
- Magyar és angol felület; a beállítások és az alkalmazásállapot helyben tárolódik
- Android 8+ támogatás Android TV launcherrel, D-pad- és érintéses navigációval, valamint közvetlenül telepíthető APK-val
- Androidon Media3/ExoPlayer, desktopon libVLC gondoskodik a HLS lejátszásról
- Android Élő adás nézetben a kijelző ébren marad; képernyőzárnál a stream megáll, feloldáskor folytatódik
- Androidon rövid érintés megjeleníti vagy elrejti az információs panelt, felfelé/lefelé pöccintés pedig csatornát vált
- A manuális frissítések rövid, automatikusan eltűnő visszajelzést adnak; siker esetén 3, hiba esetén 8 másodpercig

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

A `.github/workflows/ci.yml` pull requestnél, valamint a `main` vagy `master` ágra történő pushnál fut. Ellenőrzi a közös és platformspecifikus teszteket, a lokalizációt, a desktop fordítást és az Android debug APK-t. Kézi indításkor a debug APK Actions artifactként is letölthető.

A `.github/workflows/release.yml` `v*` tag pusholásakor vagy kézi indítással egyetlen, ellenőrzött forráscommitból készíti el az összes kiadási csomagot:

| Platform | Kimenet |
| --- | --- |
| macOS Apple Silicon | `Wukki-TV-<verzió>-macos-arm64.dmg` |
| macOS Intel | `Wukki-TV-<verzió>-macos-x64.dmg` |
| Windows x64 | `Wukki-TV-<verzió>-windows-x64.msi` |
| Linux x64 | `Wukki-TV-<verzió>-linux-x64.deb` |
| Android | `Wukki-TV-<verzió>-android-release.apk` |

A kiadás tartalmaz egy `SHA256SUMS.txt` ellenőrzőösszeg-fájlt és egy `release-metadata.json` leírást is. Az installerek Actions artifactként 30 napig megmaradnak, sikeres teljes build után pedig GitHub Release-hez csatolódnak. Kézi indításnál a workflow létrehozza a `v<verzió>` taget; már létező, más commitra mutató taget nem ír felül.

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
