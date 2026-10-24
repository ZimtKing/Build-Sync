package de.buildsync.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What the tool knows from the messages – tests check ONLY this view (the tool never saw anything else). */
public class Tool implements Emitter.Out {
    final List<String> pal = new ArrayList<>();
    final Map<Long, int[]> cells = new HashMap<>();      // key(x,y,z) -> {v,kind}
    final Map<Long, Integer> ground = new HashMap<>();   // key(x,z) -> relative g
    final List<String> raw = new ArrayList<>();
    int[] size = new int[3]; int depth;
    int sets;

    static long k3(int x, int y, int z) { return ((long) x << 40) ^ ((long) y << 20) ^ z; }
    static long k2(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    @SuppressWarnings("unchecked")
    @Override public void send(String json) {
        raw.add(json);
        Map<String, Object> m = Json.parseObject(json);
        String t = Json.str(m, "t");
        if (t.equals("pal")) { List<Object> ii = (List<Object>) m.get("i"), l = (List<Object>) m.get("list"); for (int k = 0; k < ii.size(); k++) { int i = ((Number) ii.get(k)).intValue(); while (pal.size() <= i) pal.add(null); pal.set(i, (String) l.get(k)); } }
        else if (t.equals("current.begin")) { cells.clear(); ground.clear(); List<Object> s = (List<Object>) m.get("size"); for (int i = 0; i < 3; i++) size[i] = ((Number) s.get(i)).intValue(); depth = (int) Json.lng(m, "d", 0); }
        else if (t.equals("current.set") || t.equals("pv.set")) { List<Object> b = (List<Object>) m.get("b"); for (int i = 0; i < b.size(); i += 5) { cells.put(k3(n(b, i), n(b, i + 1), n(b, i + 2)), new int[]{n(b, i + 3), n(b, i + 4)}); sets++; } }
        else if (t.equals("current.ground") || t.equals("pv.ground")) { int x = (int) Json.lng(m, "x", 0), z = (int) Json.lng(m, "z", 0), w = (int) Json.lng(m, "w", 0); List<Object> g = (List<Object>) m.get("g"); for (int i = 0; i < g.size(); i++) ground.put(k2(x + i % w, z + i / w), n(g, i)); }
    }
    static int n(List<Object> l, int i) { return ((Number) l.get(i)).intValue(); }

    /** All real block names the tool has ever seen (palette, used entries only). */
    java.util.Set<String> seenNames() {
        java.util.Set<String> r = new java.util.HashSet<>();
        for (int[] c : cells.values()) if (c[0] >= 0) r.add(pal.get(c[0]));
        return r;
    }
    String at(int x, int y, int z) { int[] c = cells.get(k3(x, y, z)); return c == null ? null : (c[0] >= 0 ? pal.get(c[0]) : c[0] == -2 ? "FILL" : "DEFAULT"); }
    int kind(int x, int y, int z) { int[] c = cells.get(k3(x, y, z)); return c == null ? -1 : c[1]; }
}
