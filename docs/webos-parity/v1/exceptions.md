# Platformeltérések – vizsgálandó, nem elfogadott kivételek

Nincs elfogadott hardverkivétel. A „nem támogatott” állapot csak mérés és termékdöntés után zárható le; a jelenlegi webOS hiányossága nem indok.

| ID | Kérdés / jelenlegi bizonyíték | Ellenőrzés és felelős story | Állapot |
| --- | --- | --- | --- |
| X01 | HTML video hangerő/pufferprofil/képarány tényleges hatása LG-n ismeretlen | Beállítás előtte/utána, hang/kép és mentés; WOS-21 | Vizsgálandó |
| X02 | Hangsáv/felirat native HLS képességek streamfüggők | Két hangsávos/feliratos fixture, nincs megtévesztő aktív opció; WOS-21 | Vizsgálandó |
| X03 | Magic Remote és LG képernyőbillentyűzet fókuszútja | Mutató→D-pad→input→Back, dupla aktiválás nélkül; WOS-16 | Vizsgálandó |
| X04 | TV CSS fontmetrika és overscan | 1080p referencia, hosszú szöveg, fókuszkeret; WOS-15/24 | Vizsgálandó |
| X05 | Háttér, suspend, relaunch nem azonos egy böngészőtabbal | Nincs rejtett hang; autoplay és cache a referencia szerint; WOS-22 | Vizsgálandó |
| X06 | Médiafelület és overlay rétegsorrend | Valódi képpel/hanggal, dialógus és információpanel; WOS-18/19 | Vizsgálandó |
| X07 | Chromium68 teljes EPG memória/teljesítmény | Nagy fixture, mért inputlatencia, stabil fókusz; WOS-23/24 | Vizsgálandó |

Lezárási mezők: TV-modell/platformverzió, reprodukció, mért eltérés, lehetséges megoldások, választott fallback, elfogadás dátuma és hivatkozása. Egyik sincs kitöltve helyettesítő feltételezéssel.
