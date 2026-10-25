# BuildSync (Fabric mod for Minecraft 26.2, client only)

Author: ZimtKing · Version 0.3.0 · 2026-10-23

Sends the build state of a plan cuboid – **filtered, without xray advantages** – to the local building-plan tool (`index.html`).
Works in singleplayer and multiplayer worlds. Everything runs locally on your computer, nothing is hosted.

## Build the mod (once)

**Option A – let GitHub build it (no Java needed)**
1. Create a new (private) GitHub repository and upload the contents of this folder.
2. Open the *Actions* tab → wait for the "build" run → download `buildsync-mod` under *Artifacts* → it contains `buildsync-0.3.0.jar`.

**Option B – locally**
1. Install JDK 25 (e.g. Temurin 25).
2. Run `./gradlew build` in this folder (Windows: `gradlew.bat build`).
3. The mod is then in `build/libs/buildsync-0.3.0.jar` (the file *without* `-sources`).

## Install
Put `buildsync-0.3.0.jar` into the `mods` folder of Fabric Loader (≥ 0.19.5) for Minecraft 26.2, together with the **Fabric API**.
Sharing: just send the `.jar` to others – everybody uses it locally with their own copy of the tool.

## Pair
On first start, `config/buildsync/pairing.txt` is created with a code such as `AB12-CD34-EF56`. In game, `/buildsync code` shows it.
The mod only listens on `127.0.0.1` (port 38517 and up). The tool asks for this code once.

Commands: `/buildsync status` · `/buildsync code` · `/buildsync newcode` (new code) · `/buildsync stop` (end session, state is saved) · `/buildsync lan` (optional home-network mode for a phone, see GUIDE.md section 5).
Optional: `config/buildsync/origins.txt` – additional allowed origins of a web page (one per line). By default only locally opened files (`file://`) are allowed.

## Security
See the rules in the core (`src/main/java/de/buildsync/core/Rules.java`, `Limits.java`): the mod alone decides what the tool sees; underground, caves, ores and secret bases stay hidden, only blocks inside the plan cuboid are considered, and the limits are built in (they cannot be loosened via config).
Anyone who modifies the mod can bypass this – against xray clients only the server's anti-xray feature helps.

## Structure
- `src/main` – pure Java core without any Minecraft dependency (rules, server, protocol). Tests are in `tests/` (see below).
- `src/client` – thin Fabric layer (reads the world, block updates via mixin, commands).

## Core tests (without Minecraft)
    javac --release 21 -d out src/main/java/de/buildsync/core/*.java tests/de/buildsync/core/*.java
    java -cp out de.buildsync.core.CoreTests
