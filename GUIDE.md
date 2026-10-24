# BuildSync – User guide (Minecraft 26.2, Fabric)

Author: ZimtKing · Mod 0.2.1 · 2026-10-23

Two parts: **`index.html`** (the tool, runs locally in the browser) and the **mod** (`buildsync-0.2.1.jar`). Nothing is hosted.

## 1. Build the mod (once)
- **Without Java:** upload this folder to a new GitHub repository → *Actions* tab → wait for the "build" run → download the `buildsync-mod` artifact → it contains the `.jar`.
- **Locally:** install JDK 25, run `./gradlew build` in the folder (Windows: `gradlew.bat build`). The file is in `build/libs/` (the one without `-sources`).
- If the build fails with an error: send the error text to Claude (the mod could not be built against the real Minecraft 26.2 here).

## 2. Install
Put `buildsync-0.2.1.jar` + the **Fabric API** into the `mods` folder (Fabric Loader ≥ 0.19.5, Minecraft 26.2). Sharing = send the `.jar` and the `index.html`.

## 3. Pair
Start Minecraft, enter a world/server, type `/buildsync code` → a code like `AB12-CD34-EF56`.
Open `index.html` **as a local file on the same PC** in Chrome, Edge or Firefox (not in the Claude app, not on a phone – the browser blocks the local connection there). Enter the code → *Connect*.

## 4. Use
1. Load a building plan (.litematic, .schem, .nbt …) – the plan (**Plan**) appears as usual; clicking marks blocks by hand.
2. *Live sync* card: choose a corner, enter X/Y/Z (or *Use my position*), set rotation/mirroring. The tool shows the size and the smallest and largest corner.
3. *Preview*: shows terrain + plan together, plus a hint about the ground height. Change the values → the preview reloads.
4. *Confirm & start live*: from now on the **Current** view shows the current state of the world. Switch with the buttons at the top or the **I** key. Camera, layers and the 2D view stay the same.
5. *Hide underground* (in the current view): shows cellars and tunnels on their own.
6. *End session* or `/buildsync stop` in game; *Discard state* deletes the saved state.

Commands in game: `/buildsync`, `/buildsync code`, `/buildsync newcode`, `/buildsync stop`.

## What the tool never shows
Only the plan cuboid is observed. Below the surface (from 2 blocks deep) everything is fill; caves, ores and secret bases stay invisible. Visible are the surface, everything above it and blocks changed by players inside the plan. In the plan view nothing is ever marked automatically, and nothing is colored in the current view.
Limits: at most 256 blocks per axis, 4 million blocks, plan at most 128 blocks away from the player.
**Honestly:** this is fair protection, not tamper-proof. Anyone who modifies the mod or uses other mods can bypass it. Against xray clients only the server's anti-xray feature helps (e.g. Paper).
