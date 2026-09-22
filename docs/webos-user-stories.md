# Wukki TV webOS – megjelenési és működési paritás

Állapot: 2026-09-21. Kiindulás: webOS 0.6.0, referencia-commit: `b7e323cac36a18eb5b566bdf0c6d6eddd556150e`.

## Cél és referencia

A webOS alkalmazás ugyanazt a Wukki terméket jelenítse meg és ugyanazokat a felhasználói folyamatokat kövesse, mint a desktop és Android kliens. A korábbi backlog funkcionális prototípust tervezett; teljesítése önmagában nem eredményezett volna azonos megjelenést és viselkedést.

A referencia a repó közös Compose felülete és navigációs szerződése. Nagyképernyős elrendezéshez a desktop, távirányítós bevitelhez az Android TV viselkedése az alap. Az Android érintős, keskeny képernyős elrendezését nem kell 1920×1080-ra nyújtani. Azonos képernyőméret, nyelv, beállítás, adat és alkalmazásállapot mellett hasonlítunk. Ahol a két referencia eltér, WOS-14 rögzíti a választást; új, önkényes webOS-viselkedés nem válhat alapértelmezetté.

A megjelenítési technológia maradhat Kotlin/JS és DOM/CSS. A paritás nem igényel Compose-portot, de megköveteli a közös vizuális értékek és állapotátmenetek átvételét. A működő natív videóadapter megmarad.

## A kódban igazolt eltérések

| Terület | Desktop/Android referencia | webOS 0.6.0 eltérése |
| --- | --- | --- |
| Főképernyők | `DashboardSection`: LIVE, GUIDE, CHANNELS, SETTINGS | Egyetlen lista és lejátszási nézet |
| Arculat | `WukkiColors`: sötét felületek, lila elsődleges és fókuszszín | Kék próba-UI, saját térközök és vezérlők |
| Csatornák | Szűrőfülek, külön megnyitható kereső, lista és műsorpanel 62/38 arányban | Állandó kereső, körbeforgó kategóriagomb, diagnosztikai oldalsáv |
| Kijelölés | Fókusz, előnézeti és játszott csatorna külön állapot | A sor aktiválása közvetlen lejátszást indít |
| Élő TV | Előnézeti állapotgép, megerősítés, külön közvetlen csatornaváltás | Nyilak közvetlen streamváltást végeznek |
| Vissza | Keresés, dialógus, beállításrészlet, navigáció külön kezelve | HUD → lista → platformkilépés |
| EPG | Teljes műsorújság már a közös kliens része | A régi terv későbbi bővítésként kezelte |
| Beállítások | Hét szekció és gyorsbeállítási dialógus | Tárolási mezők vannak, megfelelő felület nincs |

Kódreferenciák (a fenti commit szerint):

- [Téma](../shared/src/commonMain/kotlin/hu/wukki/tv/ui/components/Theme.kt), [dashboard](../shared/src/commonMain/kotlin/hu/wukki/tv/ui/app/DashboardScreen.kt), [főmenü](../shared/src/commonMain/kotlin/hu/wukki/tv/ui/navigation/MainNavigation.kt).
- [Csatornaképernyő](../shared/src/commonMain/kotlin/hu/wukki/tv/ui/channels/ChannelBrowserScreen.kt), [UI-szerződés és üres állapotok](../shared/src/commonMain/kotlin/hu/wukki/tv/ui/channels/ChannelBrowserContract.kt).
- [Távirányító-reducer](../shared/src/commonMain/kotlin/hu/wukki/tv/ui/navigation/AppRemoteReducer.kt), [élő előnézet](../shared/src/commonMain/kotlin/hu/wukki/tv/ui/navigation/LiveChannelPreview.kt), [navigáció időzítése](../shared/src/commonMain/kotlin/hu/wukki/tv/ui/app/LiveNavigationTimeout.kt).
- [Műsorújság](../shared/src/commonMain/kotlin/hu/wukki/tv/ui/guide/EpgGuideScreen.kt), [beállítások](../shared/src/commonMain/kotlin/hu/wukki/tv/ui/settings/SettingsScreen.kt), [beállítási panelek](../shared/src/commonMain/kotlin/hu/wukki/tv/ui/settings/SettingsPanes.kt).

Ez forráskód-alapú összevetés. Háromplatformos képernyőkép- és készülékellenőrzés még nem történt.

## Korábbi munka és azonosítók

A régi azonosítókat nem számozzuk át és nem használjuk fel más célra. A teljes régi backlog a [v1 archívumban](webos-user-stories-v1.md) olvasható. Az alábbi új storyk az aktív terv; a régi hátralévő feladatok ezekbe kerültek, nem kell őket párhuzamosan is implementálni.

