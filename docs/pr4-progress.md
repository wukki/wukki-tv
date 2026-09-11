# PR 4 – Headless háttérfrissítés

## Hatókör

- Kiindulás: `ae05d29`, tiszta munkakönyvtár és sikeres PR 3 `verifyAll`.
- A WorkManager közvetlenül a Compose-független `WukkiApplication` műveleteit hívja.
- A UI és a worker ugyanazt a process-szintű alkalmazásállapotot, frissítési koordinátort és mentési sort használja.
- Playlist és EPG csak a frissítési konfiguráció vagy az esedékességet meghatározó időbélyeg változásakor ütemeződik újra.
- Átmeneti hálózati hibák exponenciális backoff-fal újrapróbálhatók; végleges adat- és konfigurációhibák nem indítanak szoros retry-ciklust.
- A WorkManager egyedi nevei, DataStore-, cache- és mentett állapotformátumok változatlanok maradnak.

## Állapot és következő lépés

1. **Kész:** a jelenlegi worker, scheduler, bootstrap és PR 3 alkalmazási réteg feltérképezése.
2. **Kész:** headless application bootstrap, közvetlen worker-use-case hívás és típusos worker eredmény.
3. **Kész:** stabil playlist/EPG ütemezési kulcsok, esedékességhez igazított kezdeti késleltetés, retry/backoff szabályok és célzott tesztek.
4. **Kész:** teljes `verifyAll`, diff-ellenőrzés és dokumentáció.
5. **Lezárás:** az ellenőrzött PR 4 módosítások commitálása. Push nem része a feladatnak; a következő architekturális fázis csak külön kérésre indul.

## Eddigi ellenőrzés

- Shared desktop és Android host tesztek sikeresek.
- Android unit tesztek, desktop fordítás és Android debug APK sikeres (26 s).
- Két új headless bootstrap teszt mindkét shared JVM-célon, öt új ütemezési/retry teszt Androidon hibamentes.
- Kézi process-death eszközteszt még nem történt; az automatizált réteg a worker-only újrainicializálást ellenőrzi.
- A végleges `./gradlew verifyAll --no-daemon --continue` sikeres (22 s, 118 feladat): Detekt és ktlint 0 találat, lokalizáció, minden shared/desktop/Android teszt, desktop fordítás és Android debug APK rendben.
- A worker forrása nem hivatkozik `WukkiModel`-re vagy más presentation API-ra; a processz-szintű application runtime közvetlenül futtatja a use case-eket.
- `git diff --check` sikeres. Kézi Android process-death/WorkManager eszközteszt ebben a munkafázisban nem történt.

## Kompatibilitási megjegyzés

A foreground Compose időzítők ebben a PR-ben megmaradnak. A közös `RefreshCoordinator` miatt a workerrel átfedő kérés nem indít párhuzamos letöltést. A streaming EPG és indexoptimalizálás a külön PR 5 része.
