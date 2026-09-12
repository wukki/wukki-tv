# PR 6 – UI selectorok és életciklus

## Hatókör

- Kiindulás: `b6eba88`, tiszta munkakönyvtár és sikeres PR 5 `verifyAll`.
- A presentation modell külön, megfigyelhető állapotszeleteket publikál a teljes `AppState` helyett.
- A dashboard csak az aktív feature-höz szükséges UI-állapotot és EPG-adatot állítja elő.
- A stabil vezérlő- és callback-példányok csökkentik a szükségtelen Compose újrarajzolást.
- Az órajelhez és timeoutokhoz kötött UI-munka csak aktív előtéri UI mellett fut.
- Androidon a WorkManager marad az automatikus háttérfrissítés egyetlen időzítője; a Compose-időzítő csak desktopon marad.

## Állapot és következő lépés

1. **Kész:** a teljes AppState-megfigyelés, dashboard selectorok, időzítők és platform-életciklus feltérképezése.
2. **Kész:** megfigyelhető modellállapot szeletelése, memoizált csatornaazonosító-index és immutable feature UI-state-ek.
3. **Kész:** stabil session controller/callbackek, aktív UI-hoz kötött időzítők és az Android foreground-frissítés duplikációjának megszüntetése.
4. **Kész:** selector- és életciklus-policy tesztek mindkét shared JVM-célon, célzott platformfordítás és dokumentáció.
5. **Kész:** teljes `verifyAll` és végső diff-ellenőrzés. Következő lépés az ellenőrzött PR 6 commitálása.

## Kompatibilitási korlátok

A mentett állapot, a navigáció, a frissítési intervallumok, a lejátszás és a megjelenés nem változik. A `WukkiModel.state` kompatibilitási olvasata megmarad, de nem lesz Compose invalidációs forrás.

## Ellenőrzési eredmény

- A settings-only módosítás nem építi újra a csatornadirectoryt és nem változtatja az EPG content verzióját.
- A csatorna- és EPG-snapshot cseréje csak a hozzá tartozó selectorokat invalidálja.
- Háttérben az Android UI órája, timeoutjai és Compose-frissítési ciklusa nem fut.
- A végleges `./gradlew verifyAll --no-daemon --continue` sikeres (27 s, 118 feladat): Detekt és ktlint 0 találat, lokalizáció, shared desktop/Android host tesztek, desktop tesztek és fordítás, Android unit tesztek és debug APK rendben.
