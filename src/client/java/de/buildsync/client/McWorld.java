package de.buildsync.client;

import java.util.IdentityHashMap;

import de.buildsync.core.Palette;
import de.buildsync.core.WorldView;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** The only place that touches Minecraft classes for world data. Use on the client thread only. */
final class McWorld implements WorldView {
    final Palette pal = new Palette();
    private ClientLevel level;
    private LocalPlayer player;
    private final IdentityHashMap<BlockState, Integer> cache = new IdentityHashMap<>();
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
    private int minY, height;
    private int lastCx = Integer.MIN_VALUE, lastCz;
    private LevelChunk last;

    void bind(ClientLevel l, LocalPlayer p) {
        if (l != level) { level = l; last = null; lastCx = Integer.MIN_VALUE; minY = intCall(l, 0, "getMinY", "getMinBuildHeight"); height = intCall(l, 384, "getHeight"); }
        player = p;
    }

    /** Height methods via reflection with a fallback (their names have changed between versions before). */
    private static int intCall(Object o, int def, String... names) {
        for (String n : names) {
            try { return ((Number) o.getClass().getMethod(n).invoke(o)).intValue(); } catch (Throwable ignored) { }
        }
        return def;
    }

    ClientLevel level() { return level; }

    @Override public Palette palette() { return pal; }
    @Override public int minY() { return minY; }
    @Override public int height() { return height; }

    @Override public Object chunkToken(int cx, int cz) {
        ChunkAccess c = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
        return c instanceof LevelChunk ? c : null;
    }

    @Override public int stateAt(int x, int y, int z) {
        int cx = x >> 4, cz = z >> 4;
        if (cx != lastCx || cz != lastCz || last == null) {
            Object t = chunkToken(cx, cz);
            last = t instanceof LevelChunk ? (LevelChunk) t : null; lastCx = cx; lastCz = cz;
        }
        if (last == null) return 0;
        BlockState s = last.getBlockState(mp.set(x, y, z));
        Integer i = cache.get(s);
        if (i == null) { i = pal.intern(stateString(s)); cache.put(s, i); }
        return i;
    }

    @Override public double playerX() { return player.getX(); }
    @Override public double playerY() { return player.getY(); }
    @Override public double playerZ() { return player.getZ(); }

    /** "minecraft:oak_stairs[facing=east,half=bottom]" – block state only, no block entity data. */
    static String stateString(BlockState s) {
        StringBuilder b = new StringBuilder(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
        boolean first = true;
        for (Property<?> p : s.getProperties()) {
            b.append(first ? '[' : ',').append(p.getName()).append('=').append(value(s, p));
            first = false;
        }
        if (!first) b.append(']');
        return b.toString();
    }

    private static <T extends Comparable<T>> String value(BlockState s, Property<T> p) { return p.getName(s.getValue(p)); }
}
