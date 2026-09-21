# WOS-14 – Rögzített Wukki referencia, v1

A WOS-15–24 implementáció kötelező kiindulópontja. A közös felület/reducerek referencia-commitja `b7e323cac36a18eb5b566bdf0c6d6eddd556150e`; a források sértetlenségét a `manifest.json` SHA-256 lenyomatai ellenőrzik. A képek e commit változatlan komponenseiből, külön teszt/debug harness-ben készültek, nem egy korábbi kiadott binárisból.

## Rögzített környezet

| Paraméter | Érték |
| --- | --- |
| Kép / viewport | 1920 × 1080 fizikai pixel |
| Compose density / fontScale; web DPR | 1 / 1; 1 |
| UI-skála / lista | 1.0 / NORMAL |
| Nyelv / időzóna | HUNGARIAN / Europe/Budapest |
| Óra | 2026-09-21 18:00:00 CEST, epoch `1790006400000` |
| Tartalom | [fixture.json](fixture.json): 4 csatorna, 24 műsor, 1 kedvenc, 2 előzmény |
| Kijelölt/játszott | `ref-1`; keresés `hir`, üres találat `nincsilyen` |
| Média/képek | Nincs dekóder vagy hálózati kép; fekete videóslot, betűs logófallback |
| Desktop | Compose ImageComposeScene / Skia, macOS arm64; 1,2 s renderidő |
| Android | Television_1080p Android TV emulátor, natív Android Compose; density a harness-ben normalizálva |
| webOS kiindulás | 0.6.0 production bundle, helyi Chrome headless; **nem fizikai TV-felvétel** |

A JSON azonos adatkészletet ad a későbbi JS teszteknek is. A webOS 0.6.0 még nem fogad közös AppState-et: a capture-adapter ugyanebből a JSON-ból M3U-t készít és a fetch választ helyettesíti. Kedvenc/EPG/előzmény azonosságot ez a régi felület még nem tud teljesíteni; a kép ennek kiinduló eltérését dokumentálja. A snapshot sémája és a webOS állapotmegőrzés sémája külön szerződés.

## Választás a desktop/Android eltéréseinél

| ID | Megfigyelés | webOS cél és indok |
| --- | --- | --- |
| D01 | A `SideNavigation.kt` fájlnév ellenére `TopNavigation` vízszintes felső menü | **Desktop:** feliratos Élő adás → Műsorújság → Csatornák → Beállítások. Android ikon-only változatát nem vesszük át. |
| D02 | Desktop kétpaneles, Android teljes képernyős beállításrészletek | **Desktop geometria**, közös SettingsNavigationState műveletekkel. |
| D03 | Desktop platformkilépés, Android két Back 2000 ms-on belül | **Android TV:** két Back a gyökérben; az első feliratot mutat. Nem léphet ki keresőből vagy dialógusból. |
| D04 | Natív Android/Skia alapértelmezett fontok kissé eltérnek | Desktop metrikák és súlyok; platformfont raszterezése külön tolerancia. Olvashatóság és tördelés nem engedmény. |
| D05 | Menühighlight: `focusedSection ?: activeSection` | A fókuszált menüpont kapja a kiemelést. A fókusz léptetése még nem vált nézetet; OK/kattintás igen. Nem találunk ki két külön aktív jelölést. |
| D06 | Csatornasor pointerkattintása előnézet; távirányító OK közvetlenül nyit | Azonos **szándékhoz** azonos hatás: sorclick = preview, Megnyitás/OK = lejátszás. Nem kell különböző gesztusokat mesterségesen azonosítani. |
| D07 | Lejátszási panel desktop Java2D, Android Compose | Közös `PlaybackInfoPanelStyle` méretek és Android Compose elrendezés; desktop adatok/állapotok egyeznek. Natív felület későbbi TV-tesztje külön szükséges. |

Ezek termék-referencia választások, nem hardverkorlát miatti felmentések. A platformkorlátokat az [eltérésjegyzék](exceptions.md) tartja nyilván; egyik sincs automatikusan elfogadva.

## Képernyő- és állapotmátrix

A PNG-k az alábbi scenario-nevekkel találhatók a [desktop](screenshots/desktop) és [Android TV](screenshots/android-tv) könyvtárban. A [webOS kiinduló kép](screenshots/webos-browser/channels.png) a jelenlegi egyoldalas felületet mutatja. A „forrás” bizonyíték nem képernyőkép vagy készülékteszt.

