package de.buildsync.core;

public class CoreTests {
    static int pass, fail;
    static void ok(boolean c, String m) { if (c) pass++; else { fail++; System.out.println("FAIL  " + m); } }

    static SimWorld world() {
        SimWorld w = new SimWorld();
        w.terrain(-1, -1, 2, 2);
        // secrets (all below the surface, inside the plan cuboid)
        for (int x = 10; x <= 14; x++) for (int z = 10; z <= 14; z++) for (int y = 56; y <= 58; y++) w.set(x, y, z, "minecraft:cave_air");   // cave
        w.set(3, 57, 3, "minecraft:diamond_ore");                                                                                          // deep ore
        w.set(20, 62, 20, "minecraft:diamond_ore");                                                                                        // ore just below the surface
        for (int x = 24; x <= 28; x++) for (int z = 24; z <= 28; z++) { w.set(x, 56, z, "minecraft:oak_planks"); w.set(x, 61, z, "minecraft:oak_planks");
            for (int y = 57; y <= 60; y++) w.set(x, y, z, (x == 24 || x == 28 || z == 24 || z == 28) ? "minecraft:stone_bricks" : "minecraft:air"); }  // secret base
        w.set(26, 57, 26, "minecraft:chest[facing=north,type=single]"); w.set(25, 57, 25, "minecraft:spawner");
        // camouflage hatch: plank lid over a cavity
        for (int y = 57; y <= 62; y++) w.set(18, y, 5, "minecraft:air"); w.set(18, 63, 5, "minecraft:oak_trapdoor[facing=north,half=bottom,open=false]");
        w.set(18, 57, 5, "minecraft:ender_chest");
        // camouflage roof made of player-placed leaves above a cavity
        for (int y = 57; y <= 62; y++) w.set(5, y, 20, "minecraft:air"); w.set(5, 63, 20, "minecraft:oak_leaves[persistent=true,distance=7]"); w.set(5, 58, 20, "minecraft:diamond_block");
        // real tree: trunk on grass, natural leaves
        for (int y = 64; y <= 67; y++) w.set(8, y, 8, "minecraft:oak_log[axis=y]");
        for (int x = 6; x <= 10; x++) for (int z = 6; z <= 10; z++) w.set(x, 68, z, "minecraft:oak_leaves[persistent=false,distance=1]");
        return w;
    }

    static Placement PL = new Placement("minecraft:overworld", 0, 55, 0, 32, 21, 32);