| Régi story | Megőrzött eredmény / új helye |
| --- | --- |
| WOS-01, WOS-02 | TV-n igazolt playlist-betöltés és közös parser; újraimplementálás nem szükséges |
| WOS-03 | Lejátszási megbízhatóság → WOS-19 |
| WOS-04 | Távirányítás/HUD technikai alap elkészült; paritás → WOS-16, WOS-18 |
| WOS-05 | Helyi tárolás elkészült; teljes UI-/állapotparitás és TV-s tartósság → WOS-17, WOS-22 |
| WOS-06 | Keresés/rendezés/virtualizáció alap elkészült; referencia szerinti felület és fókusz → WOS-17 |
| WOS-07 | Kedvencek és előzmények → WOS-17 |
| WOS-08 | Életciklus → WOS-22 |
| WOS-09 | EPG-adatok → WOS-20 |
| WOS-10 | Adatfrissítés → WOS-22 |
| WOS-11 | Teljes beállítási és nyelvi paritás → WOS-21 |
| WOS-12 | Kiadás és ellenőrzés → WOS-24 |
| WOS-13 | Teljes műsorújság → WOS-23, a paritási kiadás része |

WOS-04–06 korábbi állapota „implementálva, TV-s ellenőrzésre vár”; ez nem jelent igazolt UI-paritást. WOS-14 állapota: **implementálva és referencia-harness-szel ellenőrizve**; [verziózott referenciacsomag](webos-parity/v1/README.md). WOS-15–17 állapota: **implementálva, TV-s ellenőrzésre vár**. WOS-18–24 állapota: **tervezett**.

## WOS-14 – Rögzített képernyő- és viselkedési referencia

**Story:** Nézőként minden platformon ugyanazt a terméket szeretném használni, egyértelműen meghatározott közös működéssel.

**Prioritás:** P0. **Függőség:** nincs. A rögzített referencia és az ellenőrzési eredmények a [WOS-14 csomagban](webos-parity/v1/README.md) vannak.

**Elfogadás:**

- Verziózott mátrix tartalmazza mind a négy főnézetet, a keresést, előnézetet, dialógusokat, gyorsbeállításokat, valamint a betöltés, üres, hiba, offline és lejátszás állapotokat.
- A referencia-commitból azonos fixture-rel, rögzített idővel, nyelvvel, UI-skálával és viewporttal desktop és Android TV képernyőképek készülnek; a webOS kiinduló képe is melléjük kerül.
- Képernyőnként rögzített a menü, panelek, méretek, tipográfia, ikonok, fókusz és szövegek forrása. Android/desktop eltérésnél dokumentált, melyik változatot követi a TV.
- Eseménysorok adják meg a kezdőállapotot, gombot/kattintást, következő állapotot és mellékhatást. Lefedik a Back/OK/nyilak, keresőbezárás, előnézet, közvetlen csatornaváltás, számbevitel és előző csatorna működését.
- A platformeltérések jegyzéke kezdetben vizsgálandó tételeket tartalmaz; nem tekintjük automatikusan elfogadott eltérésnek a jelenlegi webOS-megoldást.

## WOS-15 – Közös arculat és alkalmazáskeret

**Story:** Nézőként már az első képernyőről ugyanazt a Wukki alkalmazást szeretném felismerni.

**Prioritás:** P0. **Függőség:** WOS-14.

**Elfogadás:**

- A WukkiColors szemantikus színei, márkajelzése, ikonrendszere, betűméretei, térközei, sarokkerekítései és fókuszjelölései megjelennek webOS-en, a referencia skálázási szabályával.
- A vízszintes felső főmenü sorrendje Élő adás, Műsorújság, Csatornák, Beállítások; a referencia szerint a fókuszált elem kiemelése elsőbbséget élvez az aktívval szemben.
- A nézetváltás és a videó elhelyezése a dashboard referenciáját követi. Nem keletkezik második stream vagy indokolatlan újraindítás nézetváltáskor.
- A főképernyőről eltűnik a forrás- és diagnosztikai panel; a diagnosztika a megfelelő beállítási területről érhető el. A stream-URL nem része a napi böngészésnek.
- Minden főnézet útvonala létezik. Átmeneti placeholder fejlesztés közben lehetséges, de nem teljesíti a kapcsolódó képernyőstoryt vagy a paritási kiadást.
- 1920×1080-on a referencia-geometria teljesül; a támogatott más TV-felbontásokon nincs levágás vagy fókuszvesztés.