| ID / scenario | Állapot és ellenőrizendő tartalom | Referencia / bizonyíték | Követő US |
| --- | --- | --- | --- |
| S01 `live` | Fekete tesztvideo, látható felső menü, aktív Élő adás | DashboardScreen + LiveTvScreen; PNG mindkét kliensen | 15,18 |
| S02 `live-empty` | Nincs kiválasztott csatorna, lokalizált középső üzenet | LiveTvScreen; PNG | 18 |
| S03 `channels` | 4 sor, első fókuszált/kedvenc/játszott; 62/38 műsorpanel | ChannelBrowserScreen; PNG | 17 |
| S04 `search` | Nyitott kereső, `hir` → Hírek; kurzor a mezőben | ChannelBrowserScreen; PNG + `search-caret` trace | 16,17 |
| S05 `no-results` | Nincs találat, keresés törlése művelet | ChannelEmptyContent; PNG | 17 |
| S06 `no-data` | Üres lista, Frissítés; nincs műsorpanel-adat | ChannelEmptyContent; PNG | 17 |
| S07 `favorites` | Csak Hírek; aktív kedvencszűrő | ChannelBrowserScreen; PNG | 17 |
| S08 `recent` | Kultúra, Hírek ebben a sorrendben | ChannelBrowserScreen; PNG | 17,22 |
| S09 `guide` | 18:00 időjel, 4 csatorna, műsorblokkok, Most fókusz | EpgGuideScreen; PNG | 23 |
| S10 `guide-details` | Műsorcím/leírás/idő/következő, Mégse/Megnyitás | GuideProgrammeDetails; PNG + `dialog-back` | 23 |
| S11 `settings` | Hét kategória, kezdőállapot; desktop és Android layout eltérés | SettingsScreen; PNG | 21 |
| S12 `settings-playback` | Autoplay, hangerő, puffer, képarány, reconnect/próbák | SettingsPanes; PNG + `settings-back` | 21 |
| S13 `quick-settings` | Képarány + két hangsáv; képességfüggő opciók | PlaybackQuickSettingsPanel; PNG | 18,21 |
| S14 `offline` | Cache lista + hiba-snackbar, nincs adatvesztés | DashboardScreen/AppFeedback; PNG; hiba a harness-ben befecskendezve | 19,22 |
| S15 előnézet | Játszott ref-1 mellett ref-2 információ; nincs streamváltás OK-ig | LiveChannelPreview + PlaybackInfoPanelStyle; `preview-confirm/cancel/timeout` trace, forrás | 18 |
| S16 indulás/betöltés | Középső `storage.loading` + spinner; hiba `storage.load.failed` + újrapróba | AppBootstrapHost; forrás, animáció nem része a PNG-knek | 19,22 |
| S17 lejátszás/pufferelés | PLAYING alatt nincs hiba; BUFFERING spinner; OPENING állapotszöveg | PlaybackOverlayMapper, AndroidPlaybackOverlay, PlaybackOverlayRenderer; forrás | 18,19 |
| S18 streamhiba/reconnect | Közös recovery dialógus: próbák, Újra/Mégse, Csatornák, Részletek | PlaybackRecoveryDialog + PlaybackRecoveryNavigation; forrás | 19 |
| S19 többi üres állapot | LOAD_FAILED, NO_FAVORITES, NO_RECENT, NO_CATEGORY_RESULTS → REFRESH/SHOW_ALL | ChannelBrowserContract + ChannelEmptyContent; forrás | 17 |
| S20 további beállítás/dialógus | EPG, Megjelenítés, Szülői placeholder, Playlistek, Nyelv, Névjegy/jogi, dropdown | SettingsScreen/SettingsPanes; forrás | 21 |

A rögzített képek UI-referenciák. Valódi videokép, codec, Magic Remote, OS-billentyűzet, lifecycle és hálózatvesztés igazolása WOS-18/19/22/24 készüléktesztje; a fenti forrásalapú sorok nem állítják, hogy ezek már TV-n sikeresek.

## Vizuális szerződés és források

Az alábbi utak a repó gyökeréhez képest értendők; teljes SHA-256 lenyomatuk a manifestben. A forrás a pontos szabály, az alábbi számok annak áttekintése.

| Terület | Rögzített szabály | Forrás `shared/src/commonMain/kotlin/hu/wukki/tv/` alatt |
| --- | --- | --- |
| Színek | background #07101A, surface #101D2B, primary #8B5CF6, focus #A277FF; alpha is megőrzendő | `ui/components/Theme.kt`; összes token: `visual-tokens.json` |
| Dashboard | scale=min(width/1470,height/920), clamp .70..1.45; padding=14dp×scale clamp8..20 | `ui/app/DashboardScreen.kt` |
| Felső menü | 48dp overlay; öt egyenlő oszlop a branddel; alsó sarkok24dp, item padding3dp | `ui/navigation/SideNavigation.kt` |
| Brand/icon/text | Wukki 36×scale sp Black; TV17 Bold; Material Outlined LiveTv/CalendarMonth/FormatListBulleted/Settings; menü14×scale sp | `ui/navigation/SideNavigation.kt` |
| Csatornák | scale max1; 62/38 panelarány; cím28sp Bold; kereső56dp; fülsor50dp | `ui/channels/ChannelBrowserScreen.kt` |
| Sor | COMPACT64 / NORMAL88 / DETAILED120dp × scale; név16/18sp SemiBold, szám18/22 Light; 8dp sarok | `ui/channels/ChannelBrowserScreen.kt` |
| Műsorpanel | 24sp Bold név; 5dp haladás; 48dp Megnyitás gomb; hiányzó kép/logó fallback | `ui/channels/ChannelBrowserScreen.kt` |
| Beállítások | scale=min(width/1116,height/892), clamp .70..1; desktop bal panel430dp×scale, gap34dp×scale | `ui/settings/SettingsScreen.kt` |
| EPG | Saját idővonal, fejléc, cellák, jelölések és fókusz; nem generikus HTML-táblázat | `ui/guide/EpgGuideScreen.kt`, `GuideNavigationHeader.kt`, `GuideProgrammeDetails.kt` |
| Élő információ | width92%, max760; minHeight162; margin28,padding18,gap18; cím19sp | `player/PlaybackInfoPanelStyle.kt` |
| Szövegek | Magyar/angol kulcsok, fallback és időformázás ugyanabból a forrásból | `ui/components/Localizer.kt`; `shared/src/commonMain/resources/i18n/messages_{hu,en}.properties` |

