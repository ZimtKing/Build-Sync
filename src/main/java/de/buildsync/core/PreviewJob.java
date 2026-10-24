package de.buildsync.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Surroundings preview for placement: terrain around a (not yet confirmed) cuboid.
 * The same rules apply as for the current state, just without a baseline: surface and everything above it real, underground as fill.
 */
public final class PreviewJob {
    public final int id;
    public final int rx0, ry0, rz0, rsx, rsy, rsz;
    private final Palette pal;
    private final Emitter em;
    private final Emitter.Chan ch;
    private final List<int[]> chunks = new ArrayList<>();
    private int next = 0;
    private boolean began = false;

    /** margin is clamped to the allowed limits. */
    public PreviewJob(int id, Placement pl, int margin, WorldView w, Palette pal, Emitter em) {
        this.id = id; this.pal = pal; this.em = em; this.ch = em.chan("pv");
        int m = Math.max(0, Math.min(margin, Limits.PREVIEW_MARGIN_MAX));
        while (m > 0 && (long) (pl.sx + 2 * m) * (pl.sz + 2 * m) > Limits.PREVIEW_MAX_COLS) m--;
        rx0 = pl.x - m; rz0 = pl.z - m; rsx = pl.sx + 2 * m; rsz = pl.sz + 2 * m;
        int minY = w.minY(), top = minY + w.height() - 1;
        ry0 = Math.max(minY, pl.y - Limits.PREVIEW_BELOW);
        int ry1 = Math.min(top, pl.y1() + Limits.PREVIEW_ABOVE);
        rsy = Math.max(1, ry1 - ry0 + 1);
        int cx0 = Math.floorDiv(rx0, 16), cx1 = Math.floorDiv(rx0 + rsx - 1, 16), cz0 = Math.floorDiv(rz0, 16), cz1 = Math.floorDiv(rz0 + rsz - 1, 16);
        double mx = pl.x + pl.sx / 2.0, mz = pl.z + pl.sz / 2.0;
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) chunks.add(new int[]{cx, cz});
        chunks.sort((a, b) -> Double.compare(Math.hypot(a[0] * 16 + 8 - mx, a[1] * 16 + 8 - mz), Math.hypot(b[0] * 16 + 8 - mx, b[1] * 16 + 8 - mz)));
    }

    /** @return true when finished */
    public boolean step(WorldView w, long budgetNanos) {
        long end = System.nanoTime() + budgetNanos;
        if (!began) {
            began = true;
            em.raw("{\"t\":\"pv.begin\",\"id\":" + id + ",\"origin\":[" + rx0 + "," + ry0 + "," + rz0 + "],\"size\":[" + rsx + "," + rsy + "," + rsz + "],\"d\":" + Limits.DEPTH + "}");
        }
        while (next < chunks.size() && System.nanoTime() < end) {
            int[] c = chunks.get(next++);
            if (w.chunkToken(c[0], c[1]) == null) continue;
            int x0 = Math.max(rx0, c[0] * 16), x1 = Math.min(rx0 + rsx - 1, c[0] * 16 + 15);
            int z0 = Math.max(rz0, c[1] * 16), z1 = Math.min(rz0 + rsz - 1, c[1] * 16 + 15);
            int nx = x1 - x0 + 1, nz = z1 - z0 + 1;
            int[] g = new int[nx * nz];
            for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) g[(z - z0) * nx + (x - x0)] = Ground.compute(w, x, z);
            ch.ground(x0 - rx0, z0 - rz0, nx, nz, g, ry0);
            for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
                int gy = g[(z - z0) * nx + (x - x0)];
                for (int dy = 0; dy < rsy; dy++) {
                    int y = ry0 + dy, C = w.stateAt(x, y, z);
                    int code = Rules.disp(pal, C, C, false, y, gy);
                    if (code != Rules.DEFAULT) ch.set(x - rx0, dy, z - rz0, code);
                }
            }
            ch.flush();
        }
        ch.flush();
        if (next >= chunks.size()) { em.raw("{\"t\":\"pv.done\",\"id\":" + id + "}"); return true; }
        return false;
    }
}
