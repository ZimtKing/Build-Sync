package de.buildsync.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Connects the WebSocket server, the engine and the preview. Call tick() and blockChanged() on the client thread only. */
public final class Bridge implements LiveServer.Listener {
    private final Path dir;
    private final String modVer, mcVer;
    private final Consumer<String> log;
    private final Palette pal;
    private final Emitter em;
    private final ConcurrentLinkedQueue<Object[]> inbox = new ConcurrentLinkedQueue<>();
    private volatile LiveServer.Conn conn;
    private CurrentEngine eng;
    private String engServer = "", engPlan = "";
    private PreviewJob job;
    private long lastPreview, lastSave, lastStatus;
    private String lastStatusJson = "";
    private boolean paused;

    /** pal MUST be the same palette that the WorldView implementation uses. */
    public Bridge(Path dir, String modVer, String mcVer, Palette pal, Consumer<String> log) {
        this.pal = pal; this.em = new Emitter(pal);
        this.dir = dir; this.modVer = modVer; this.mcVer = mcVer; this.log = log;
    }

    // ---- Listener (server threads) ----
    @Override public void onAuthed(LiveServer.Conn c) { inbox.add(new Object[]{"@auth", c}); }
    @Override public void onMessage(LiveServer.Conn c, Map<String, Object> m) { if (inbox.size() < 1000) inbox.add(new Object[]{"@msg", c, m}); }
    @Override public void onClosed(LiveServer.Conn c) { inbox.add(new Object[]{"@close", c}); }

    public boolean active() { return eng != null; }
    public CurrentEngine engine() { return eng; }
    public String activeDim() { return eng == null ? null : eng.pl.dim; }

    /** Block update hook (client thread). */
    public void blockChanged(String dim, int x, int y, int z) { CurrentEngine e = eng; if (e != null && e.pl.dim.equals(dim)) e.blockChanged(x, y, z); }

    /** w == null if no world is open right now. */
    @SuppressWarnings("unchecked")
    public void tick(WorldView w, String dim, String serverKey) {
        Object[] it;
        while ((it = inbox.poll()) != null) {
            String k = (String) it[0];
            LiveServer.Conn c = (LiveServer.Conn) it[1];
            if (k.equals("@auth")) {
                conn = c; em.attach(c::send); job = null;
                if (eng != null) eng.snapshot();
            } else if (k.equals("@close")) {
                if (conn == c) { conn = null; em.detach(); job = null; }
            } else if (c == conn) handle(w, dim, serverKey, (Map<String, Object>) it[2]);
        }
        long now = System.currentTimeMillis();
        if (eng != null) {
            boolean ok = w != null && dim != null && dim.equals(eng.pl.dim) && serverKey.equals(engServer);
            paused = !ok;
            if (ok) eng.step(w, 3_000_000L);
            if (eng.dirty && now - lastSave > 60_000) save();
        }
        if (job != null && w != null && job.step(w, 3_000_000L)) job = null;
        if (conn != null && now - lastStatus > 1000) {
            String s = status(w, dim);
            if (!s.equals(lastStatusJson) || now - lastStatus > 3000) { em.raw(s); lastStatusJson = s; lastStatus = now; }
            else lastStatus = now;
        }
    }

    private String status(WorldView w, String dim) {
        StringBuilder b = new StringBuilder("{\"t\":\"status\",\"mod\":").append(Json.q(modVer)).append(",\"mc\":").append(Json.q(mcVer));
        b.append(",\"inWorld\":").append(w != null);
        if (w != null) b.append(",\"dim\":").append(Json.q(dim)).append(",\"player\":[").append((int) Math.floor(w.playerX())).append(',').append((int) Math.floor(w.playerY())).append(',').append((int) Math.floor(w.playerZ())).append(']');
        b.append(",\"active\":").append(eng != null).append(",\"paused\":").append(paused);
        if (eng != null) {
            Placement p = eng.pl;
            b.append(",\"place\":{\"dim\":").append(Json.q(p.dim)).append(",\"x\":").append(p.x).append(",\"y\":").append(p.y).append(",\"z\":").append(p.z)
             .append(",\"sx\":").append(p.sx).append(",\"sy\":").append(p.sy).append(",\"sz\":").append(p.sz).append("},\"known\":").append(eng.loadedCount()).append(",\"total\":").append(eng.total());
        }
        return b.append('}').toString();
    }

