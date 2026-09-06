# Remote orchestration (US-03)

AppRemoteState.reduce(AppRemoteKey) is the platform-neutral routing entry point. It returns updated navigation state, ordered effects, and whether the event is consumed. It composes the existing menu, channel, settings, live preview and back reducers. Exit confirmation receives time as state input. WukkiApp translates key-down events, applies the returned state, and executes effects through the existing model, guide and UI callbacks. Android system Back still comes exclusively through OnBackPressedDispatcher.

Automated cases cover three digits plus Confirm, overlay/menu/exit Back order, hidden live navigation, double Back, volume adjustment, D-pad preview versus immediate PageUp, and search Escape/Backspace. Existing navigation and preview tests remain in the shared suite.

## Manual smoke checklist — pending device availability

No Android device was attached during implementation (`adb devices -l` returned an empty list). The following checks have not been executed on a device:

- Live: D-pad Up opens the current programme preview; subsequent Up/Down browses channels; Confirm opens the previewed channel.
- Live: PageUp/PageDown immediately changes channels, including with menu focus.
- Enter three channel digits and Confirm; verify the selected stream and number overlay.
- With navigation visible, Back closes programme information, then focuses the menu; double Back follows the existing exit policy. With navigation hidden, the first Back reveals it.
- Channels search: Backspace edits text; Escape closes the search.
- Settings / Playback / Volume: Left/Right changes volume by five, clamped to 0–100.
- Touch: tap toggles programme information, next/previous gestures switch streams, and the navigation gesture reveals the menu. These callbacks were left unchanged.
- Guide: D-pad and PageUp/PageDown navigate; Confirm opens details and Back closes them.
