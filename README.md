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

## Csomagolás

Natív telepítő készíthető DMG, MSI vagy DEB formátumban. A kiadásba szánt VLC runtime-ot a `WUKKI_VLC_RUNTIME` változóval lehet az alkalmazás erőforrásai közé másolni; a licencek a `LICENSES` könyvtárból kerülnek be.

```sh
WUKKI_VLC_RUNTIME="/elérési/út/vlc-runtime" ./gradlew :desktopApp:packageDistributionForCurrentOS
```

A GitHub Actions `Package desktop applications` workflow kézzel, illetve `v*` formátumú tag pusholásakor készít macOS, Windows és Linux telepítőket. A workflow a VLC runtime-ot is a telepítőbe csomagolja, ezért a kiadott alkalmazásokhoz nem szükséges külön VLC telepítés.

> A macOS DMG jelenleg nincs Apple Developer tanúsítvánnyal aláírva vagy notarizálva. Első indításkor Finderben jobb klikk → **Megnyitás** szükséges lehet.

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
