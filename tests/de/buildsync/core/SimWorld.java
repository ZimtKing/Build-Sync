package de.buildsync.core;

import java.util.HashMap;
import java.util.Map;

/** Simulated world for tests: chunks as arrays, palette shared with the core. */
public class SimWorld implements WorldView {
    final Palette pal = new Palette();
    final int minY = -64, height = 384;
    final Map<Long, int[]> chunks = new HashMap<>();   // [ (y-minY)*256 + lz*16 + lx ]
    final Map<Long, Object> tokens = new HashMap<>();
    double px = 8, py = 70, pz = 8;
    final int AIR = pal.intern("minecraft:air");

    static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }
    public int s(String st) { return pal.intern(st); }

    public void load(int cx, int cz) { chunks.computeIfAbsent(key(cx, cz), k -> new int[height * 256]); tokens.put(key(cx, cz), new Object()); }
    public void unload(int cx, int cz) { tokens.remove(key(cx, cz)); }
    public void reload(int cx, int cz) { tokens.put(key(cx, cz), new Object()); }
    public void set(int x, int y, int z, String st) { set(x, y, z, s(st)); }
    public void set(int x, int y, int z, int id) { chunks.computeIfAbsent(key(x >> 4, z >> 4), k -> new int[height * 256])[(y - minY) * 256 + (z & 15) * 16 + (x & 15)] = id; }
    public int get(int x, int y, int z) { int[] c = chunks.get(key(x >> 4, z >> 4)); return c == null ? 0 : c[(y - minY) * 256 + (z & 15) * 16 + (x & 15)]; }

    @Override public Palette palette() { return pal; }
    @Override public int minY() { return minY; }
    @Override public int height() { return height; }
    @Override public Object chunkToken(int cx, int cz) { return tokens.get(key(cx, cz)); }
    @Override public int stateAt(int x, int y, int z) { return get(x, y, z); }
    @Override public double playerX() { return px; }
    @Override public double playerZ() { return pz; }
    @Override public double playerY() { return py; }

    /** Terrain: stone up to 59, dirt 60-62, grass 63; cave, ores, secret base. */
    public void terrain(int cx0, int cz0, int cx1, int cz1) {
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            load(cx, cz);
            for (int x = cx * 16; x < cx * 16 + 16; x++) for (int z = cz * 16; z < cz * 16 + 16; z++) {
                for (int y = -64; y <= 59; y++) set(x, y, z, "minecraft:stone");
                for (int y = 60; y <= 62; y++) set(x, y, z, "minecraft:dirt");
                set(x, 63, z, "minecraft:grass_block");
            }
        }
    }
}
