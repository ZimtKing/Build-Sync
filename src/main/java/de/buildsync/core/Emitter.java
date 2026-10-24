package de.buildsync.core;

/** Builds protocol messages (the palette is always sent before the blocks that use it). Use on the client thread only. */
public final class Emitter {
    public interface Out { void send(String json); }

    private final Palette pal;
    private Out out;
    private final java.util.BitSet palSent = new java.util.BitSet();
    private final java.util.List<Integer> palPend = new java.util.ArrayList<>();

    public Emitter(Palette pal) { this.pal = pal; }

    /** New connection: the palette starts from scratch. */
    public void attach(Out o) { out = o; palSent.clear(); palPend.clear(); }
    public void detach() { out = null; }
    public boolean on() { return out != null; }
    public void raw(String json) { Out o = out; if (o != null) o.send(json); }

    /** Only palette entries that actually occur in a sent cell are transmitted (otherwise the list alone would reveal which blocks exist in the area). */
    private void flushPal() {
        if (out == null || palPend.isEmpty()) return;
        StringBuilder i = new StringBuilder("{\"t\":\"pal\",\"i\":["), l = new StringBuilder("],\"list\":[");
        for (int k = 0; k < palPend.size(); k++) { if (k > 0) { i.append(','); l.append(','); } i.append(palPend.get(k)); l.append(Json.q(pal.get(palPend.get(k)))); }
        palPend.clear();
        out.send(i.append(l).append("]}").toString());
    }
    private void need(int pi) { if (!palSent.get(pi)) { palSent.set(pi); palPend.add(pi); } }

    public Chan chan(String prefix) { return new Chan(prefix); }

    public final class Chan {
        private final String prefix;
        private final StringBuilder sb = new StringBuilder();
        private int cnt;
        Chan(String prefix) { this.prefix = prefix; }

        /** One cell (relative to the cuboid). See Rules for the code. */
        public void set(int rx, int ry, int rz, int code) {
            if (out == null) return;
            int v, kind = 0;
            if (code == Rules.DEFAULT) v = -1;
            else if (code == Rules.FILL) v = -2;
            else { v = Rules.paletteOf(code); kind = Rules.kindOf(code); need(v); }
            if (cnt > 0) sb.append(',');
            sb.append(rx).append(',').append(ry).append(',').append(rz).append(',').append(v).append(',').append(kind);
            if (++cnt >= Limits.SET_BATCH) flush();
        }

        public void flush() {
            if (out == null) { sb.setLength(0); cnt = 0; return; }
            if (cnt == 0) return;
            flushPal();
            out.send("{\"t\":\"" + prefix + ".set\",\"b\":[" + sb + "]}");
            sb.setLength(0); cnt = 0;
        }

        /** Ground heights of a rectangle (relative to the cuboid; y relative to the cuboid's bottom edge). */
        public void ground(int rx, int rz, int w, int d, int[] g, int oy) {
            if (out == null) return;
            StringBuilder b = new StringBuilder("{\"t\":\"").append(prefix).append(".ground\",\"x\":").append(rx).append(",\"z\":").append(rz)
                .append(",\"w\":").append(w).append(",\"d\":").append(d).append(",\"g\":[");
            for (int i = 0; i < g.length; i++) { if (i > 0) b.append(','); b.append(g[i] - oy); }
            out.send(b.append("]}").toString());
        }
    }
}