## WOS-16 – Azonos navigáció és fókuszviselkedés

**Story:** Nézőként a megszokott gombokkal ugyanazokat az állapotváltozásokat szeretném elérni.

**Prioritás:** P0. **Függőség:** WOS-14, WOS-15.

**Elfogadás:**

- A Compose-független navigációs szabályok közös KMP-rétegbe kerülnek, vagy ugyanazon esemény-fixture-ök igazolják a két implementációt. A referencia javítását mindhárom kliens örökli.
- A főmenü, tartalom, szűrők, kereső, lista, kedvencgomb, beállítások és dialógusok között a referencia fókuszútjai működnek; visszalépés után az előző érvényes cél áll helyre.
- Back a referencia állapotgépe szerint zár keresést/réteget/részletet, jelenít meg navigációt vagy tér vissza Élő TV-re; platformkilépés csak a gyökérbeli kilépési szabály teljesülésekor történik.
- D-pad, OK, hosszú gombnyomás, Magic Remote, valamint keresőben kurzormozgatás és képernyőbillentyűzet nem aktivál rejtett vezérlőt. Az azonos szándékú pointeres és távirányítós művelet azonos hatású; a sorclick előnézetet, a Megnyitás/OK lejátszást kér a WOS-14 szerződés szerint.
- A készüléken elérhető szám-, csatorna- és egyéb gyorsgombokhoz dokumentált leképezés tartozik; hiányzó gomb funkciója látható vezérlővel elérhető.

## WOS-17 – A teljes Csatornák képernyő paritása

**Story:** Nézőként ugyanúgy szeretnék böngészni, keresni és előnézetet választani, mint desktopon és Androidon.

**Prioritás:** P0. **Függőség:** WOS-15, WOS-16. Az EPG-tartalom végső elfogadása WOS-20 után.

**Elfogadás:**

- Fejléc és vízszintes szűrőfülek: Összes, Kedvencek, Legutóbbiak, Előző csatorna és kategóriák; külön nyitható/zárható kereső a referencia törlési és kilépési szabályaival. A körbeforgó kategóriagomb megszűnik.
- A bal oldali csatornalista és jobb oldali műsorinformáció a közös képernyő 62/38 arányát és skálázását követi; logók, számozás, kedvencjel, most/következő, előnézeti és játszott jelölés azonos helyen jelenik meg.
- Kijelölés, előnézet és lejátszásindítás külön fogalom; a közös callbackek és navigációs fixture-ök szerint működnek, a listakattintás nem kap önkényesen eltérő jelentést.
- Közös rendezési, ékezetfüggetlen keresési és kategóriaszabályok; Legutóbbiak nézetben a sikeres lejátszások sorrendje érvényes, legfeljebb tíz egyedi csatornával.
- Kedvenc távirányítóval és pointerrel módosítható, mentése újraindítás után megmarad; frissítés közbeni módosítás nem vész el, eltűnt csatorna nem játszható fantomelem.
- NO_DATA, LOAD_FAILED, NO_SEARCH_RESULTS, NO_FAVORITES, NO_RECENT és NO_CATEGORY_RESULTS a közös szerződés szerinti szöveget és cselekvést adja.
- Kompakt/normál/részletes sorok, logó- és műsoradat-kapcsolók működnek. Virtualizáció mellett listafrissítés, szűrés, pointergörgetés és lista végéig navigálás sem veszti el a logikai fókuszt.

## WOS-18 – Élő TV és lejátszási rétegek paritása

**Story:** Nézőként azonos információs panelt és csatornaválasztási folyamatot szeretnék élő adás közben.

**Prioritás:** P0. **Függőség:** WOS-15, WOS-16. Műsoradatok: WOS-20; gyorsbeállítások: WOS-21.

**Elfogadás:**

- Az élő nézet, információs panel, csatornanév/logó, műsorcím, idő és haladás, valamint az üres állapot a referencia szerint jelenik meg.
- A LiveChannelPreviewState eseménysorai azonosak: előnézet megjelenítése/léptetése nem indít automatikusan másik streamet; megerősítés indít, megszakítás/időtúllépés visszatér.
- Az előnézet és a közvetlen csatornaváltás külön művelet; számbevitel és előző sikeres csatorna a referencia szerint működik.
- Az Élő TV navigációja öt másodperc inaktivitás után a közös szabály szerint tűnik el; információs panel és dialógus saját referencia-időzítését külön teszt igazolja.
- A panel elrejtése nem állítja le az adást, OK/Back nem aktivál háttérben maradt gombot. A TV-n igazolt kép és hang megmarad.

