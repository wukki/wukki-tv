# PR 5 – EPG streaming és indexelés

## Hatókör

- Kiindulás: `ecc3ed1`, tiszta munkakönyvtár és sikeres PR 4 `verifyAll`.
- Az XMLTV nem készül el teljes `String` értékként: a korlátozott, gzipet felismerő hálózati bájtfolyam közvetlenül a SAX parserbe kerül.
- A playlist kis szöveges adatforrásként továbbra is a kompatibilis `RemoteTextLoader` útvonalat használja.
- Az EPG-cache indexelése külön komponensbe kerül, az aktuális/következő/időtartomány lekérdezések bináris keresést használnak.
- A mentett JSON, DataStore, tömörített cache, domainmodellek és felületi működés nem változik.

## Állapot és következő lépés

1. **Kész:** jelenlegi hálózati, parser-, repository-, cache- és indexútvonal feltérképezése.
2. **Kész:** streaming tartalombetöltő és SAX parser bekötése kompatibilis fallbackkel, egyértelmű caller-owned erőforrás-élettartammal.
3. **Kész:** cache/index felelősség szétválasztása, bináris aktuális/következő/időtartomány lekérdezések és előre számított végidők.
4. **Kész:** memóriahatár-, gzip-, XXE-, regressziós és többnapos indexterhelési tesztek, célzott desktop/Android ellenőrzés és dokumentáció.
5. **Kész:** teljes `verifyAll` és végső diff-ellenőrzés. Következő lépés az ellenőrzött PR 5 commitálása.

## Rögzített korlátok

A kibontott XMLTV adatfolyam 32 MiB-os korlátja megmarad. Hiba és megszakítás nem cserélheti le a korábbi EPG-cache-t. A következő UI-optimalizálási fázis nem része ennek a PR-nek.

## Ellenőrzési eredmény

- A streaming útvonal mind tömörített, mind sima XMLTV-t közvetlenül a SAX parserbe továbbít.
- A nyers és kibontott adatméret is korlátozott; a DOCTYPE és külső entitások tiltottak.
- A hálózati olvasási hibák megőrzik típusos, újrapróbálható hibájukat akkor is, ha a SAX parser becsomagolja őket.
- Az index ugyanazt az immutable EPG snapshotot használja minden lekérdezéshez, és csak snapshotváltáskor épül újra.
- A végleges `./gradlew verifyAll --no-daemon --continue` sikeres (10 s, 118 feladat): Detekt és ktlint 0 találat, lokalizáció, shared desktop/Android host tesztek, desktop tesztek és fordítás, Android unit tesztek és debug APK rendben.
