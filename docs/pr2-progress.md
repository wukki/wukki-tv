# PR 2 – Aszinkron bootstrap és megbízható mentés

## Cél és döntések

- Az AppStateStore betöltése és lemezre írása suspend művelet.
- Közös, legfeljebb egy függő állapotot tartó writer vonja össze a gyors UI-módosításokat (200 ms).
- A flush megvárja a tényleges mentést, hibánál kivételt ad; a UI lokalizált hibát és újrapróbálást kínál.
- Az Android process-szintű modell és a worker ugyanazt az aszinkron inicializálást használja.
- A desktop külön, tömörített EPG-cache-t kap; a régi JSON és state.bin betölthető marad.
- Sérült felhasználói állapotból nem indul automatikus üres állapotmentés.
- A sikeres verifyAll és végső diff-ellenőrzés után automatikus commit készül.

## Fázisok

1. **Kész:** aktuális repository és hívási helyek feltérképezése. Kiinduló commit: 438e410; tiszta munkakönyvtár.
2. **Kész, tesztelt:** suspend tárolóhatár, összevont writer és közös bootstrap.
3. **Kész, tesztelt:** Android/desktop adapterek, betöltési/mentési hiba UI, Android onStop flush és desktop bezáráskori flush, külön desktop EPG-cache és régi állapot olvasása.
4. **Kész, tesztelt:** közös writer/bootstrap tesztek (10 000 módosítás, lassú írás, párhuzamos flush, hiba/újrapróbálás, megszakítás és közös inicializálás). Android DataStore- és desktop migráció/cache/hiba tesztek is sikeresek.
5. **Kész:** teljes verifyAll, végső diff-ellenőrzés és dokumentáció. Következő lépés: az ellenőrzött PR 2 commitálása; a commit a jelen naplót is tartalmazza.

## Ellenőrzések

- Az első körben sikeres: shared desktop tesztek, desktop tesztek/fordítás, Android unit tesztek és debug APK build (21 s).
- A kibővített tárolótesztek sikeresek: 5 desktop tároló-, 3 Android DataStore-, 4 közös writer- és 2 bootstrap-teszt; a meglévő migrációs tesztek is sikeresek.
- A végleges `./gradlew verifyAll --no-daemon --continue` sikeres (27 s): build-logika, detekt, ktlint, shared desktop/Android host tesztek, desktop tesztek és fordítás, Android unit tesztek/debug APK, lokalizáció.
- `git diff --check`: sikeres. Baseline nem módosult. A ktlint a Compose-konvenció szerinti, `@Composable`-lel jelölt függvények nagybetűs nevét engedi; a többi névellenőrzés megmarad. Nem kapcsolódó formázási változás nem maradt a diffben.
- README: aszinkron betöltés, mentés, cache-migráció és életciklus-korlátok dokumentálva.
- A munkafázisok végén ez a fájl frissül; folytatás előtt a git státusz és diff az elsődleges ellenőrzés.

## Átadás és korlátok

- Az automatikus ellenőrzés kész; ezen munkafázisban nem történt fizikai eszközös vagy kézi emulátoros UI-/streamteszt.
- A betöltési képernyő a mentett nyelv elérhetőségéig az alapértelmezett magyart használja. A futás közbeni mentési hiba a kiválasztott nyelven jelenik meg.
- Az Android onStop mentése best-effort: a rendszer folyamatleállítása vagy kényszerbezárás nem garantálja a még függő írás befejezését.
- Sikeres commit után ellenőrizendő a tiszta munkakönyvtár. Push nem része ennek a feladatnak.
