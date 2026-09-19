# UX-09 hibajavítás és ellenőrzés

## Hibák és javítások

- A teljes `verifyAll` ellenőrzést Kotlin-formázási hibák és a
  `SettingsScreen.kt` wildcard importjai akadályozták. Az érintett hat fájl
  formázása és az explicit importok javítva.
- Androidon a 4:3-as lejátszónézet mérete helyes volt, de a natív
  `SurfaceView` videótartalma túlnyúlt a Compose által kijelölt területen.
  A `PlayerView` most a saját téglalap alakú körvonalához vágja a tartalmát
  (`clipToOutline`, `ViewOutlineProvider.BOUNDS`).

## Elvégzett ellenőrzések

- `./gradlew verifyAll`: sikeres, 120 feladat (9 futott, 111 naprakész).
- `git diff --check`: sikeres.
- Android TV `Television_1080p` emulátor, API 36, 1920×1080,
  frissen telepített debug APK, meglévő lejátszási adatok megtartásával.
- Élő adás közben a 4:3-as videó két oldalán megfelelő fekete sáv jelent meg;
  a videó nem rajzolódott a kijelölt területen kívülre.
- 21:9 esetén megfelelő felső és alsó fekete sáv jelent meg.
- A MENU megnyitotta a gyorspanelt, a BACK bezárta, a lejátszás folytatódott.
- Csatornaváltás után a képarány ismét Automatikus lett.
- Élő nézetből OK → JOBBRA → JOBBRA után a gyorspanel 16:9-et jelzett.
- A felirat kiválasztható volt (Sáv 2), majd ismét kikapcsolható.
- Az egyetlen hangsávval rendelkező adásnál nem jelent meg hangsávválasztó.
- Az emulátor crash naplója az ellenőrzés végén üres volt.

## Lefedettség korlátai

A felhasználó kérésére fizikai eszköz helyett emulátoron történt a próba.
Több hangsáv közötti natív váltás és a kiválasztott felirat tényleges
szöveges megjelenése ezzel az élő adással nem lett igazolva. A JVM-tesztek
nem helyettesítik a natív SurfaceView vágásának vizuális ellenőrzését.
