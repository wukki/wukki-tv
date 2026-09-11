# PR 3 – Application/domain réteg szétválasztása

## Hatókör és döntések

- Kiindulás: `f070fd8`, tiszta munkakönyvtár; a PR 2 teljes verifyAll ellenőrzése sikeres.
- Közös, Compose-független ApplicationStore és Channel/Epg/Settings repository interfészek.
- Playlist/EPG-frissítés, beállításmódosítás, csatornaválasztás és kedvencművelet külön alkalmazási műveletekben.
- Injektálható Clock és DispatcherProvider; típusos alkalmazási hibák és presentation oldali fordítás.
- WukkiModel kompatibilis UI-façade marad: nincs domainmodell-, JSON-, UI- vagy navigációváltozás.
- A WorkManager közvetlen use case hívása PR 4, streaming/indexoptimalizálás PR 5, UI-életciklus PR 6: nem része ennek a commitnak.
- Az alkalmazásállapot mutációi a meglévő tulajdonos dispatcherén történnek; a blokkoló hálózat és parser injektált háttér-dispatchert használ.

## Állapot és következő lépés

1. **Kész:** korábbi audit PR 3 tervének visszakeresése, modell/adattároló/frissítési tesztek feltérképezése.
2. **Kész:** repositoryk, alkalmazási műveletek, vékonyabb WukkiModel, bootstrap és platformos IO-dispatcher bekötése. Típusos HTTP/URL/méretkorlát hibák, HU/EN presentation-leképezés.
3. **Kész:** determinisztikus óra/dispatcher-, repository-, hibakezelési, cancellation- és headless use case tesztek; lokális HTTP-szerveres hibateszt.
4. **Kész:** célzott formázás, teljes verifyAll, diff-ellenőrzés és architektúradokumentáció. Az alkalmazási réteg nem importál Compose- vagy platformos API-t.
5. **Lezárás:** az ellenőrzött PR 3 módosítások commitálása. Push nem része a feladatnak. PR 3-hoz további implementáció nincs hátra; PR 4 csak külön kérésre indul.

## Ellenőrzések

- Első regressziós kör sikeres (24 s): shared desktop/Android host tesztek, desktop tesztek/fordítás, Android unit tesztek és debug APK. Ez még az új PR 3-specifikus tesztek előtti kör.
- A meglévő, letöltés alatti kedvenc-/csatornamódosítást vizsgáló RefreshOwnershipTest is sikeres.
- A végleges `./gradlew verifyAll --no-daemon --continue` sikeres (19 s): shared desktop/Android host tesztek, desktop teszt és fordítás, Android unit tesztek és debug APK, lokalizáció, ktlint és detekt.
- A hét új ApplicationUseCasesTest és két RemoteFailureTest mindkét JVM-tesztcélon sikeres: nulla hiba, nulla kihagyott teszt.
- A detekt által jelzett összetettséget közös parser-hibakezelő segédfüggvény kiemelése oldotta meg; ellenőrzési szabályt vagy baseline-t nem lazítottunk.
- A folytatáskor ellenőrzött munkakönyvtár és tesztriportok megfeleltek a rögzített állapotnak; nem ismételtük meg a sikeresen befejezett buildet.
- `git diff --check` sikeres. Kézi desktop/Android eszközteszt nem történt ebben a munkafázisban.

## Megőrzendő viselkedés

- Letöltés alatt végzett kedvenc-, beállítás- és utolsócsatorna-módosítás nem veszhet el.
- Hiba megőrzi a cache-t; új vagy hiányzó EPG URL nem használ idegen cache-t.
- A kézi frissítés visszajelzést ad, automatikus siker néma; cancellation továbbterjed.
- Kijelölés nem írja át az utolsó sikeresen lejátszott csatornát.
- PR 2 aszinkron indulás/mentés és régi állapotmigráció változatlanul működik.
