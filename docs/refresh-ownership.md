# Android refresh ownership (US-02)

AndroidAppGraph owns exactly one `ApplicationBootstrap`, application runtime and `RefreshService` per process. MainActivity adds a `WukkiModel` presentation adapter only when the UI starts. `WukkiRefreshWorker` invokes the process-owned `WukkiApplication` use cases directly, so a worker-only process does not construct Compose or presentation state. Application mutations run on the main dispatcher; downloads and parsing run on their injected background dispatchers.

RefreshService joins overlapping requests for the same resource into one operation and serializes playlist and EPG operations. Its scope belongs to the process, so an Activity disappearing or a caller being cancelled does not abandon a shared refresh. Playlist-triggered EPG loading runs inside the same serialized operation. User edits during a download update the shared model immediately; the refresh merges channels against that current state on completion.

PR 3 introduced the Compose-independent operations and repositories; see [application-layer.md](application-layer.md). PR 4 connects WorkManager directly to those operations. The PR 2 `StateWriter` coalesces pending snapshots and serializes physical writes through `AndroidStateStore`; workers flush it before reporting success. An Activity restart reuses the process application, while process death causes the headless bootstrap to load the persisted state before checking due work. Manual mode and due-time checks use that current state.

Playlist and EPG jobs each have a stable fingerprint made from their interval and relevant successful-refresh timestamp. Unrelated state saves do not update WorkManager. A changed fingerprint realigns the periodic work with `CANCEL_AND_REENQUEUE`; the first sync after process start uses `UPDATE`, preserving an existing periodic schedule where possible. Both jobs require network connectivity and use a 30-second exponential backoff. Network failures, HTTP 408/429/5xx and unexpected failures retry up to five total attempts; malformed URLs, invalid playlist/XMLTV, missing EPG source, oversized responses and other non-transient HTTP responses wait for the next periodic run.

## Automated race scenario

RefreshOwnershipTest blocks the worker's playlist download, starts a manual refresh, changes a favourite and lastChannel, then releases the download. Assertions require one download, one successful playlist timestamp commit, and both user changes in the final saved state. Intermediate user/source saves are legitimate; there is only one successful refresh result.

RefreshServiceTest also verifies that cancelling an Activity caller does not cancel the refresh, and that an EPG request waits for the playlist operation.

## Device lifecycle check

Enable a periodic refresh, remove the Activity from memory without force-stopping the package, and allow WorkManager to execute the registered work. The worker can initialize `AndroidAppGraph` without `MainActivity` and persists through `StateWriter` and `AndroidStateStore`. Reopening the Activity attaches its presentation model to the same application state. Android determines background execution timing; force-stop suppresses scheduled work until the package is launched again. The worker-only bootstrap, schedule fingerprints and retry policy are unit tested; the final operating-system lifecycle still requires a device test.
