package de.buildsync.core;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Watches the plan cuboid and delivers only what the rules (Rules) allow.
 *
 * Important properties against abuse:
 *  - "Changed" means: noticed live during observation (block update or reconciliation of a loaded chunk).
 *    When a chunk is reloaded, its state becomes the new baseline – only own changes already known are kept.
 *    This prevents a "time travel diff" (old state vs. today's state) from being used to discover other players' builds/caves.
 *  - Only cells inside the cuboid are considered; ground heights are determined once per column and stay stable.
 *  - Changes far away from the player are ignored.
 */
public final class CurrentEngine {
    public final Placement pl;
    private final Palette pal;
    private final Emitter em;
    private final Emitter.Chan ch;
    private final int cx0, cz0, ncx, ncz;
    private final Rec[] recs;
    private final LinkedHashSet<Integer> pend = new LinkedHashSet<>();
    private int rr = 0, rescanTick = 0;
    public boolean dirty;   // there is something to save

    static final class Rec {
        int cx, cz, x0, z0, nx, nz;
        char[] base, sent;
        BitSet chg;
        int[] ground;
        boolean groundKnown, loaded;
        Object token;
        Map<Integer, Integer> pers;   // from file / last session: cell index -> baseline palette index
    }

    public CurrentEngine(Placement pl, Palette pal, Emitter em) {
        this.pl = pl; this.pal = pal; this.em = em; this.ch = em.chan("current");
        cx0 = Math.floorDiv(pl.x, 16); cz0 = Math.floorDiv(pl.z, 16);
        ncx = Math.floorDiv(pl.x1(), 16) - cx0 + 1; ncz = Math.floorDiv(pl.z1(), 16) - cz0 + 1;
        recs = new Rec[ncx * ncz];
        for (int i = 0; i < ncx; i++) for (int j = 0; j < ncz; j++) {
            Rec r = new Rec();
            r.cx = cx0 + i; r.cz = cz0 + j;
            r.x0 = Math.max(pl.x, r.cx * 16); r.z0 = Math.max(pl.z, r.cz * 16);
            r.nx = Math.min(pl.x1(), r.cx * 16 + 15) - r.x0 + 1; r.nz = Math.min(pl.z1(), r.cz * 16 + 15) - r.z0 + 1;
            recs[i * ncz + j] = r;
        }
    }

    public int total() { return recs.length; }
    public int loadedCount() { int n = 0; for (Rec r : recs) if (r.loaded) n++; return n; }

    private Rec recAt(int x, int z) {
        int i = Math.floorDiv(x, 16) - cx0, j = Math.floorDiv(z, 16) - cz0;
        if (i < 0 || j < 0 || i >= ncx || j >= ncz) return null;
        return recs[i * ncz + j];
    }

    // ---------- Events ----------
    /** Called by the block update hook (any time on the client thread); processed in step(). */
    public void blockChanged(int x, int y, int z) {
        if (!pl.contains(x, y, z)) return;
        if (pend.size() > 200000) return;
        pend.add(((x - pl.x) << 16) | ((y - pl.y) << 8) | (z - pl.z)); // coordinates < 256 -> fits
    }

    /** Works for at most ~budgetNanos: new/reloaded chunks, reported changes, slow reconciliation. */
    public void step(WorldView w, long budgetNanos) {
        long end = System.nanoTime() + budgetNanos;
        // 1) detect chunks being (re)loaded / unloaded
        for (Rec r : recs) {
            Object t = w.chunkToken(r.cx, r.cz);
            if (t == null) { if (r.loaded) { r.loaded = false; r.token = null; } continue; }
            if (t != r.token) {
                if (System.nanoTime() > end) continue;   // next tick
                loadChunk(w, r, t);
            }
        }
        // 2) reported block changes
        if (!pend.isEmpty()) {
            double px = w.playerX(), pz = w.playerZ();
            int n = 0;
            for (Iterator<Integer> it = pend.iterator(); it.hasNext() && n < 30000; ) {
                int k = it.next(); it.remove(); n++;
                int rx = (k >> 16) & 255, ry = (k >> 8) & 255, rz = k & 255;
                int x = pl.x + rx, y = pl.y + ry, z = pl.z + rz;
                if (Math.abs(x + 0.5 - px) > Limits.NEAR_EVENT || Math.abs(z + 0.5 - pz) > Limits.NEAR_EVENT) continue;
                Rec r = recAt(x, z);
                if (r == null || !r.loaded) continue;
                cell(w, r, x, y, z, true);
                if (System.nanoTime() > end) break;
            }
        }
        // 3) safety net: every few ticks, fully reconcile one loaded chunk (catches missed events)
        if (System.nanoTime() < end && ++rescanTick >= 3) {
            rescanTick = 0;
            for (int k = 0; k < recs.length; k++) {
                rr = (rr + 1) % recs.length;
                Rec r = recs[rr];
                if (r.loaded && r.token == w.chunkToken(r.cx, r.cz)) { rescan(w, r); break; }
            }
        }
        ch.flush();
    }

    private int idx(Rec r, int dx, int dy, int dz) { return (dy * r.nz + dz) * r.nx + dx; }

    private void ensureArrays(Rec r) {
        if (r.base != null) return;
        int n = r.nx * r.nz * pl.sy;
        r.base = new char[n]; r.sent = new char[n]; java.util.Arrays.fill(r.sent, (char) Rules.DEFAULT);
        r.chg = new BitSet(n);
        if (r.ground == null) r.ground = new int[r.nx * r.nz];
    }

    private void loadChunk(WorldView w, Rec r, Object token) {
        boolean again = r.base != null;
        if (again) r.pers = collectChg(r);       // remember this session's changes before the baseline is recorded again
        ensureArrays(r);
        r.token = token; r.loaded = true;
        if (!r.groundKnown) {
            for (int dz = 0; dz < r.nz; dz++) for (int dx = 0; dx < r.nx; dx++) r.ground[dz * r.nx + dx] = Ground.compute(w, r.x0 + dx, r.z0 + dz);
            r.groundKnown = true;
        }
        ch.ground(r.x0 - pl.x, r.z0 - pl.z, r.nx, r.nz, r.ground, pl.y);
        for (int dy = 0; dy < pl.sy; dy++) {
            int y = pl.y + dy;
            for (int dz = 0; dz < r.nz; dz++) for (int dx = 0; dx < r.nx; dx++) {
                int i = idx(r, dx, dy, dz);
                int C = w.stateAt(r.x0 + dx, y, r.z0 + dz), B = C;
                boolean chg = false;
                if (r.pers != null) { Integer pb = r.pers.get(i); if (pb != null) { B = pb; chg = pb != C; } }
                r.base[i] = (char) B;
                if (chg) r.chg.set(i); else r.chg.clear(i);
                put(r, i, dx, dy, dz, Rules.disp(pal, B, C, chg, y, r.ground[dz * r.nx + dx]));
            }
        }
        dirty = true;
    }

    private Map<Integer, Integer> collectChg(Rec r) {
        Map<Integer, Integer> m = new HashMap<>();
        if (r.chg != null) for (int i = r.chg.nextSetBit(0); i >= 0; i = r.chg.nextSetBit(i + 1)) m.put(i, (int) r.base[i]);
        return m;
    }

    private void put(Rec r, int i, int dx, int dy, int dz, int code) {
        if (r.sent[i] == code) return;
        r.sent[i] = (char) code;
        ch.set(r.x0 + dx - pl.x, dy, r.z0 + dz - pl.z, code);
    }

    /** Re-evaluate one cell. */
    private void cell(WorldView w, Rec r, int x, int y, int z, boolean live) {
        int dx = x - r.x0, dz = z - r.z0, dy = y - pl.y;
        int i = idx(r, dx, dy, dz);
        int C = w.stateAt(x, y, z), B = r.base[i];
        boolean chg = C != B;
        if (chg != r.chg.get(i)) { if (chg) r.chg.set(i); else r.chg.clear(i); dirty = true; }
        put(r, i, dx, dy, dz, Rules.disp(pal, B, C, chg, y, r.ground[dz * r.nx + dx]));
    }

    private void rescan(WorldView w, Rec r) {
        for (int dy = 0; dy < pl.sy; dy++) for (int dz = 0; dz < r.nz; dz++) for (int dx = 0; dx < r.nx; dx++)
            cell(w, r, r.x0 + dx, pl.y + dy, r.z0 + dz, false);
    }

    // ---------- Snapshot for new connections ----------
    public void snapshot() {
        em.raw("{\"t\":\"current.begin\",\"dim\":" + Json.q(pl.dim) + ",\"origin\":[" + pl.x + "," + pl.y + "," + pl.z + "],\"size\":[" + pl.sx + "," + pl.sy + "," + pl.sz + "],\"d\":" + Limits.DEPTH + "}");
        for (Rec r : recs) {
            if (!r.groundKnown) continue;
            ch.ground(r.x0 - pl.x, r.z0 - pl.z, r.nx, r.nz, r.ground, pl.y);
            if (r.sent == null) continue;
            for (int dy = 0; dy < pl.sy; dy++) for (int dz = 0; dz < r.nz; dz++) for (int dx = 0; dx < r.nx; dx++) {
                int c = r.sent[idx(r, dx, dy, dz)];
                if (c != Rules.DEFAULT) ch.set(r.x0 + dx - pl.x, dy, r.z0 + dz - pl.z, c);
            }
            ch.flush();
        }
        ch.flush();
        em.raw("{\"t\":\"current.end\"}");
    }

    // ---------- Saving / loading (only column heights and known changes; no terrain) ----------
    public void save(java.io.DataOutputStream o) throws java.io.IOException {
        List<Rec> use = new ArrayList<>();
        for (Rec r : recs) if (r.groundKnown || (r.chg != null && !r.chg.isEmpty()) || (r.pers != null && !r.pers.isEmpty())) use.add(r);
        // write the palette entries that are needed, by name
        List<String> names = new ArrayList<>(); Map<Integer, Integer> remap = new HashMap<>();
        List<Map<Integer, Integer>> chgs = new ArrayList<>();
        for (Rec r : use) {
            Map<Integer, Integer> m = r.base != null ? collectChg(r) : (r.pers != null ? r.pers : new HashMap<>());
            chgs.add(m);
            for (int pi : m.values()) if (!remap.containsKey(pi)) { remap.put(pi, names.size()); names.add(pal.get(pi)); }
        }
        o.writeInt(names.size());
        for (String s : names) o.writeUTF(s);
        o.writeInt(use.size());
        for (int k = 0; k < use.size(); k++) {
            Rec r = use.get(k);
            o.writeInt(r.cx); o.writeInt(r.cz);
            o.writeBoolean(r.groundKnown);
            if (r.groundKnown) for (int g : r.ground) o.writeInt(g);
            Map<Integer, Integer> m = chgs.get(k);
            o.writeInt(m.size());
            for (Map.Entry<Integer, Integer> e : m.entrySet()) { o.writeInt(e.getKey()); o.writeShort(remap.get(e.getValue())); }
        }
        dirty = false;
    }

    public void load(java.io.DataInputStream in) throws java.io.IOException {
        int np = in.readInt(); if (np < 0 || np > 100000) throw new java.io.IOException("Palette");
        int[] map = new int[np];
        for (int i = 0; i < np; i++) map[i] = pal.intern(in.readUTF());
        int nr = in.readInt(); if (nr < 0 || nr > recs.length + 1) throw new java.io.IOException("Chunks");
        for (int k = 0; k < nr; k++) {
            int cx = in.readInt(), cz = in.readInt();
            int i = cx - cx0, j = cz - cz0;
            Rec r = (i >= 0 && j >= 0 && i < ncx && j < ncz) ? recs[i * ncz + j] : null;
            boolean gk = in.readBoolean();
            int[] g = null;
            if (gk) { int n = (r != null ? r.nx * r.nz : 256); g = new int[n]; for (int q = 0; q < n; q++) g[q] = in.readInt(); }
            int nc = in.readInt(); if (nc < 0 || nc > 8_000_000) throw new java.io.IOException("Changes");
            Map<Integer, Integer> m = new HashMap<>();
            for (int q = 0; q < nc; q++) { int idx = in.readInt(); int pi = in.readUnsignedShort(); if (pi < np) m.put(idx, map[pi]); }
            if (r == null) continue;
            if (gk && g.length == r.nx * r.nz) { r.ground = g; r.groundKnown = true; r.pers = m; }
            else if (!m.isEmpty()) r.pers = m;
        }
    }
}
