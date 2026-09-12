# PR 7 – Build- és release-hardening

## Elkészült

- Minden közvetlen plugin- és könyvtárverzió a Gradle version catalogban található.
- A minőségellenőrzés és a dependency locking convention pluginekkel egységesített.
- A `shared` és `androidApp` feloldható konfigurációi szigorú lockfile-t használnak.
- A GitHub Actions hivatkozások változtathatatlan commit SHA-kra vannak rögzítve.
- A macOS és Windows VLC-letöltés SHA-256 ellenőrzött; a Linux runtime fájlmanifestet kap.
- A release CycloneDX SBOM-ot készít, majd GitHub artifact attestationnel igazolja az SBOM-ot és a build provenance-t.
- A közös `verifyAll` ellenőrzi a lockfile-okat, a dependency verification metaadatot és az Action SHA-pineket.
- A teljes `verifyAll`, a szigorú dependency verification és a runtimeokra szűkített CycloneDX SBOM-generálás helyben sikeresen lefutott.

## Következő lépés

A dependency-verziók módosításakor a lockfile-okat és az ellenőrzőösszegeket tudatosan frissíteni, majd a teljes `verifyAll` kaput lefuttatni. A release workflow első éles futásán ellenőrizni kell a platformos csomagokat és a GitHub Release Attestations nézetét.
