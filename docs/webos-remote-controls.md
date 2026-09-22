# webOS távirányító-kiosztás

A webOS kliens ugyanazt a `AppRemoteState` állapotgépet használja, mint a desktop és Android kliens. A DOM-adapter csak az LG billentyűkódokat fordítja közös eseményre és végrehajtja a reducer mellékhatásait.

| Művelet | Magic Remote / D-pad | Látható vezérlő |
| --- | --- | --- |
| Fókusz mozgatása | Bal, Jobb, Fel, Le | pointeres rámutatás |
| Megerősítés | OK | kattintás |
| Vissza | Back (`461`) | Bezárás, ahol réteg nyílt meg |
| Élő előnézet | Fel / Le | információs panel |
| Közvetlen csatornaváltás | Page Up / Page Down kompatibilis billentyűzeten | Csatorna + / Csatorna − |
| Előző csatorna | piros gomb (`403`) | Előző csatorna |
| Gyorsbeállítások | zöld gomb (`404`) | Gyorsbeállítások |
| Csatornaszám | `0`–`9`, ha a készülék átadja az alkalmazásnak | csatornalista és keresés |
| Leállítás | – | Leállítás |

Az LG hivatalos webOS TV dokumentációja az iránygombokat, OK-t, Backet és az újabb Magic Remote színgombjait adja át webalkalmazásoknak. A Channel +/−, Guide és számbillentyűk elérhetősége modellenként eltérhet, illetve egyes rendszereken a TV kezeli őket. Emiatt minden szükséges művelet látható, fókuszolható vezérlővel is elérhető.

Források:

- [LG webOS TV – Magic Remote](https://webostv.developer.lge.com/develop/guides/magic-remote)
- [LG webOS TV – Back Button](https://webostv.developer.lge.com/develop/guides/back-button)
- [LG webOS TV – appinfo.json](https://webostv.developer.lge.com/develop/references/appinfo-json)

Hosszan nyomott iránygombnál az ismételt események folytatják a fókuszmozgatást. Az ismételt OK eseményt az adapter eldobja, ezért egy nyomás nem indíthat kétszer lejátszást vagy műveletet. Szövegmezőben a Bal/Jobb és Backspace a kurzoré marad; Escape vagy a TV Back gombja zárja a keresési állapotot.