    public static void main(String[] a) throws Exception {
        testBaseline(); testChanges(); testTimeTravel(); testLimits(); testPersistence(); testPreview(); testLan();
        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    static boolean anyRaw(Tool t, String s) { for (String r : t.raw) if (r.contains(s)) return true; return false; }

    static void testBaseline() {
        SimWorld w = world(); Palette pal = w.pal; Tool tool = new Tool(); Emitter em = new Emitter(pal); em.attach(tool);
        CurrentEngine e = new CurrentEngine(PL, pal, em); e.snapshot();
        for (int i = 0; i < 4; i++) e.step(w, 2_000_000_000L);
        ok(e.loadedCount() == 4, "all 4 chunks loaded");
        for (String bad : new String[]{"diamond", "chest", "spawner", "oak_planks", "stone_bricks", "cave_air", "trapdoor", "ender"}) ok(!anyRaw(tool, bad), "baseline reveals nothing: " + bad);
        ok(!anyRaw(tool, "\"minecraft:stone\""), "stone is never sent as a block (fill is only derived)");
        ok("FILL".equals(tool.at(20, 62 - 55, 20)), "ore just below the surface becomes fill");
        ok(tool.at(3, 57 - 55, 3) == null || "DEFAULT".equals(tool.at(3, 57 - 55, 3)), "deep ore: nothing sent");
        ok("minecraft:grass_block".equals(tool.at(1, 63 - 55, 1)) && tool.kind(1, 63 - 55, 1) == 1, "grass on the surface is real (unchanged)");
        ok("minecraft:dirt".equals(tool.at(1, 62 - 55, 1)), "dirt directly below is real");
        // ground heights
        ok(tool.ground.get(Tool.k2(1, 1)) == 63 - 55, "ground height of a normal column");
        ok(tool.ground.get(Tool.k2(18, 5)) == 63 - 55, "ground height under a camouflage hatch = hatch (cavity stays invisible)");
        ok(tool.ground.get(Tool.k2(5, 20)) == 63 - 55, "ground height under player leaves = leaves (cavity invisible)");
        ok(tool.ground.get(Tool.k2(8, 8)) == 63 - 55, "tree trunk on grass: ground = grass");
        ok("minecraft:oak_log[axis=y]".equals(tool.at(8, 65 - 55, 8)), "tree trunk above the ground is visible");
        ok(tool.at(8, 68 - 55, 8) != null && tool.at(8, 68 - 55, 8).startsWith("minecraft:oak_leaves"), "natural leaves are visible");
    }

    static void testChanges() {
        SimWorld w = world(); Palette pal = w.pal; Tool tool = new Tool(); Emitter em = new Emitter(pal); em.attach(tool);
        CurrentEngine e = new CurrentEngine(PL, pal, em); e.snapshot();
        for (int i = 0; i < 4; i++) e.step(w, 2_000_000_000L);
        // player builds a house and digs a cellar (partly overlapping the cave)
        for (int x = 12; x <= 14; x++) for (int z = 12; z <= 14; z++) { w.set(x, 64, z, "minecraft:cobblestone"); e.blockChanged(x, 64, z); for (int y = 57; y <= 60; y++) { w.set(x, y, z, "minecraft:air"); e.blockChanged(x, y, z); } }
        w.set(3, 57, 3, "minecraft:air"); e.blockChanged(3, 57, 3);   // ore mined
        e.step(w, 2_000_000_000L);
        ok("minecraft:cobblestone".equals(tool.at(13, 64 - 55, 13)) && tool.kind(13, 64 - 55, 13) == 0, "built block is visible (kind = changed)");
        ok("minecraft:air".equals(tool.at(13, 60 - 55, 13)) && tool.kind(13, 60 - 55, 13) == 0, "dug-out cellar is visible (real air)");
        ok("minecraft:air".equals(tool.at(3, 57 - 55, 3)), "mined-out ore only appears as air");
        ok(!anyRaw(tool, "diamond"), "the ore name is never sent, even after mining");
        ok(tool.at(10, 57 - 55, 10) == null || "DEFAULT".equals(tool.at(10, 57 - 55, 10)), "untouched part of the cave stays invisible");
        // undo the build -> unchanged again
        w.set(13, 64, 13, "minecraft:air"); e.blockChanged(13, 64, 13); e.step(w, 2_000_000_000L);
        ok("DEFAULT".equals(tool.at(13, 64 - 55, 13)), "an undone change disappears again");
        // a change far away from the player is ignored
        w.px = 5000; w.set(1, 64, 1, "minecraft:gold_block"); e.blockChanged(1, 64, 1); e.step(w, 2_000_000_000L);
        ok(!anyRaw(tool, "gold_block"), "changes far from the player are not reported (the rescan net only covers nearby chunks as well)");
    }

    static void testTimeTravel() {
        SimWorld w = world(); Palette pal = w.pal; Tool tool = new Tool(); Emitter em = new Emitter(pal); em.attach(tool);
        CurrentEngine e = new CurrentEngine(PL, pal, em); e.snapshot();
        for (int i = 0; i < 4; i++) e.step(w, 2_000_000_000L);
        w.unload(1, 1); e.step(w, 2_000_000_000L);
        // while the chunk is unloaded, someone else builds a base deep underground
        for (int x = 20; x <= 22; x++) for (int z = 20; z <= 22; z++) for (int y = 57; y <= 59; y++) w.set(x, y, z, y == 57 ? "minecraft:furnace" : "minecraft:air");
        w.reload(1, 1); for (int i = 0; i < 4; i++) e.step(w, 2_000_000_000L);
        ok(!anyRaw(tool, "furnace"), "time travel: changes made while away are not visible");
        ok(tool.at(21, 58 - 55, 21) == null || "DEFAULT".equals(tool.at(21, 58 - 55, 21)), "time travel: a new cavity stays hidden");
        // own change survives the reload
        w.set(17, 64, 17, "minecraft:glass"); e.blockChanged(17, 64, 17); e.step(w, 2_000_000_000L);
        w.unload(1, 1); e.step(w, 2_000_000_000L); w.reload(1, 1); for (int i = 0; i < 3; i++) e.step(w, 2_000_000_000L);
        ok("minecraft:glass".equals(tool.at(17, 64 - 55, 17)) && tool.kind(17, 64 - 55, 17) == 0, "own change is kept after a chunk reload");
        // a change observed live deep underground (other players, chunk loaded) is shown inside the plan – as agreed
        w.set(21, 58, 21, "minecraft:torch"); e.blockChanged(21, 58, 21); e.step(w, 2_000_000_000L);
        ok(tool.at(21, 58 - 55, 21) != null && tool.at(21, 58 - 55, 21).contains("torch"), "a change observed live inside the plan is visible");
    }

    static void testLimits() {
        SimWorld w = world();
        ok(new Placement("d", 0, 55, 0, 257, 10, 10).check(w) != null, "edge length too large rejected");
        ok(new Placement("d", 0, 55, 0, 256, 256, 256).check(w) != null, "volume too large rejected");
        ok(new Placement("d", 2000, 55, 2000, 10, 10, 10).check(w) != null, "too far from the player rejected");
        ok(new Placement("d", 0, 300, 0, 10, 100, 10).check(w) != null, "height outside the world rejected");
        ok(new Placement("d", 0, 55, 0, 32, 21, 32).check(w) == null, "valid placement accepted");
    }

    static void testPersistence() throws Exception {
        SimWorld w = world(); Palette pal = w.pal; Tool tool = new Tool(); Emitter em = new Emitter(pal); em.attach(tool);
        CurrentEngine e = new CurrentEngine(PL, pal, em); e.snapshot();
        for (int i = 0; i < 4; i++) e.step(w, 2_000_000_000L);
        w.set(13, 59, 13, "minecraft:air"); e.blockChanged(13, 59, 13); e.step(w, 2_000_000_000L);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); e.save(new java.io.DataOutputStream(bo));
        // new session (game restarted): reload chunks, the saved change stays known
        Tool t2 = new Tool(); Emitter em2 = new Emitter(pal); em2.attach(t2);
        CurrentEngine e2 = new CurrentEngine(PL, pal, em2); e2.load(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bo.toByteArray()))); e2.snapshot();
        for (int i = 0; i < 4; i++) e2.step(w, 2_000_000_000L);
        ok("minecraft:air".equals(t2.at(13, 59 - 55, 13)) && t2.kind(13, 59 - 55, 13) == 0, "saved cellar is visible again after a restart");
        ok(!anyRaw(t2, "diamond") && !anyRaw(t2, "chest"), "still nothing hidden after a restart");
    }

    static void testLan() throws Exception {
        // private / public address classification
        ok(LiveServer.isPrivate(java.net.InetAddress.getByName("192.168.1.20")), "192.168.x.x is private");
        ok(LiveServer.isPrivate(java.net.InetAddress.getByName("10.0.0.5")), "10.x.x.x is private");
        ok(LiveServer.isPrivate(java.net.InetAddress.getByName("172.20.1.1")), "172.16-31.x.x is private");
        ok(LiveServer.isPrivate(java.net.InetAddress.getByName("fd12:3456::1")), "IPv6 unique-local is private");
        ok(!LiveServer.isPrivate(java.net.InetAddress.getByName("8.8.8.8")), "8.8.8.8 is public");
        ok(!LiveServer.isPrivate(java.net.InetAddress.getByName("172.32.0.1")), "172.32.x.x is public");
        ok(!LiveServer.isPrivate(java.net.InetAddress.getByName("2001:4860:4860::8888")), "public IPv6 is public");
        // host header rules
        ok(LiveServer.hostAllowed("127.0.0.1:38517", 38517, false), "loopback host allowed (LAN off)");
        ok(!LiveServer.hostAllowed("192.168.1.20:38517", 38517, false), "LAN host rejected while LAN is off");
        ok(LiveServer.hostAllowed("192.168.1.20:38517", 38517, true), "LAN host allowed while LAN is on");
        ok(!LiveServer.hostAllowed("192.168.1.20:1234", 38517, true), "wrong port rejected");
        ok(!LiveServer.hostAllowed("8.8.8.8:38517", 38517, true), "public IP host rejected");
        ok(!LiveServer.hostAllowed("evil.example:38517", 38517, true), "DNS name host rejected (DNS rebinding)");
        ok(!LiveServer.hostAllowed("add.beef:38517", 38517, true), "hex-looking DNS name rejected");
        ok(LiveServer.hostAllowed("[fd12:3456::1]:38517", 38517, true), "private IPv6 literal allowed");
        // two-step confirmation
        LanGate g = new LanGate();
        ok(!g.confirm(1000), "confirm without request fails");
        g.request(1000); ok(g.confirm(1000 + LanGate.WINDOW_MS), "confirm inside the window works");
        ok(!g.confirm(1001), "a confirmation can only be used once");
        g.request(1000); ok(!g.confirm(1000 + LanGate.WINDOW_MS + 1), "confirm after the window fails");
        g.request(1000); g.cancel(); ok(!g.confirm(1001), "cancel removes the request");
        ok(LanGate.WARNING.length >= 4 && String.join(" ", LanGate.WARNING).contains("NOT encrypted"), "warning mentions missing encryption");
        // real server: LAN off by default, on/off switching keeps the port
        java.util.concurrent.atomic.AtomicInteger hello = new java.util.concurrent.atomic.AtomicInteger();
        LiveServer srv = new LiveServer(38700, 20, "AAAA-BBBB-CCCC", java.util.Set.of(), new LiveServer.Listener() {
            public void onAuthed(LiveServer.Conn c) {} public void onMessage(LiveServer.Conn c, java.util.Map<String, Object> m) {} public void onClosed(LiveServer.Conn c) {} }, s -> {});
        int port = srv.start();
        final byte[] PAGE = "<html>viewer</html>".getBytes();
        srv.setPage(() -> PAGE);
        ok(!srv.lanOn(), "LAN mode is off by default");
        String lanIp = null;
        for (java.util.Enumeration<java.net.NetworkInterface> e = java.net.NetworkInterface.getNetworkInterfaces(); e.hasMoreElements() && lanIp == null; ) {
            java.net.NetworkInterface ni = e.nextElement();
            if (!ni.isUp() || ni.isLoopback()) continue;
            for (java.util.Enumeration<java.net.InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) { java.net.InetAddress x = a.nextElement(); if (x instanceof java.net.Inet4Address && x.isSiteLocalAddress()) { lanIp = x.getHostAddress(); break; } }
        }
        if (lanIp == null && System.getProperty("os.name", "").toLowerCase().contains("linux")) lanIp = "127.0.0.2";   // on Linux the whole 127/8 block is local: a stand-in for a LAN address
        if (lanIp == null) System.out.println("note: no private network address on this machine - live LAN connection test skipped");
        else {
            ok(!upgrade(lanIp, port, lanIp + ":" + port), "LAN off: connecting via the LAN address fails");
            srv.setLan(true);
            ok(srv.lanOn() && srv.port() == port, "LAN on keeps the port");
            ok(upgrade(lanIp, port, lanIp + ":" + port), "LAN on: WebSocket handshake via the LAN address works");
            String r1 = http(lanIp, port, lanIp + ":" + port, "/");
            ok(r1.startsWith("HTTP/1.1 200") && r1.endsWith("<html>viewer</html>") && r1.contains("Cache-Control: no-store"), "LAN on: the page is served");
            ok(http(lanIp, port, lanIp + ":" + port, "/index.html?x=1").startsWith("HTTP/1.1 200"), "LAN on: /index.html with a query is served");
            ok(http(lanIp, port, lanIp + ":" + port, "/../etc/passwd").startsWith("HTTP/1.1 404"), "LAN on: other paths give 404");
            ok(http(lanIp, port, lanIp + ":" + port, "/config/pairing.txt").startsWith("HTTP/1.1 404"), "LAN on: no file access");
            ok(!http(lanIp, port, "evil.example:" + port, "/").startsWith("HTTP/1.1 200"), "LAN on: page not served for a wrong Host header");
            ok(!upgrade(lanIp, port, "evil.example:" + port), "LAN on: wrong Host header is rejected");
            ok(upgrade("127.0.0.1", port, "127.0.0.1:" + port), "LAN on: loopback still works");
            srv.setLan(false);
            ok(!upgrade(lanIp, port, lanIp + ":" + port), "LAN off again: LAN address fails");
            ok(!http(lanIp, port, lanIp + ":" + port, "/").startsWith("HTTP/1.1 200"), "LAN off again: no page");
            ok(!http("127.0.0.1", port, "127.0.0.1:" + port, "/").startsWith("HTTP/1.1 200"), "LAN off: the page is not served on loopback either");
            ok(upgrade("127.0.0.1", port, "127.0.0.1:" + port), "LAN off: loopback still works");
        }
        srv.close();
    }

    /** Plain HTTP GET; returns the whole response text (status line, headers, body) or "" on failure. */
    static String http(String ip, int port, String hostHeader, String path) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(ip, port), 1500); s.setSoTimeout(1500);
            s.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: " + hostHeader + "\r\n\r\n").getBytes());
            s.getOutputStream().flush();
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
            while ((n = s.getInputStream().read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } catch (Exception e) { return ""; }
    }

    /** Opens a raw WebSocket handshake; true if the server answers 101. */
    static boolean upgrade(String ip, int port, String hostHeader) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(ip, port), 1500); s.setSoTimeout(1500);
            s.getOutputStream().write(("GET / HTTP/1.1\r\nHost: " + hostHeader + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n").getBytes());
            s.getOutputStream().flush();
            byte[] b = new byte[64]; int n = s.getInputStream().read(b);
            return n > 0 && new String(b, 0, n).startsWith("HTTP/1.1 101");
        } catch (Exception e) { return false; }
    }

    static void testPreview() {
        SimWorld w = world(); Palette pal = w.pal; Tool tool = new Tool(); Emitter em = new Emitter(pal); em.attach(tool);
        PreviewJob j = new PreviewJob(1, new Placement("minecraft:overworld", 0, 55, 0, 32, 21, 32), 100, w, pal, em);
        ok(j.rx0 >= -Limits.PREVIEW_MARGIN_MAX, "preview margin is clamped");
        while (!j.step(w, 2_000_000_000L)) {}
        for (String bad : new String[]{"diamond", "chest", "spawner", "oak_planks", "stone_bricks", "cave_air", "ender"}) ok(!anyRaw(tool, bad), "preview reveals nothing: " + bad);
        ok("minecraft:grass_block".equals(tool.at(-j.rx0 + 1, 63 - j.ry0, -j.rz0 + 1)), "preview shows the surface");
    }
}
