# BuildSync – User guide (Minecraft 26.2, Fabric)

Author: ZimtKing · Mod 0.3.0 · Viewer v23 · 2026-10-25

Two parts: **`index.html`** (the tool, runs locally in the browser) and the **mod** (`buildsync-0.3.0.jar`). Nothing is hosted.

## 1. Build the mod (once)
- **Without Java:** upload this folder to a new GitHub repository → *Actions* tab → wait for the "build" run → download the `buildsync-mod` artifact → it contains the `.jar`.
- **Locally:** install JDK 25, run `./gradlew build` in the folder (Windows: `gradlew.bat build`). The file is in `build/libs/` (the one without `-sources`).
- If the build fails with an error: send the error text to Claude (the mod could not be built against the real Minecraft 26.2 here).

## 2. Install
Put `buildsync-0.3.0.jar` + the **Fabric API** into the `mods` folder (Fabric Loader ≥ 0.19.5, Minecraft 26.2). Sharing = send the `.jar` and the `index.html`.

## 3. Pair
Start Minecraft, enter a world/server, type `/buildsync code` → a code like `AB12-CD34-EF56`.
Open `index.html` **as a local file on the same PC** in Chrome, Edge or Firefox (not in the Claude app; a phone needs the network option, see section 5). Enter the code → *Connect*.

## 4. Use
1. Load a building plan (.litematic, .schem, .nbt …) – the plan (**Plan**) appears as usual; clicking marks blocks by hand.
2. *Live sync* card: choose a corner, enter X/Y/Z (or *Use my position*), set rotation/mirroring. The tool shows the size and the smallest and largest corner.
3. *Preview*: shows terrain + plan together, plus a hint about the ground height. Change the values → the preview reloads.
4. *Confirm & start live*: from now on the **Current** view shows the current state of the world. Switch with the buttons at the top or the **I** key. Camera, layers and the 2D view stay the same.
5. *Hide underground* (in the current view): shows cellars and tunnels on their own.
6. *End session* or `/buildsync stop` in game; *Discard state* deletes the saved state.

Commands in game: `/buildsync status`, `/buildsync code`, `/buildsync newcode`, `/buildsync stop`, `/buildsync lan`.

## 5. Use it on a phone or tablet (optional, home network)
By default only this computer can connect. To follow the plan on a phone in the same Wi-Fi:
1. In game type `/buildsync lan on`. The chat shows a **warning** (no encryption, only for networks you trust). Nothing is switched on yet.
2. Within 30 seconds type `/buildsync lan confirm`. The chat now shows an address like `http://192.168.1.20:38517`.
3. Open that address in the phone's browser, enter the pairing code (`/buildsync code`), confirm the warning in the viewer → *Connect*.
4. Switch it off again with `/buildsync lan off`. After a game restart it is always off. `/buildsync status` shows whether it is on and whether a viewer is connected.

How it is protected: off by default · two-step confirmation · only private addresses (192.168.x.x, 10.x.x.x, 172.16–31.x.x) – never the internet · only IP addresses are accepted as host (no names) · pairing code with lockout · only the viewer page itself is served (no files).
**Not encrypted:** anyone in the same network can listen in. Use it only at home, never on public Wi-Fi. If the phone cannot connect, check that the PC firewall allows Minecraft/Java in private networks.
The plan alone (without live sync) works on a phone without any of this: open `index.html` in the phone browser and load the plan file.

## What the tool never shows
Only the plan cuboid is observed. Below the surface (from 2 blocks deep) everything is fill; caves, ores and secret bases stay invisible. Visible are the surface, everything above it and blocks changed by players inside the plan. In the plan view nothing is ever marked automatically, and nothing is colored in the current view.
Limits: at most 256 blocks per axis, 4 million blocks, plan at most 128 blocks away from the player.
**Honestly:** this is fair protection, not tamper-proof. Anyone who modifies the mod or uses other mods can bypass it. Against xray clients only the server's anti-xray feature helps (e.g. Paper).
