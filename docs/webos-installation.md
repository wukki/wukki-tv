# Wukki TV telepítése LG webOS TV-re

## Előfeltételek

1. Telepítsd a TV-re az LG Developer Mode alkalmazást, jelentkezz be fejlesztői fiókkal, kapcsold be a Developer Mode és Key Server kapcsolókat.
2. A számítógépen telepítsd a rögzített CLI-verziót: `npm install --global @webos-tools/cli@3.2.6`.
3. Add hozzá a TV-t: `ares-setup-device`. Az alapértelmezett SSH-felhasználó `prisoner`; az IP-címet a TV hálózati beállításaiból olvasd ki.
4. Kérd le a kulcsot: `ares-novacom --device <eszköznév> --getkey`, majd írd be a Developer Mode alkalmazásban látható jelszót.

## Telepítés és indítás

A kiadási könyvtárban előbb ellenőrizd az IPK-t:

```sh
shasum -a 256 --check SHA256SUMS.txt
ares-package -i hu.wukki.tv.webos_*.ipk
```

Ezután telepítsd és indítsd el:

```sh
ares-install --device <eszköznév> hu.wukki.tv.webos_*.ipk
ares-launch --device <eszköznév> hu.wukki.tv.webos
```

A futási naplóhoz használd az `ares-inspect --device <eszköznév> --app hu.wukki.tv.webos --open` parancsot. Eltávolítás: `ares-install --device <eszköznév> --remove hu.wukki.tv.webos`.

Az EPG-t a csomagolt `hu.wukki.tv.webos.epg` service tölti le, ezért telepített TV-alkalmazásnál nem kell és nem használható a fejlesztői `127.0.0.1:4173` proxy.
