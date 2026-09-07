# Network policy

The application text HTTP client is used only for the fixed official playlist and the single EPG URL declared by that playlist. Both current endpoints use HTTPS:

- `raw.githubusercontent.com` — official playlist
- `kizman.net` — XMLTV EPG declared in the playlist

Each request has 15-second connection and 30-second read timeouts, sends a versioned `WukkiTV` User-Agent, and is read through a bounded stream. Playlists are limited to 2 MiB. EPG responses are limited to 32 MiB in both compressed and decoded form. XMLTV reaches the SAX parser only after this limit has been enforced. Redirects remain subject to the JVM HTTP implementation's normal HTTPS validation.

Android denies cleartext traffic by default. The only declared exception is `88.212.15.19`, because the official playlist currently points its legacy live streams at that HTTP host. Media3 handles those streams directly; the playlist/EPG text client does not fetch video. Adding another cleartext stream host requires an explicit reviewed entry in `network_security_config.xml`. Playlist, EPG, artwork, and all other application traffic remain HTTPS-only unless such a narrow exception is added.
