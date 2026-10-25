# Changelog (author: ZimtKing, as of 2026-10-25)

## Viewer (index-vXX.html)
- v1–v12: viewer with schematic import, textures, resource pack, layers, 2D view, spectator camera, hidden mouse, phone controls.
- v13: live-sync UI (pairing code, placement with rotate/mirror, plan/current switch, hide underground).
- v14: live connection tested against the mod core; automatic switch to the current view after confirming.
- v15: surroundings preview before confirming (with ground-height hint).
- v16: clear messages for an empty build area; "my position" uses the block under the feet.
- v17: key I toggles plan/current (also while a number field has focus).
- v18: first mipmap smoothing against flickering gaps.
- v19: texture border (padding) against light lines between blocks.
- v20: selectable texture filter (intermediate step).
- v21: mipmap levels 0–4 like in Minecraft.
- v22: the whole tool is English (UI, messages, errors, code comments); permanent file-type hint under the upload button; understands the `current.*` messages of mod 0.2.1; fixed the lockout message (the viewer now matches the mod's reason "locked"). Views are named Plan / Current / Preview.

- v23: works over the home network: when the page is opened from the mod (`http://PC-IP:port`) it connects to that address automatically; optional "Advanced: connect over my network" field (private IP addresses only, names and public addresses are refused); warning dialog before the pairing code is sent over the network, permanent yellow notice while connected over the network; clearer message for an invalid port. Needs mod 0.3.0.

## Mod (BuildSync)
- 0.1.0: Java core + Fabric layer, Gradle project, GitHub action.
- 0.1.1: the topmost block of each column is shown as real (floors/roofs used to appear as stone); hatches/doors stay hidden. Build files (Gradle 9.7.1, chmod +x gradlew, --no-daemon) taken from ZimtKing's working setup.
- 0.1.2: all code comments translated to English; unused legacy files (Session, Terrain, …) removed.
- 0.1.3: all remaining texts of the mod translated to English (in-game messages, log lines, error texts, tests, README/GUIDE/CHANGELOG). Protocol reason texts are now English ("locked", "wrong code"); the viewer accepts both.
- 0.2.0: everything English, including the wire protocol and UI names: messages `ist.*` became `actual.*`, `IstEngine` became `ActualEngine`, "Soll/Ist" became "Plan/Actual" (replaced by 0.2.1 right away), UI labels in the guide are English.
- 0.2.1: "actual" renamed to "current" (viewer tab "Current", messages `current.*`, `CurrentEngine`; wording "planned state" / "current state"). Needs viewer v22 from the same date. Version 0.2.0 used `actual.*` and is replaced by this one.
- 0.2.1 (follow-up): last German test messages and comments translated (tests only, no change to the mod itself).
- 0.3.0: optional LAN mode for phones/tablets in the home network. `/buildsync lan on` shows a warning, `/buildsync lan confirm` (within 30 s) switches it on, `/buildsync lan off` switches it off; always off after a restart. Only private addresses are accepted (never the internet), host must be a literal IP (against DNS rebinding), pairing code and lockout as before. In LAN mode the mod serves the bundled viewer at `http://PC-IP:port` (only `/` and `/index.html`). `/buildsync status` now shows server, LAN mode, viewer connected and session; the bare `/buildsync` command was removed. 83 core tests (new: address rules, host rules, confirmation window, page serving). Needs viewer v23.
