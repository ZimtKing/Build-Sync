package de.buildsync.core;

public class CoreTests {
    static int pass, fail;
    static void ok(boolean c, String m) { if (c) pass++; else { fail++; System.out.println("FAIL  " + m); } }

    static SimWorld world() {
        SimWorld w = new SimWorld();
        w.terrain(-1, -1, 2, 2);
        // secrets (all below the surface, inside the plan cuboid)
        for (int x = 10; x <= 14; x++) for (int z = 10; z <= 14; z++) for (int y = 56; y <= 58; y++) w.set(x, y, z, "minecraft:cave_air");   // cave
        w.set(3, 57, 3, "minecraft:diamond_ore");                                                                                          // tiefes Erz
        w.set(20, 62, 20, "minecraft:diamond_ore");                                                                                        // ore just below the surface
        for (int x = 24; x <= 28; x++) for (int z = 24; z <= 28; z++) { w.set(x, 56, z, "minecraft:oak_planks"); w.set(x, 61, z, "minecraft:oak_planks");
            for (int y = 57; y <= 60; y++) w.set(x, y, z, (x == 24 || x == 28 || z == 24 || z == 28) ? "minecraft:stone_bricks" : "minecraft:air"); }  // Geheimbasis
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
        testBaseline(); testChanges(); testTimeTravel(); testLimits(); testPersistence(); testPreview();
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
        ok(tool.at(3, 57 - 55, 3) == null || "DEFAULT".equals(tool.at(3, 57 - 55, 3)), "tiefes Erz: nichts gesendet");
        ok("minecraft:grass_block".equals(tool.at(1, 63 - 55, 1)) && tool.kind(1, 63 - 55, 1) == 1, "grass on the surface is real (unchanged)");
        ok("minecraft:dirt".equals(tool.at(1, 62 - 55, 1)), "Erde direkt darunter echt");
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
        w.set(3, 57, 3, "minecraft:air"); e.blockChanged(3, 57, 3);   // Erz abgebaut
        e.step(w, 2_000_000_000L);
        ok("minecraft:cobblestone".equals(tool.at(13, 64 - 55, 13)) && tool.kind(13, 64 - 55, 13) == 0, "built block is visible (kind = changed)");
        ok("minecraft:air".equals(tool.at(13, 60 - 55, 13)) && tool.kind(13, 60 - 55, 13) == 0, "gegrabener Keller sichtbar (echte Luft)");
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
        ok(tool.at(21, 58 - 55, 21) == null || "DEFAULT".equals(tool.at(21, 58 - 55, 21)), "Zeitreise: neuer Hohlraum bleibt verborgen");
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
        ok("minecraft:air".equals(t2.at(13, 59 - 55, 13)) && t2.kind(13, 59 - 55, 13) == 0, "gespeicherter Keller nach Neustart wieder sichtbar");
        ok(!anyRaw(t2, "diamond") && !anyRaw(t2, "chest"), "nach Neustart weiterhin nichts Verstecktes");
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
