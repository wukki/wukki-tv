# Wukki TV webOS – következő user story-k

Állapot: 2026-09-21. Kiindulás: webOS 0.6.0.
Kapcsolódó dokumentum: [megvalósítási terv](webos-implementation-plan.md).
Az alábbi backlog a jelenlegi kód alapján frissíti a korábbi terv készültségi állításait.

## Mi készült el?

- KMP core és Kotlin/JS webOS kliens, telepíthető IPK.
- Natív streamlejátszás: a felhasználó TV-n igazolta a képet és hangot.
- M3U parser, kézi URL-betöltés, alap csatornalista, nyilas navigáció, csatornaváltás és Back.
- JVM/JS parser-tesztek és sikeres `verifyAll` a 0.2.0 commitnál.

A 0.3.0 automatikus playlist-betöltését és közös csatorna-feldolgozását a felhasználó fizikai TV-n sikeresen ellenőrizte.

## Első szakasz – megbízható csatornaböngésző

### WOS-01 – A hivatalos csatornalista automatikus betöltése

**Story:** Nézőként azt szeretném, hogy az alkalmazás megnyitásakor a Wukki csatornáit lássam, hogy ne kelljen URL-t gépelnem.

**Prioritás:** P0. **Állapot:** kész, TV-n ellenőrizve. **Függőség:** nincs.

**Elfogadási feltételek:**
- Első indításkor automatikusan letöltődik a hivatalos M3U, majd kiválasztható a csatorna.
- A playlist URL-je soha nem kerül a videolejátszóba; a diagnosztikai közvetlen stream külön kezelhető.
- Betöltés, üres lista és letöltési hiba külön állapot; hiba után elérhető az újrapróbálás.
- HTTP-hibaválasz nem jelenik meg érvényes playlistként; van időkorlát és 2 MiB playlist-méretkorlát.
- Ismételt aktiválás nem indít párhuzamos letöltéseket. Sikertelen frissítés megőrzi a már betöltött listát.
- A csomagolt IPK-ból, fizikai TV-n is sikeres a betöltés. Adatletöltő JS service csak igazolt hálózati akadály esetén készül külön technikai feladatként.

### WOS-02 – Következetes csatornaadatok minden platformon

**Story:** Nézőként ugyanazokat a csatornaneveket, sorrendet és műsoradat-hozzárendelést szeretném webOS-en, mint a többi Wukki kliensben.

**Prioritás:** P0. **Állapot:** kész, TV-n ellenőrizve. **Függőség:** nincs.

**Elfogadási feltételek:**
- A közös parser kezeli az idézett, vesszőt tartalmazó attribútumokat, hiányzó neveket, duplikátumokat, `tvg-chno` és `tvg-shift` értékeket.
- A fejlécből felismerhető az EPG URL: `url-tvg`, `x-tvg-url`, `tvg-url`.
- HLS média- és master manifest nem válik IPTV csatornalistává; a relatív stream-URL a playlist tényleges forrásához képest oldódik fel.
- A JVM és JS azonos fixture-ből azonos csatornaadatokat és azonosítókat állít elő. A jelenlegi JVM UUID-algoritmus megmarad; a webOS FNV-azonosítóiról szükség esetén migráció készül.
- Egy közös feldolgozási szerződés váltja fel a két eltérően fejlődő parsert; a meglévő Android/desktop tesztek továbbra is sikeresek.

### WOS-03 – Biztos csatornaváltás és hibából helyreállás

**Story:** Nézőként gyorsan szeretnék csatornát váltani, és átmeneti hálózati hiba után folytatni az adást anélkül, hogy az alkalmazást újraindítanám.

**Prioritás:** P0. **Függőség:** WOS-01.

**Elfogadási feltételek:**
- Elkülönül az indítás, lejátszás, pufferelés, leállítás és hiba állapota.
- Gyors váltáskor a korábbi stream eseménye vagy visszautasított `play()` ígérete nem ronthatja el az új csatorna állapotát.
- Átmeneti hibánál legfeljebb három automatikus újrapróbálás történik növekvő várakozással; váltás és leállítás megszakítja a korábbi próbálkozásokat.
- Sikertelenségnél érthető hiba és kézi újrapróbálás jelenik meg, a lista elérhető marad.
- Egyszerre egy stream szól; a meglévő, TV-n működő videomegjelenítés nem romlik el.

### WOS-04 – Kényelmes távirányítás és eltűnő lejátszófelület

**Story:** Nézőként kizárólag távirányítóval szeretném kezelni az alkalmazást, miközben a vezérlők nem takarják tartósan a műsort.

**Prioritás:** P0. **Állapot:** implementálva, TV-s ellenőrzésre vár. **Függőség:** WOS-03.

