# UI selector and lifecycle ownership

`WukkiModel` is the compatibility boundary between `WukkiApplication` and Compose. It publishes
separate observable slices for channels, settings, EPG sources and EPG programme content. Feature
state must depend on the narrowest slice possible; `AppState` is retained for integration and tests,
not as a dashboard-wide recomposition key.

The channel directory sorts, groups and indexes channels once for each immutable channel snapshot or
filter change. Settings updates retain the directory object. EPG replacement increments a dedicated
content version used by Channels and Guide, without invalidating unrelated Settings content.

`UiLifecyclePolicy` determines which composition-owned jobs may run. When Android reports an
inactive Activity, programme clocks, feedback/overlay timeouts and Compose refresh loops stop. On
resume, time-sensitive screens refresh their clock immediately and restart relevant timeouts.
Settings remains interactive but does not maintain a programme clock.

Android automatic refresh ownership belongs to WorkManager and the process-wide application graph.
Desktop has no background scheduler, so its foreground composition retains the automatic refresh
loop. Both paths invoke the same serialized application use cases and persistence writer.

The dashboard remembers one `AppSessionController` and one callback bundle for the composition
lifetime. UI-state contracts are marked immutable, allowing Compose to skip stable feature content
when an unrelated parent state changes.
