# Android refresh ownership (US-02)

AndroidAppGraph owns exactly one WukkiModel and RefreshService per process. MainActivity passes that model to Compose. WukkiRefreshWorker submits a refresh request to the graph; it does not create a model or load and save its own snapshot. All model mutations run on the main dispatcher. Downloads and parsing still run off the main thread.

RefreshService joins overlapping requests for the same resource into one operation and serializes playlist and EPG operations. Its scope belongs to the process, so an Activity disappearing or a caller being cancelled does not abandon a shared refresh. Playlist-triggered EPG loading runs inside the same serialized operation. User edits during a download update the shared model immediately; the refresh merges channels against that current state on completion.

AndroidStateStore is the single disk writer. It processes saves in order, and workers await the pending save before reporting success. An Activity restart reuses the model instead of reloading an older disk snapshot. Periodic WorkManager registration remains independent of the Activity; after process death, a worker initializes the same process owner from persisted state using the application context. MANUAL and due-time checks run against the owner's current settings.

## Automated race scenario

RefreshOwnershipTest blocks the worker's playlist download, starts a manual refresh, changes a favourite and lastChannel, then releases the download. Assertions require one download, one successful playlist timestamp commit, and both user changes in the final saved state. Intermediate user/source saves are legitimate; there is only one successful refresh result.

RefreshServiceTest also verifies that cancelling an Activity caller does not cancel the refresh, and that an EPG request waits for the playlist operation.

## Device lifecycle check

Enable a periodic refresh, remove the Activity from memory without force-stopping the package, and allow WorkManager to execute the registered work. The worker can initialize AndroidAppGraph without MainActivity and persists through LocalStore. Reopening the Activity observes the process model (or loads persisted state after process death). Android determines background execution timing; force-stop suppresses scheduled work until the package is launched again. This lifecycle check requires a device and is not covered by the JVM tests.