**Elfogadási feltételek:**
- A lista nyilakkal bejárható, OK indít, a fókusz mindig látható és visszatéréskor az utoljára választott csatornára áll.
- Rejtett és letiltott elemek nem kapnak fókuszt; hosszú listában a kijelölt sor a látható területre görgetődik.
- Lejátszáskor a HUD 5 másodperc inaktivitás után eltűnik; OK újra megjeleníti, és nem aktivál háttérben maradt gombot.
- Back először a nyitott réteget zárja, majd visszavisz a listához; platformkilépés csak gyökérnézetből történik.
- A Fel/Le csatornaváltás és a Magic Remote kattintás működik; hosszan nyomott gomb sem okoz beragadt fókuszt vagy vezérlési hibát.

## Második szakasz – napi használat

### WOS-05 – Állapot megőrzése újraindítás után

**Story:** Nézőként újranyitáskor szeretném visszakapni a csatornalistámat és beállításaimat, hogy ne kelljen minden alkalommal elölről kezdenem.

**Prioritás:** P1. **Állapot:** implementálva, TV-s ellenőrzésre vár. **Függőség:** WOS-01, WOS-02.

**Elfogadási feltételek:**
- Verziózott helyi tároló őrzi a playlist-cache-t, beállításokat és az utolsó sikeresen játszott csatornát.
- Induláskor a cache azonnal böngészhető, a hálózati frissítés nem blokkolja a felületet. Offline állapotban a cache nem jelent offline videolejátszást.
- Csak sikeres lejátszás kerül a legutóbbi tíz csatorna előzményeibe.
- Sérült vagy megtelt tároló nem írja felül üres adatokkal a meglévő felhasználói állapotot; a hiba látható.
- Az adatok tényleges TV-s alkalmazásbezárás és újraindítás után is megmaradnak.

### WOS-06 – Keresés és kategóriák

**Story:** Nézőként név és kategória szerint szeretnék csatornát keresni, hogy egy hosszú listában is gyorsan megtaláljam a műsort.

**Prioritás:** P1. **Állapot:** implementálva, TV-s ellenőrzésre vár. **Függőség:** WOS-02, WOS-04.

**Elfogadási feltételek:**
- A névkeresés kis-/nagybetűtől és ékezettől független, kategóriaszűréssel együtt is működik.
- A sorrend csatornaszám, majd normalizált név szerinti; az üres találat egyértelmű és a szűrés törölhető.
- Szűrés, törlés és listafrissítés után a fókusz érvényes elemre kerül.
- Nagy listánál csak a szükséges sorok renderelődnek; a virtualizálás nem veszíti el a fókuszt.

### WOS-07 – Kedvencek és legutóbbi csatornák

**Story:** Nézőként külön listából szeretném elérni a kedvenceimet és a nemrég nézett csatornákat.

**Prioritás:** P1. **Függőség:** WOS-02, WOS-04, WOS-05.

**Elfogadási feltételek:**
- Kedvenc hozzáadása és eltávolítása távirányítóval és pointerrel is elérhető.
- Van Kedvencek és Legutóbbiak nézet, értelmezhető üres állapottal.
- A kedvencek újraindítás és playlist-frissítés után megmaradnak; eltűnt csatorna nem marad lejátszható fantomelemként.
- A frissítés közben végzett kedvencmódosítás sem veszhet el.

### WOS-08 – Helyes működés háttérbe kerüléskor

**Story:** Nézőként másik TV-alkalmazásra váltáskor leálló hangot, visszatéréskor kiszámítható folytatást szeretnék.

**Prioritás:** P1. **Függőség:** WOS-03, WOS-05.

**Elfogadási feltételek:**
- Háttérben a lejátszás és a felületi időzítők leállnak; nincs háttérhang.
- Visszatéréskor a korábbi csatorna és fókusz helyreáll, a folytatás az automatikus lejátszás beállítását követi.
- Relaunch nem hoz létre második lejátszót vagy duplikált eseménykezelőt.
- Hálózatvesztés és visszatérés közben is elérhető marad a lista és a kilépés.

## Harmadik szakasz – műsorinformáció és beállítások

### WOS-09 – Most és következő műsor

**Story:** Nézőként látni szeretném, mi megy most és mi következik a kiválasztott csatornán.

**Prioritás:** P1. **Függőség:** WOS-02, WOS-05.

**Elfogadási feltételek:**
- A hivatalos playlistben megadott XMLTV-forrásból betöltődik a releváns műsoradat, a meglévő közös EPG-párosítással.
- Helyes az időzóna, nyári időszámítás és `tvg-shift` kezelése; a műsorváltás frissíti a kijelzést.
- A listában és a lejátszó HUD-ján megjelenik a most/következő cím és időpont; a jelenlegi műsorhoz haladásjelző tartozik.
- Hiányzó, elavult vagy hibás EPG egyértelmű jelzést kap, és nem akadályozza a videót.
- A feldolgozás nem blokkolja a távirányítást; méretkorlátos, DTD/külső entitás nélküli feldolgozás és külön üríthető EPG-cache készül.

