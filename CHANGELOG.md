# Changelog (author: ZimtKing, as of 2026-10-25)

## Viewer (BuildSync-vXX.html)
v0.0.1-dev: - added basic camera movement
            - added core foundation for file uploading and rendering
v0.0.2-dev: - fixed a visual bug that rendered non-full blocks as full blocks.
            - fixed textures
            - added y-axis slider to adjust building plan height and progress status
v0.0.3-dev: - added feature to upload texture packs
v0.0.4-dev: - fixed upload bug on mobile devices (3D-model not rendering)
              - fixed missing block faces in some scenarios
v0.0.5-dev: - added 2D top-down feature
v0.0.6-dev: - fixed a bug that caused transparent textures to render invisible
v0.0.7-dev: - fixed a problem that caused 3D-models to not render in rare occassions
v0.0.8-dev: - added "free-cam" mode that functions like minecraft spectator mode or freecam mod
v0.0.9-dev: - fixed the loader because there was a bug that caused the file to not open at all
            - fixed the freecam mode so the mouse cursor fully disappears (keybind "l")
            - added look sensitivity slider for freecam mode
v0.1.0-dev: - fixed a bug that caused the mouse cursor to reappear in the menu
v0.1.1-dev: - fixed the freecam so it stops moving when pressing l
v0.1.2-dev: - fixed a camera bug that caused the mouse to snap in some scenarios
v0.1.3-dev: - added live-sync core foundations and compatibility with the mod (v0.0.1)
            - added keybind "i" that switches between plan and current mode
v0.1.4-dev: - fixed live connection with the mod
v0.1.5-dev: - added preview of the surroundings when entering coordinates
v0.1.6-dev: - added clear error message when connection failed
            - fixed a bug that caused blocks to not show in the viewer
v0.1.7-dev: - fixed keybind "i"
v0.1.8-dev: - added mipmap to counter flickering
v0.1.9-dev: - fixed texture borders against light lines between blocks
v0.2.0-dev: - fixed texture filtering
v0.2.1-dev: - added mipmap slider (level 0-4)
**v0.2.2-release:** - translated tool from German to English
**v0.2.3-release:** - added feature to connect tool with mod cross-device. Needs mod v0.3.0-release
        

## Mod (BuildSync)
v0.0.1-dev: - created basic mod foundation for Fabric
v0.1.0-dev: - fixed basic anti-xray feature
v0.1.2-dev: - translated code comments to English
v0.1.3-dev: - fixed protocol reason texts
v0.2.0-dev: - translated missing German comments to English
            - fixed a bug that caused the ingame commands /buildsync newcode to not generate a new code
**v0.2.1-release:** - "actual" renamed to "current" for the viewer. Needs viewer v0.2.2-release
**v0.3.0-release:** - added optional lan mode: `/buildsync lan on` shows a warning, `/buildsync lan confirm` (within 30 s) switches it on, `/buildsync lan off` switches it                          off; always off after a restart. Only private addresses are accepted (never the internet), host must be a literal IP (against DNS rebinding), pairing                           code and lockout as before. In LAN mode the mod serves the bundled viewer at `http://PC-IP:port` (only `/` and `/index.html`). `/buildsync status` now                         shows server, LAN mode, viewer connected and session; the bare `/buildsync` command was removed. (new: address rules, host rules,                                              confirmation window, page serving). Needs viewer v0.2.3-release
