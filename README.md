# Wukki TV

A Wukki TV Kotlin Multiplatform / Compose alapú IPTV alkalmazás. A hivatalos Wukki csatornalistát, XMLTV műsorújságot és alkalmazáson belüli HLS lejátszást biztosít desktopon, Android TV-n és fekvő Android telefonon/tableten.

## Funkciók

- Rögzített, hivatalos Wukki TV playlist: [`wukki-tv.m3u`](https://raw.githubusercontent.com/wukki/wukki-tv/refs/heads/main/wukki-tv.m3u); manuális, 6 órás vagy napi frissítéssel
- Automatikus playlist-normalizálás: `tvg-chno` szerinti rendezés, csatornalogók, kategóriák, kedvencek és keresés
- Beágyazott HLS lejátszás: indításkor az utoljára nézett, ennek hiányában az első csatorna automatikusan elindul
- Csatornaváltás `PageUp` / `PageDown`, nyilak, számbillentyűk és csatornalista segítségével
- Újracsatlakozás, hangerő, pufferprofil és képarány beállítása (`Automatikus`, `16:9`, `4:3`, `21:9`, `Kitöltés`)
- Az M3U fejlécéből automatikusan felismert, rögzített XMLTV-forrás (`url-tvg` / `x-tvg-url` / `tvg-url`) és csatorna–EPG párosítás; egyéni playlist- és EPG-források nem használhatók
- Csatornahelyes „most megy” és következő műsor, az EPG-lefedettséghez igazodó, időarányos, kétirányban navigálható műsorújság
- Magyar és angol felület; a beállítások és az alkalmazásállapot helyben, a `~/.wukki-tv/state.bin` fájlban tárolódnak
- Android 8+ támogatás Android TV launcherrel, D-pad navigációval és közvetlenül telepíthető APK-val
- Androidon Media3/ExoPlayer, desktopon libVLC gondoskodik a HLS lejátszásról

## Indítás fejlesztőként

Először töltsd le a Gradle wrappert, majd indítsd az alkalmazást:

```sh
./bootstrap-gradle.sh
./gradlew :app:run
```

Az alkalmazáson belüli lejátszáshoz elérhető VLC/libVLC runtime szükséges. Fejlesztés közben a Wukki TV először az alábbi helyeken keresi:

1. `WUKKI_VLC_HOME` környezeti változó vagy `-Dwukki.vlc.home=...` JVM paraméter
2. `runtime/vlc` a projekt gyökerében
3. platform alapértelmezett VLC telepítési helye (macOS-en például `/Applications/VLC.app`)

Példa egyedi VLC runtime-mal:

```sh
WUKKI_VLC_HOME="/Applications/VLC.app/Contents/MacOS" ./gradlew :app:run
```

## Csomagolás

Natív telepítő készíthető DMG, MSI vagy DEB formátumban. A kiadásba szánt VLC runtime-ot a `WUKKI_VLC_RUNTIME` változóval lehet az alkalmazás erőforrásai közé másolni; a licencek a `LICENSES` könyvtárból kerülnek be.

```sh
WUKKI_VLC_RUNTIME="/elérési/út/vlc-runtime" ./gradlew :app:packageDistributionForCurrentOS
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

## Korlátok

- Elsődleges cél a HLS (`.m3u8`) streamek támogatása; más streamprotokollok eredménye a használt VLC runtime-tól függ.
- A PIN-alapú szülői felügyelet jelenleg előkészített felület, még nem tartalomzárolási funkció.