### WOS-10 – Kézi és időzített adatfrissítés

**Story:** Nézőként friss csatornalistát és műsoradatokat szeretnék kézi újraindítás nélkül.

**Prioritás:** P1. **Függőség:** WOS-05, WOS-08, WOS-09.

**Elfogadási feltételek:**
- Választható kézi, 6, 12 és 24 órás frissítés; az esedékesség az utolsó sikeres frissítéstől számít.
- Előtérben és visszatéréskor az esedékes frissítés elindul, egyszerre legfeljebb egy frissítési folyamat fut.
- Hibás, félbeszakadt vagy elavult válasz nem cserél le jó cache-t, és nem módosítja a sikeres frissítés időpontját.
- A lejátszás, fókusz és kedvencek megmaradnak; kikapcsolt TV melletti frissítés nem része az ígéretnek.

### WOS-11 – Nyelv és lejátszási beállítások

**Story:** Nézőként magyarul vagy angolul szeretném használni az alkalmazást, és beállítani az indulás és lejátszás viselkedését.

**Prioritás:** P1. **Függőség:** WOS-04, WOS-05, WOS-08.

**Elfogadási feltételek:**
- A feliratok, hibák és üres állapotok magyarul és angolul is elérhetők.
- Az automatikus lejátszás és újracsatlakozás kikapcsolható; a beállítások újraindítás után megmaradnak.
- Képaránymód csak akkor választható, ha fizikai TV-n igazolt és nem okoz fekete képet.
- A technikai diagnosztika külön nézetbe kerül; a Névjegy mutatja az app verzióját és buildazonosítóját.

## Negyedik szakasz – ellenőrzött kiadás és bővítés

### WOS-12 – Megismételhető, ellenőrzött telepítőkiadás

**Story:** Tesztelőként egyértelműen azonosítható, ellenőrzött csomagot szeretnék telepíteni és hiba esetén visszajelzést adni róla.

**Prioritás:** P1, kiadási kapu. **Függőség:** WOS-01–11; a CI és a tesztjegyzőkönyv előbb is elkészíthető.

**Elfogadási feltételek:**
- A CI közös JVM/JS tesztet, felületi navigációs tesztet, statikus elemzést, production buildet és IPK-csomagolást futtat.
- A JavaScript kompatibilitását valódi szintaktikai elemzés ellenőrzi; regexben vagy stringben szereplő `?.` nem okoz téves hibát.
- A csomag mellé checksum, buildazonosító, változáslista és telepítési leírás készül.
- Jegyzőkönyv rögzíti a TV modelljét és platformverzióját, valamint minden aktuális hivatalos csatorna eredményét.
- Sikeres a 100 csatornaváltás, 2 óra lejátszás, 20 háttér/előtér ciklus, hálózatvesztés és újraindítás utáni adatmegőrzés próbája; nincs összeomlás, beragadt fókusz vagy háttérhang.
- A korábbi terv teljesítménycéljait méréssel ellenőrizzük, az eltéréseket dokumentáljuk. A build sikere nem helyettesíti a TV-s próbát.

### WOS-13 – Teljes műsorújság

**Story:** Nézőként több csatorna következő műsorait szeretném egy idővonalon áttekinteni és onnan adásra váltani.

**Prioritás:** P2, az első stabil kiadás után. **Függőség:** WOS-04, WOS-09, WOS-12.

**Elfogadási feltételek:**
- Az időarányos rács csatornák és időpontok között kétirányban navigálható.
- Jelen idejű műsor kiválasztása az élő csatornát indítja; jövőbeli műsor részleteket mutat, nem ígér visszanézést vagy felvételt.
- Van ugrás a jelen időpontra és műsorrészlet-nézet; visszalépéskor megmarad a fókusz.
- Az időablak és a sorok virtualizáltak; nagy EPG mellett is megfelel a minimum TV-n mért válaszidőcéloknak.

## Javasolt sorrend és közös készültségi feltétel

1. Következő implementáció: **WOS-03**, majd a WOS-04–06 fizikai TV-s ellenőrzése.
2. Napi használhatóság: **WOS-05–08**.
3. EPG és beállítások: **WOS-09–11**.
4. Stabil kiadás: **WOS-12**; későbbi bővítés: **WOS-13**.

Minden story akkor kész, ha az elfogadási feltételei teljesülnek, a releváns automatizált ellenőrzések sikeresek, és a változás bekerül egy azonosítható IPK-ba. A készülékfüggő feltételekhez dokumentált fizikai TV-s próba szükséges; addig a story állapota „implementálva, TV-s ellenőrzésre vár”.

LG Store-kiadás, egyedi playlistforrások, médiaproxy/transzkódolás, DRM, timeshift és felvétel külön hatókör, nem része ennek a backlognak.