Nincs új fontcsalád vagy ikoncsomag kijelölve: a Compose alapértelmezett tipográfia/metrika és a felsorolt Material ikonok a referencia. A képek platformonkénti fonteltéréseit nem szabad CSS-eltérésként „kijavítani” a másik képre másolással.

## Input-szerződés

[input-traces.json](input-traces.json) 13 forgatókönyvet tartalmaz explicit kezdőállapottal, eseménnyel, következő állapottal, rendezett mellékhatáslistával és `handled` értékkel. A `ParityInputTraceTest` közvetlenül ezt olvassa és a tényleges közös reducerrel ellenőrzi. Az elvárt eredményeket nem generálja újra a tesztelt kódból.

Fontos határ: a reducer **kiadja**, de nem hajtja végre a mellékhatásokat. Például ActivateSection után a controller vált nézetet; CLOSE_CHANNEL_SEARCH után a callback törli a model query-t. A JSON ennek megfelelően nem állít végponttól végpontig UI-tesztet. WOS-16 a JS-reducert és a DOM-adapter mellékhatásait is e szerződéshez köti.

| Felhasználói művelet | Kötelező hatás / sorrend |
| --- | --- |
| Felső menü Left/Right | Index lép, nézet nem; Down tartalom, OK aktivál |
| Lista fel/le | Fókusz/előnézet változik; OK OpenChannel, jobb→kedvenc, OK ToggleFavorite |
| Pointer sor / Megnyitás | onChannelPreviewSelect / onOpenChannel; nem ugyanaz a szándék |
| Kereső Back | CLOSE_CHANNEL_SEARCH → query törlés, listafókusz-zóna, főmenü fókusz; Escape a keresőben listafókusz |
| Szöveges kereső Left/Right/Backspace | Szerkesztésé marad a gomb, nem csatornaváltás |
| Élő Next előnézet | Rejtett panelnél első lépés az aktuális csatorna; második a következő; OK indít |
| Channel+/− / Previous | Közvetlen váltás / előző sikeres csatorna, nem előnézet |
| Back | Dialógus → keresés → élő réteg → beállításrészlet → menü → LIVE → gyökérkilépés; rejtett LIVE navigáció előbb megjelenik |
| Számbevitel | Legfeljebb4 számjegy; OK azonnal, 3000ms inaktivitás után automatikus kiválasztás |
| Időzítők | Élő menü5000ms; információ/előnézet5000ms; success feedback3000ms, error8000ms; gyökér dupla Back2000ms |

Az időzítők értékei a `WukkiApp.kt`, `LiveNavigationTimeout.kt`, `ExitConfirmation.kt` forrásokból származnak; a reducer-trace-ben a TIMEOUT explicit esemény. Felfüggesztett alkalmazásban a timeoutokat a lifecycle-szabály vezérli.

## Újrafuttatás és ellenőrzés

A repó gyökeréből:

```sh
./gradlew :desktopApp:test --tests '*ParityInputTraceTest'
./gradlew :desktopApp:captureParityReferences :androidApp:assembleDebug
# Előbb indítsd a meglévő Television_1080p AVD-t; a script csak emulátort céloz.
python3 tools/parity/capture_android.py
./gradlew :webosApp:jsBrowserDistribution
python3 tools/parity/capture_webos.py
python3 tools/parity/verify_reference.py
```

A normál tesztfutás nem írja felül a PNG-ket; erre külön capture task van. A capture tudatos baseline-frissítés, diff-review szükséges. A validator eltérő hash esetén hibázik, nem fogadja el automatikusan az új képet. A manifest újragenerálása csak felülvizsgált új referenciaverziónál indokolt.

A két Compose capture nem bootolja a teljes éles appot: a változatlan DashboardScreen-t hívja determinisztikus model/session inputtal, mellékhatás nélküli callbackekkel. A tényleges felhasználói interakciót a reducer-fixture és a meglévő controller-tesztek ellenőrzik. A debug Activity a release APK-ban nincs benne. A készülékfüggő nyitott kérdések nem akadályozzák a WOS-15 arculat/keret implementálását, de a kiadási paritás igazolását igen.
