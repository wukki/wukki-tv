# Wukki TV desktop MVP

Kotlin Multiplatform / Compose Desktop alkalmazás IPTV playlist-ek és XMLTV műsorújság kezelésére.

## Indítás

Első alkalommal töltsd le a Gradle wrappert, majd indítsd az alkalmazást:

```sh
./bootstrap-gradle.sh
./gradlew :app:run
```

## Amit az MVP tud

- M3U import URL-ről vagy helyi fájlból, több playlist kezelése
- Playlist manuális és alkalmazáson belüli időzített frissítése
- XMLTV URL import és csatorna–EPG automatikus párosítása
- Kedvencek, kategória- és szöveges keresés
- „Most megy”, következő műsor, mini guide és teljes guide nézet
- Billentyűzetes csatornaváltás: `PageUp` / `PageDown`, nyilak, Enter, számok
- Megszakítás nélküli helyi adatmentés a `~/.wukki-tv/state.bin` fájlba

A stream megnyitása az MVP-ben az operációs rendszer alapértelmezett lejátszójában/böngészőjében történik. Ez a feldolgozási és UI-funkciók gyors tesztelésére szolgál; az Android TV célalkalmazásban ezt Media3/ExoPlayer váltja fel.
