# Changelog (author: ZimtKing, as of 2026-10-23)

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
- v22: the whole tool is English (UI, messages, errors, code comments); permanent file-type hint under the upload button; understands the `current.*` messages of mod 0.2.0; fixed the lockout message (the viewer now matches the mod's reason "locked"). Views are named Plan / Current / Preview.

## Mod (BuildSync)
- 0.1.0: Java core + Fabric layer, Gradle project, GitHub action.
- 0.1.1: the topmost block of each column is shown as real (floors/roofs used to appear as stone); hatches/doors stay hidden. Build files (Gradle 9.7.1, chmod +x gradlew, --no-daemon) taken from ZimtKing's working setup.
- 0.1.2: all code comments translated to English; unused legacy files (Session, Terrain, …) removed.
- 0.1.3: all remaining texts of the mod translated to English (in-game messages, log lines, error texts, tests, README/GUIDE/CHANGELOG). Protocol reason texts are now English ("locked", "wrong code"); the viewer accepts both.
- 0.2.0: everything English, including the wire protocol and UI names: messages `ist.*` are now `current.*` (`current.begin/set/ground/end`), `IstEngine` is now `CurrentEngine`, "Soll/Ist" is now "Plan/Current", UI labels in the guide are English. **Needs viewer v22 or newer** (older viewers still expect `ist.*`).
- 0.2.1: "actual" renamed to "current" (viewer tab "Current", messages `current.*`, `CurrentEngine`; wording "planned state" / "current state"). Needs viewer v22 from the same date. Version 0.2.0 used `actual.*` and is replaced by this one.
