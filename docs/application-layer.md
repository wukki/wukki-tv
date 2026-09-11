# Application/domain boundary (PR 3)

`WukkiApplication` assembles the Compose-independent application layer. `WukkiModel` is its
compatibility presentation façade: it retains observable UI state, directory filters, feedback
tokens, channel selection and playback requests. Existing screens and callers keep their API.

## State and repositories

- `ApplicationStore` owns the current immutable `AppState`, exposes a read-only `StateFlow` and
  forwards changes to PR 2's `StateWriter`. Its synchronous observer bridges the existing Compose
  façade; there is no Compose dependency in the application classes.
- `ChannelRepository` owns channel replacement, favourite updates and the last successful channel.
  Refresh completion merges against the **current** state, not the snapshot before download.
- `EpgRepository` owns source synchronization, cache publication, matching and access to the existing
  `ProgrammeIndex`. A response for a replaced source ID/URL is rejected.
- `SettingsRepository` updates settings and the compatibility `autoRefreshHours` field together.
- All repositories share the same store. Mutations/observers remain confined to the application's
  owner dispatcher (currently Main). The exposed StateFlow is not permission to mutate repositories
  from arbitrary threads. Network and parsing work run separately through injected dispatchers.

## Application operations

- `RefreshOfficialPlaylist` downloads/parses the fixed M3U, merges channel data, synchronizes its EPG
  URL and records completion using the injected `Clock`.
- `RefreshOfficialEpg` downloads/parses XMLTV and publishes only a successful, still-current result.
  Failures preserve the previous cache; cancellation is rethrown.
- `RefreshCoordinator` serializes playlist and EPG work. With the process-owned `RefreshService`,
  concurrent requests for the same source also share one result. Nested playlist-triggered EPG
  work uses the already-held coordinator lock.
- `UpdateSettings` normalizes playback limits; `SelectChannel` validates the channel without
  persisting it as successfully played; `ToggleFavorite` updates the shared repository.
- `Clock` and `DispatcherProvider` are injected through `WukkiAppDependencies`. Platform roots use
  an IO dispatcher for blocking loads; tests use a fixed clock and deterministic dispatchers.

## Errors and presentation

Operations emit typed `RefreshEvent`/`AppFailure` values, not UI strings. The presentation mapper
alone knows localization keys. HTTP status, invalid URL, body-size limit, playlist/XMLTV validation
and network failures have explicit identities. Exception text is never interpreted as a translation
key or displayed as a network error. Wrapping exceptions preserves causes for diagnostics.

Manual work emits loading and completion feedback. Automatic successful work stays silent; errors
remain visible. A successfully downloaded playlist with missing or failing EPG is still usable and
retains the EPG warning rather than overwriting it with a playlist success message.

## Compatibility and follow-up

No serialized domain type, JSON field, cache format, WorkManager identifier or playback API changes.
The old `WukkiModel(initialState, loader, parser, saver, refreshService)` constructor remains usable.
PR 2's asynchronous bootstrap and save-error handling remain the persistence boundary.

`ApplicationBootstrap` can initialize the store, writer and `WukkiApplication` without constructing a
presentation model. Android WorkManager uses this path directly; the UI attaches its `WukkiModel`
adapter to the same process-owned runtime when an Activity exists. The headless worker flushes the
shared writer before reporting success and applies typed retry/backoff policy to application failures.

Streaming EPG, new indexing algorithms, selector optimization and UI lifecycle coordination belong
to later PRs.

Validation: existing migration/navigation/refresh tests plus headless application tests, controlled
clock/dispatcher tests, cancellation and late-response tests, and local HTTP-server failure tests.
