# Wukki TV – LG webOS megvalósítási terv

Készült: 2026-09-19. Állapot: P0/P1 technikai alap és IPK-csomagolási feladat elkészült, fizikai TV-s mérés nélkül.

## 2026-09-21-i termékirány és elsőbbség

A cél a desktop/Android kliens megjelenésének és működésének webOS-paritása. Az aktív hatókört, sorrendet és kiadási feltételeket az [újratervezett user storyk](webos-user-stories.md) adják; az alábbi korábbi technikai terv eltérő prioritásai helyett ezek érvényesek. A DOM-prototípus újrahasználható technikai alap, nem végleges termékfelület. A WOS-14 [rögzített referenciacsomagja](webos-parity/v1/README.md) elkészült; következő feladat a közös arculat és navigáció (WOS-15–16). A közös főmenü vízszintes felső menü.

A teljes műsorújság az egységes első kiadás része. A médiaadapter készülékfüggő képességeit külön igazoljuk; a látható eltéréseket nem tekintjük automatikusan elfogadott paritási kivételnek. A terv többi szakasza technikai háttéranyagként marad meg.

## Cél és döntési alap

A Wukki TV külön, telepíthető webOS kliensként megvalósítható. Első cél a saját TV-n, Developer Mode segítségével telepített alkalmazás. A korábbi beszélgetés alapján a tervezési minimum webOS 5.x; a pontos TV-modell, SDK-verzió és firmware még ellenőrizendő. A firmware verziószáma önmagában nem azonos a webOS platformverzióval.

Megvalósított alap: külön `core` KMP-modul Android, JVM és JavaScript célplatformmal, valamint erre épülő `webosApp` Kotlin/JS webapp natív HTML-video lejátszással. A próba közvetlen DOM-felületet használ, hogy a Kotlin/JS futtatás, a TV böngészője és a médialejátszás külön mérhető legyen. A Compose webes felületéről a fizikai TV-s teljesítménypróba után születik döntés.