## WOS-19 – Lejátszási állapotok és helyreállás paritása

**Story:** Nézőként azonos visszajelzést és helyreállítási lehetőséget szeretnék streamhibánál.

**Prioritás:** P0. **Függőség:** WOS-14, WOS-15; WOS-18-cal együtt integrálandó.

**Elfogadás:**

- Indítás, lejátszás, pufferelés, leállítás és hiba állapota a közös UI-szerződésbe képeződik, azonos megfogalmazású és helyű visszajelzéssel.
- Gyors váltáskor a régi forrás eseménye, play-promise-a és újrapróbálása nem írhatja felül az új munkamenetet.
- Automatikus újracsatlakozás és próbálkozásszám a közös beállításnak megfelelő; váltás/leállítás megszakítja a régi próbákat. Nincs a beállítást felülíró fix próbálkozásszám.
- Kézi újrapróbálás és navigáció hibaállapotban elérhető; egyszerre egy stream szól. Az előzmény csak igazoltan sikeres, aktuális lejátszásból frissül.
- Böngészőteszt szimulálja az elavult eseményeket és hibákat; fizikai TV-n gyors váltás és hálózatvesztés is ellenőrzött.

## WOS-20 – Közös műsoradat és információs tartalom

**Story:** Nézőként ugyanahhoz a csatornához ugyanazt a műsort és részleteket szeretném látni mindhárom platformon.

**Prioritás:** P0. **Függőség:** WOS-14, meglévő WOS-02 és WOS-05 alap.

**Elfogadás:**

- A hivatalos playlist XMLTV-forrása, csatornapárosítás, időzóna, nyári időszámítás és tvg-shift ugyanazon fixture-re azonos eredményt ad JVM-en és JS-en.
- A most/következő műsor, cím, leírás, kép, idő és progress egységesen táplálja a Csatornák, Élő TV és Műsorújság nézetet; hiányzó adat azonos fallbacket kap.
- A műsorhatár átlépése automatikusan frissíti a látható adatokat. Az EPG hibája nem állítja le a videót.
- Korlátos XML-feldolgozás, DTD/külső entitás tiltás, külön üríthető cache; a nagy adathalmaz nem blokkolja tartósan a távirányítást.

## WOS-21 – Teljes beállítási és nyelvi paritás

**Story:** Nézőként ugyanott szeretném megtalálni és módosítani a Wukki beállításait.

**Prioritás:** P0. **Függőség:** WOS-15, WOS-16, WOS-19. EPG-opciók végső ellenőrzése WOS-20 után.

**Elfogadás:**

- A referencia szerinti Lejátszás, EPG, Megjelenítés, Szülői felügyelet, Playlistek, Nyelv és Névjegy szekciók, sorrendjük, kezdő- és részletnézetük elkészül.
- A referenciában placeholder Szülői felügyelet ugyanazt az állapotot mutatja; nem ígér még nem létező PIN-védelmet.
- Autoplay, újracsatlakozás, próbálkozásszám, frissítési időközök, megjelenítési mód/skála és képkapcsolók ténylegesen hatnak és újraindítás után megmaradnak. A puszta JSON-mező nem kész funkció.
- A gyorsbeállítási dialógus, lenyílók, kapcsolók és léptetők fókusza/Back-kezelése a referencia szerinti.
- Közös magyar/angol szövegforrás és fallback; minden címke, hiba, üres állapot, Névjegy és jogi dokumentum elérhető.
- Hangerő, pufferprofil és képarány támogatását TV-n ellenőrizzük. Nem működő opció nem lehet aktív, megtévesztő vezérlő; eltérés csak WOS-14 jegyzékében indokolt, felhasználó által elfogadott kivétellel zárható le.

## WOS-22 – Indulás, mentés, frissítés és életciklus paritása

**Story:** Nézőként újranyitás, frissítés és háttérbe kerülés után ugyanazt a kiszámítható állapotot szeretném visszakapni.

**Prioritás:** P0. **Függőség:** WOS-17, WOS-19, WOS-20, WOS-21.

**Elfogadás:**

- Az induló nézet és autoplay a közös szabályt követi; utolsó sikeres csatorna, kedvencek, előzmények és beállítások megmaradnak. Átmeneti keresés/navigáció csak a referencia szerint tárolható.
- A 0.5.0-s tárolási séma migrációja tesztelt; sérült vagy nem támogatott állapot nem törli a felhasználói beállításokat egy hálózati alapállapottal. Kvótahiba látható, a jó adat megmarad.
- Cache-ből azonnal böngészhető lista, aszinkron hálózati frissítés; frissítés nem lopja el a keresőfókuszt, nem állítja át a játszott csatornát és nem veszít kedvencmódosítást.
- Kézi és 6/12/24 órás frissítés a közös időzítés szerint; hibás/elavult válasz nem lesz sikeres frissítés.
- Háttérbe kerülés leállítja a hangot és felületi időzítőket; visszatérés és relaunch a referencia folytatási szabályát követi, duplikált lejátszó nélkül.
- TV-n valódi alkalmazásbezárás, újraindítás és hálózat nélküli nyitás is igazolja a tartósságot; offline cache nem jelent offline streamet.