    private void err(String t, String why) { em.raw("{\"t\":" + Json.q(t) + ",\"reason\":" + Json.q(why) + "}"); }

    private void handle(WorldView w, String dim, String serverKey, Map<String, Object> m) {
        String t = Json.str(m, "t");
        if (t == null) return;
        switch (t) {
            case "ping": em.raw("{\"t\":\"pong\"}"); break;
            case "snapshot": if (eng != null) eng.snapshot(); break;
            case "stop": save(); eng = null; em.raw("{\"t\":\"stopped\"}"); break;
            case "forget":
                if (eng != null) { try { Files.deleteIfExists(file(engServer, eng.pl, engPlan)); } catch (IOException ignored) {} eng = null; }
                em.raw("{\"t\":\"stopped\"}"); break;
            case "place": {
                if (w == null || dim == null) { err("place.err", "You are not in a world."); break; }
                Placement p = parsePlacement(m, dim);
                if (p == null) { err("place.err", "Invalid values."); break; }
                String e = p.check(w);
                if (e != null) { err("place.err", e); break; }
                if (eng != null) save();
                String plan = Json.str(m, "key"); if (plan == null || plan.length() > 200) plan = "";
                eng = new CurrentEngine(p, pal, em); engServer = serverKey; engPlan = plan; paused = false;
                Path f = file(serverKey, p, plan);
                if (Files.exists(f)) try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(f)))) { eng.load(in); } catch (Exception ex) { log.accept("Saved data unreadable: " + ex); }
                eng.snapshot();
                em.raw("{\"t\":\"place.ok\"}");
                break;
            }
            case "preview": {
                long now = System.currentTimeMillis();
                if (w == null) { err("pv.err", "You are not in a world."); break; }
                if (now - lastPreview < Limits.PREVIEW_MIN_INTERVAL_MS) break;
                lastPreview = now;
                Placement p = parsePlacement(m, dim);
                if (p == null) { err("pv.err", "Invalid values."); break; }
                String e = p.check(w);
                if (e != null) { err("pv.err", e); break; }
                job = new PreviewJob((int) Json.lng(m, "id", 0), p, (int) Math.max(0, Math.min(Limits.PREVIEW_MARGIN_MAX, Json.lng(m, "margin", 16))), w, pal, em);
                break;
            }
            default: break;
        }
    }

    private static Placement parsePlacement(Map<String, Object> m, String dim) {
        if (!Json.has(m, "x") || !Json.has(m, "y") || !Json.has(m, "z") || !Json.has(m, "sx") || !Json.has(m, "sy") || !Json.has(m, "sz")) return null;
        long x = Json.lng(m, "x", 0), y = Json.lng(m, "y", 0), z = Json.lng(m, "z", 0), sx = Json.lng(m, "sx", 0), sy = Json.lng(m, "sy", 0), sz = Json.lng(m, "sz", 0);
        if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 100_000) return null;
        if (Placement.checkSize(sx, sy, sz) != null) return null;
        return new Placement(dim, (int) x, (int) y, (int) z, (int) sx, (int) sy, (int) sz);
    }

    // ---- Saving ----
    private Path file(String serverKey, Placement p, String plan) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest((serverKey + "|" + p.key() + "|" + plan).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x));
            return dir.resolve("projects").resolve(b + ".bsb");
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public void save() {
        CurrentEngine e = eng; if (e == null) return;
        lastSave = System.currentTimeMillis();
        try {
            Path f = file(engServer, e.pl, engPlan);
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            try (DataOutputStream o = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp)))) { e.save(o); }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) { log.accept("Saving failed: " + ex); }
    }

    /** Leaving a world: save the state and end the session (the next world has a different baseline). */
    public void leaveWorld() { save(); eng = null; job = null; paused = false; if (conn != null) em.raw("{\"t\":\"stopped\"}"); }
}