A webOS 5.x Chromium 68-at használ. A JS service futtatókörnyezete webOS 5.0-n Node 8.12.0, ezért a frontend és a service külön fordítási célt és kompatibilitási ellenőrzést kap. A fejlesztőgépen modern, támogatott Node használható. [LG web engine](https://webostv.developer.lge.com/develop/specifications/web-api-and-web-engine), [LG JS service](https://webostv.developer.lge.com/develop/guides/js-service-basics).

Flutter nem megfelelő a feltételezett minimumhoz: az LG hivatalos támogatása webOS TV 26 Re:New és újabb rendszerekre vonatkozik. [LG bejelentés](https://webostv.developer.lge.com/news/flutter-for-webos-tv-is-now-available-for-developers).

## Kapcsolódás a jelenlegi repóhoz

| Meglévő elem | webOS-terv |
| --- | --- |
| `shared/build.gradle.kts`: desktop JVM + Android target | A UI-független típusok már a háromplatformos `core` modulban vannak. |
| `WukkiApplication`, repositoryk, típusos hibák | Fokozatosan kerülnek a `core` modulba, platformadapterek mögé. |
| `data/OfficialWukkiSource.kt` | Az egyetlen hivatalos playlist és a belőle származó EPG marad a forrás. |
| `data/PlaylistParser.kt`, `data/XmlTvParser.kt` | A playlist-parser és az EPG-párosítás már `core`; a platformfüggetlen XMLTV-parser következő lépés. |
| `domain/Models.kt` | Áthelyezve a `core` modulba; ugyanaz a Kotlin-kód fut JS-en is. |
| `JvmRemoteTextLoader`, `JvmXmlTvParser` | Új hálózati és streaming XML-adapter szükséges. |
| Media3 / libVLC | HTML-video adapter, saját lejátszási állapotgéppel. |
| Compose UI, Android WorkManager | DOM-felület és webOS-életciklus-kezelés. |
| `docs/network-policy.md`, `docs/application-layer.md` | A hálózati limitek és állapotkezelési szabályok alapjai. |

A Compose-független részek kiválasztása megkezdődött. A modellek, csatornaelőzmény, M3U-feldolgozás, stabil csatornaazonosítás és EPG-párosítás a `core` modulból JVM-en és JS-en is tesztelhető. Az alkalmazásréteg, hálózat és XML-feldolgozás következő szeletek.

A platformfüggetlen szerződések és tesztek helye a `core` modul. Ugyanazok a common tesztek futnak JVM-en és JavaScripten; fixture-rel kell lefedni az idézett M3U-attribútumokat, EPG URL-t, `tvg-chno`/`tvg-shift` értékeket, duplikátumokat, Unicode-normalizálást, EPG-párosítást és a sikeres lejátszási előzményt. A jelenlegi csatorna-ID Java `UUID.nameUUIDFromBytes` eredmény; a JS-megvalósításnak ezt a pontos algoritmust kell reprodukálnia és közös fixture-rel igazolnia.

## Első döntési kapu: lejátszási és hálózati próba

Először minimális, csomagolt alkalmazás készüljön egy videóelemmel, csatornaválasztóval és diagnosztikával. Nem elegendő a laptop böngészőjében működő prototípus.

1. TV-modell, SDK-verzió, firmware és távirányító rögzítése; a `packageWebOs` feladattal készített IPK telepítése és a debug kapcsolat ellenőrzése.
2. A hivatalos M3U aktuális példányának vizsgálata: URL-ek, átirányítások, protokollok, manifesztek, kodekek, esetleges fejlécelvárások. A repó dokumentációja HTTP-s élő streameket említ; ez jelenleg dokumentált állapot, nem friss hálózati mérés.
3. Playlist és EPG lekérése a csomagolt appból: CORS, TLS, gzip, méret és HTTP/HTTPS viselkedés mérése. A fejlesztői szerver proxyja ne fedje el a TV-s hibákat.
4. Legalább 5 reprezentatív csatorna kipróbálása; ha kevesebb van, mindegyiké. Eltérő streamcsaládonként külön minta: HTTP/HTTPS, felbontás, kodek, master/media playlist és átirányítás.
5. Hideg indulás, 20 csatornaváltás, legalább 30 perc lejátszás, hálózatmegszakítás és alkalmazásból kilépés/visszatérés.
6. Jegyzőkönyv: első kép ideje, hang/kép, hibakód, újracsatlakozás, memória alakulása, támogatott/nem támogatott források.

Az LG támogatja a HLS-t, de dokumentált tag- és médiaformátum-korlátokkal. A `.ts` kiterjesztés vagy a HLS-támogatás önmagában nem garantálja egy tetszőleges IPTV stream működését. [LG streaming](https://webostv.developer.lge.com/develop/specifications/streaming-protocol-drm), [webOS 5.0 médiaformátumok](https://webostv.developer.lge.com/develop/specifications/video-audio-50).

**Továbbhaladás:** ha a szükséges csatornák közvetlenül mennek, folytatható az önálló kliens. Ha csak az M3U/EPG lekérése akad el, a TV-n futó JS service kezelje az adatforgalmat. Ha a média igényel külön fejléceket vagy átalakítást, először a dokumentált platformlehetőségeket kell igazolni, majd külön becslés készül egy opcionális proxyra. Egy adatletöltő service nem oldja meg automatikusan a videó szegmens- és kulcslekéréseit. Nem támogatott kodek esetén egy egyszerű proxy sem elég; transzkódolás kellhet. Külső szerverfüggőség külön termékdöntés.

## Tervezett szerkezet

```text
core/                    # közös KMP modellek és üzleti szabályok
webosApp/
  src/jsMain/kotlin/     # Kotlin/JS belépési pont és platformadapterek
  src/jsMain/resources/  # appinfo.json, HTML, CSS és ikon
  service/               # feltételes: XMLTV/adatletöltés, külön Node 8 build
docs/webos-testing.md    # létrehozandó TV-s tesztjegyzőkönyv
```

A felület a use case-ekkel és adapterekkel kommunikál. A lejátszó a stream URL-jét közvetlenül kapja; az EPG hibája nem akadályozhatja az élő adást. A JS service csak akkor kerül a kiadási csomagba, ha a próba igazolja a szükségességét.

## Funkcionális hatókör

| Funkció | Első használható kiadás | Későbbi bővítés |
| --- | --- | --- |
| Hivatalos M3U, rendezés, kategória, keresés | Igen | — |
| Natív HLS, csatornaváltás, hibajelzés | Igen | További igazolt médiaformátumok |
| Kedvencek, utolsó csatorna, legutóbbi 10 sikeres csatorna | Igen | — |
| Most/következő EPG és teljes, virtualizált műsorújság | Igen, WOS-20 és WOS-23 | — |
| D-pad, OK, Back, Magic Remote kattintás | Igen | További készülékspecifikus gombok |
| Magyar/angol, helyi állapot, kézi frissítés | Igen | — |
| 6/12/24 órás frissítés | Előtérben és visszatéréskor, ha esedékes | Háttérütemezés csak külön igazolással |
| Képarány | Igazolt módok | Hangsáv/felirat képességvizsgálat alapján |
| Pufferprofil, rendszerhangerő | Nem ígért paritás; hangerő a TV rendszerén | Csak ténylegesen támogatott vezérlés |
| Timeshift, felvétel, DRM-integráció, egyedi forrás | Nem része | Önálló igény és terv |
| LG Store publikálás | Nem része | Külön kiadási szakasz |

## Adatok, tárolás és életciklus

- M3U: 2 MiB; XMLTV: tömörítve és kibontva legfeljebb 32 MiB, a meglévő hálózati szabályok szerint. Böngészőben a hálózati tömörítés előtti méret nem mindig ellenőrizhető; ha a kettős limit kötelező, a letöltést service végezze. A jelenlegi 15/30 másodperces kapcsolódási/olvasási határokhoz igazodó, adapterenként dokumentált timeoutok szükségesek.
- XMLTV: inkrementális parser, DTD/külső entitás tiltása, gzip-kezelés, explicit időzóna és `tvg-shift`. A frontend worker vagy service csak a Wukki-csatornák releváns, kezdetben tegnaptól +7 napig terjedő műsorait tartsa meg. A teljes XML DOM-ként ne kerüljön memóriába.
- Műsorindex: csatornánként rendezett listák és bináris keresés. A service használatakor lapozott időablakos lekérdezés legyen, ne teljes EPG-átvitel Luna-üzenetben. A service fájljainak közvetlen frontend-elérhetőségére nem szabad építeni. [LG service-korlátok](https://webostv.developer.lge.com/develop/guides/js-service-basics).
- Beállítások, előzmények és playlist-cache: verziózott, egyetlen művelettel cserélt Web Storage snapshot; az első próbában a tényleges TV-s tartósságot ellenőrizni kell. Az üres vagy sérült állapot nem írhatja felül az előző snapshotot, a kvótahiba pedig látható marad. A későbbi, nagy EPG-cache külön IndexedDB-adatbázisba kerül.
- Egyszerre egy frissítés; az azonos forrásra érkező kérések összevonása. Félbeszakadt vagy elavult válasz nem cserélhet le jó cache-t. A frissítés közben módosított kedvencek megmaradnak.
- Mentés módosítás után, rövid összevonási ablakkal; háttérbe kerüléskor azonnali flush-kísérlet. A működés ne függjön kizárólag a bezárási eseménytől.
- Háttérbe kerüléskor lejátszás és UI-időzítők leállítása, visszatéréskor állapot- és esedékességvizsgálat, igény esetén új streamnyitás. Relaunch nem hozhat létre második lejátszót. [LG életciklus](https://webostv.developer.lge.com/develop/guides/app-lifecycle-management).
- A JS service nem állandó daemon: az LG könyvtára inaktivitáskor leállíthatja. A feladat/feliratkozás élettartamát kezelni kell; a kiadás nem ígér kikapcsolt TV mellett periodikus frissítést. [LG service-életciklus](https://webostv.developer.lge.com/develop/guides/js-service-faq).

## Lejátszó és távirányító

Egyetlen videoelem, `idle → loading → playing → buffering → error/stopped` állapotokkal. Minden csatornaváltás új sessionazonosítót kap; korábbi esemény és retry nem írhatja felül az új csatornát. Előzmény csak igazolt lejátszás után mentődik. Korlátozott újrapróbálás, növekvő várakozással; kézi váltáskor minden korábbi retry törlődik. Hiba esetén a csatornalista elérhető marad.

A fókusz nézetenként stabil elemazonosítóra épül. Nyilak navigálnak, OK aktivál, csatornagombok élő adásban váltanak, számbevitel késleltetett csatornaválasztást végez. A nyomva tartott gomb ismétlését kezelni kell. Pointer és billentyűzet ugyanazt az aktív elemet használja; virtuális lista nem távolíthatja el helyreállítás nélkül a fókuszt.

Back sorrend: párbeszédablak → overlay → aloldal → gyökérnézet → platform Back. Az alkalmazás saját navigációjához `disableBackHistoryAPI` és a 461-es gomb kezelése tervezett. A gyökérben a `webOS.platformBack()` platformverzió szerinti viselkedését használjuk. [LG Back-kezelés](https://webostv.developer.lge.com/develop/guides/back-button).

## Megvalósítási sorrend és készültségi feltételek

A becslések egy fejlesztő munkanapjai, elérhető teszt-TV mellett; nem vállalási határidők. A szakaszok sorrendben függnek egymástól.

| Szakasz | Tartalom | Elfogadási feltétel | Becslés |
| --- | --- | --- | --- |
| P0 | Telepíthető hálózati/lejátszási próba | TV-s jegyzőkönyv és közvetlen/service/proxy döntés | 2–4 nap |
| P1 | Appváz, KMP core, kompatibilis JS build, közös tesztek | Webes csomag elkészült; az IPK és TV-s indítás még hátra van | 2–3 nap |
| P2 | Playlist, lista, fókusz, lejátszó, kedvencek, mentés | Távirányítóval nézhető TV; újraindítás után helyes állapot | 4–6 nap |
| P3 | XMLTV, now/next, cache, frissítés, HU/EN | EPG-hiba mellett is lejátszik; időzóna/cache tesztek zöldek | 3–5 nap |
| P4 | Életciklus, hibák, hosszú próba, CI, kiadási IPK | Az alábbi kiadási ellenőrzések teljesülnek | 3–5 nap |
| P5 | Teljes műsorújság, további kényelmi funkciók | Időarányos, kétirányú navigáció minimum TV-n | +4–7 nap |

Első használható és ellenőrzött kiadás: **14–23 munkanap**, plusz 20–30% tartalék a készülékspecifikus hibákra. A szükségessé váló adatservice külön +2–4 nap; médiaproxy/transzkódolás és Store-jóváhagyás nincs ebben a becslésben. P0 után újrabecslés szükséges.

## Ellenőrzés és kiadás

Automatizált ellenőrzések:

- M3U/EPG/ID/normalizálás közös fixture-jei; időzóna, hibás XML, gzip és méretkorlát.
- Elavult kérés és lejátszóesemény, gyors csatornaváltás, retry-megszakítás, frissítés közbeni kedvencmódosítás.
- Sérült tároló, kvótahiba, megszakított cache-frissítés; felhasználói adatok megőrzése.
- Böngészős D-pad/Back/pointer tesztek, modal-fókusz és virtualizált lista. Mock lejátszóval determinisztikusan; valódi HLS-hez TV-s teszt.
- Typecheck, lint, unit/integrációs teszt, production build, függőségek és kibocsátott JS/CSS kompatibilitása. A szintaxis transzpilálása nem pótolja a hiányzó Web API-kat.

Fizikai TV-s kiadási kapu: minden aktuális hivatalos csatorna dokumentált eredménye; legalább 100 váltás, 2 óra folyamatos lejátszás, 20 háttér/előtér ciklus, hálózatvesztés és visszaállás, újraindítás utáni adatmegőrzés. Elvárás: nincs összeomlás, beragadt fókusz, háttérhang vagy korlátlanul növekvő memória. Tervezési teljesítménycélok: fókuszválasz p95 <100 ms, cache-ből kezelhető felület <3 s, stabil referenciaforrás első képe p95 <8 s; P0-ban kalibrálandók, nem jelenlegi mért eredmények.

CI: külön webOS job modern Node-dal, Gradle által zárolt npm-függőségekkel, ellenőrzésekkel, production builddel és `ares-package` csomagolással; a service kimenetét külön Node 8 kompatibilitási próbának kell alávetni. A meglévő Gradle CI megmarad. Az Action-hivatkozások teljes commit SHA-ra rögzítendők. A kiadási artifact IPK + checksum + verziómetaadat, a meglévő UTC dátum/Git SHA verziózásból levezetve; az `appinfo.json` numerikus verzióját az LG formátumhoz kell igazítani, a teljes Wukki-verzió a Névjegyben marad.

Telepítési leírás: LG Developer Mode, azonos hálózat, TV-regisztráció, kulcslekérés, IPK telepítés, indítás és inspect a hivatalos CLI-vel. A Developer Mode időkorlátos; letiltása a fejlesztői appok eltávolítását okozhatja. Ez fejlesztői terjesztés, nem tartós Store-telepítés. [LG Developer Mode](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app), [LG CLI](https://webostv.developer.lge.com/develop/tools/cli-introduction).

Store-kiadás külön munka: aktuális LG checklist, alkalmazásazonosító és grafikai anyagok, adatkezelési/tartalomterjesztési feltételek, készülékmátrix és beküldési folyamat ellenőrzése. [LG jóváhagyási folyamat](https://webostv.developer.lge.com/distribute/app-approval-process).

## Nyitott adatok az implementáció előtt

A terv ezek nélkül is végrehajtható előkészítő munkára bontható; a teljes készülékmátrixhoz szükséges a TV pontos típusa és SDK-verziója. A Developer Mode telepítés, egy referenciafolyam képe és hangja, valamint a 0.3.0 automatikus playlist-betöltése és csatornalistája fizikai TV-n már igazolt.