## WOS-23 – Teljes Műsorújság paritása

**Story:** Nézőként ugyanabban a műsorújságban szeretnék műsort keresni és részleteket megnyitni, mint a többi kliensben.

**Prioritás:** P0, az egységes kiadás része. **Függőség:** WOS-15, WOS-16, WOS-20.

**Elfogadás:**

- A közös EpgGuideScreen szerinti csatornaoszlop, idővonal, fejléc, aktuális időjel, programblokkok és részletdialógus jelenik meg azonos fixture-rel.
- Kétirányú fókusz, időablak-váltás, jelen időre ugrás, részletmegnyitás/bezárás és csatornaindítás azonos eseménysorokat követ; jövőbeli műsor nem ígér visszanézést.
- Hosszú cím, hiányzó program, időhatáron átnyúló adás, időzónaváltás és üres EPG a referenciának megfelelően látszik.
- Virtualizált sorok és időablak, stabil fókusz és visszatérési pozíció; teljes playlist/EPG mellett is használható fizikai TV-n.

## WOS-24 – Igazolt háromplatformos paritás és kiadás

**Story:** Nézőként ellenőrzötten egységes, telepíthető Wukki TV-verziót szeretnék kapni.

**Prioritás:** P0, kiadási kapu. **Függőség:** WOS-14–23. Az ellenőrző infrastruktúra WOS-14-től épül.

**Elfogadás:**

- Minden referenciaképernyő azonos fixture-rel és rögzített idővel összehasonlított desktop/Android/webOS képet kap, fókuszált és hibás állapotban is. A geometria, színek, ikonok és szövegek eltérései tételesen ellenőrzöttek; fontsimítási eltérés külön tolerancia, elrendezési eltérés nem rejthető el átfogó pixelküszöbbel.
- A közös input-fixture-ök azonos állapotot és mellékhatást adnak; DOM-tesztek lefedik a kereső bezárását, virtuális ablakváltást, frissítés alatti fókuszt, előnézetet és Back-sorrendet.
- JVM/JS domain-tesztek, webOS böngészőtesztek, statikus elemzés, production build, valódi JS-szintaxisellenőrzés és IPK-csomagolás a CI része. A verifyAll nem helyettesíti a hiányzó felületi teszteket.
- Nincs nyitott funkcionális vagy vizuális paritási eltérés, kivéve a külön dokumentált és felhasználó által elfogadott készülékkorlátot.
- Fizikai TV-n minden hivatalos csatorna, 100 váltás, két óra lejátszás, 20 háttér/előtér ciklus, hálózatvesztés, keresés/szűrés és adatmegőrzés ellenőrzött. Modell, platformverzió és mért válaszidők a jegyzőkönyvben szerepelnek.
- Verziózott IPK, checksum, buildazonosító, változáslista és telepítési leírás készül.

## Sorrend és készültség

1. WOS-14: referencia és eltérésmátrix.
2. WOS-15, WOS-16: arculat, alkalmazáskeret, közös navigáció.
3. WOS-17, WOS-18, WOS-19: csatornaböngésző, élő nézet, megbízható lejátszás. Az EPG- és beállításfüggő elfogadás a következő lépésekig nyitott.
4. WOS-20, WOS-21: műsoradatok, beállítások és lokalizáció; az előző képernyők véglegesítése.
5. WOS-22, WOS-23: tartósság/életciklus és teljes műsorújság.
6. WOS-24: egységes kiadás igazolása.

Minden storyhoz tartozik referenciaállapot, működési és vizuális ellenőrzés. Az implementált story külön commitot kap; a készülékfüggő feltételek igazolásáig „implementálva, TV-s ellenőrzésre vár”. A technikai prototípus kiadható tesztelésre, de „desktop/Android-paritás kész” csak WOS-24 után állítható.

LG Store-publikálás, egyedi források, DRM, felvétel és timeshift továbbra is külön hatókör. A meglévő desktop/Android termékben működő funkció nem minősíthető későbbi bővítésnek pusztán a webOS implementáció egyszerűsítésére.
