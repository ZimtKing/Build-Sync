package de.buildsync.core;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Tiny WebSocket server (RFC 6455) on 127.0.0.1 only. No dependencies.
 * Protection: loopback only · host check (against DNS rebinding) · origin check (web pages from other origins are rejected) ·
 * pairing code with lockout after failed attempts · limited message size · one active connection.
 */
public final class LiveServer implements AutoCloseable {
    public interface Listener {
        void onAuthed(Conn c);
        void onMessage(Conn c, Map<String, Object> msg);
        void onClosed(Conn c);
    }

    private final int basePort, tries;
    private final byte[] code;
    private final Set<String> origins;
    private final Listener listener;
    private final Consumer<String> log;
    private ServerSocket server;
    private volatile boolean closed;
    private volatile Conn active;
    private int fails;
    private long lockUntil, lockLen = 30_000;
    private int port = -1;

    public LiveServer(int basePort, int tries, String code, Set<String> allowedOrigins, Listener l, Consumer<String> log) {
        this.basePort = basePort; this.tries = tries; this.code = norm(code).getBytes(StandardCharsets.UTF_8);
        this.origins = allowedOrigins; this.listener = l; this.log = log;
    }

    static String norm(String s) { return s == null ? "" : s.replace("-", "").replace(" ", "").trim().toUpperCase(Locale.ROOT); }

    public int port() { return port; }

    public int start() throws IOException {
        IOException last = null;
        for (int i = 0; i < tries; i++) {
            try {
                ServerSocket s = new ServerSocket();
                s.setReuseAddress(false);
                s.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), basePort + i), 8);
                server = s; port = basePort + i;
                Thread t = new Thread(this::acceptLoop, "buildsync-accept"); t.setDaemon(true); t.start();
                return port;
            } catch (IOException e) { last = e; }
        }
        throw last != null ? last : new IOException("no free port");
    }

    @Override public void close() {
        closed = true;
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        Conn c = active; if (c != null) c.close();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket s = server.accept();
                Thread t = new Thread(() -> serve(s), "buildsync-conn"); t.setDaemon(true); t.start();
            } catch (IOException e) { if (!closed) log.accept("accept: " + e.getMessage()); }
        }
    }

    // ------------------------------------------------------------------
    public final class Conn {
        private final Socket sock;
        private final OutputStream out;
        private final LinkedBlockingQueue<byte[]> q = new LinkedBlockingQueue<>();
        private volatile boolean authed, dead;
        private long queued;
        Conn(Socket s, OutputStream o) { sock = s; out = o; }
        public boolean authed() { return authed; }
        public boolean isOpen() { return !dead; }

        /** Thread-safe; text frame. If the queue gets too long (stalled receiver), the connection is dropped. */
        public void send(String text) {
            if (dead) return;
            byte[] f = frame(0x1, text.getBytes(StandardCharsets.UTF_8));
            synchronized (this) {
                queued += f.length;
                if (queued > 96L * 1024 * 1024) { close(); return; }
            }
            q.add(f);
        }
        void sendNow(int op, byte[] p) throws IOException { synchronized (out) { out.write(frame(op, p)); out.flush(); } }
        public void close() {
            if (dead) return; dead = true;
            q.add(new byte[0]);
            try { sock.close(); } catch (IOException ignored) {}
        }
        void writer() {
            try {
                for (;;) {
                    byte[] f = q.take();
                    if (dead) return;
                    synchronized (this) { queued -= f.length; }
                    synchronized (out) { out.write(f); out.flush(); }
                }
            } catch (Exception e) { close(); }
        }
    }

    static byte[] frame(int op, byte[] p) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(p.length + 10);
        b.write(0x80 | op);
        if (p.length < 126) b.write(p.length);
        else if (p.length < 65536) { b.write(126); b.write(p.length >> 8); b.write(p.length); }
        else { b.write(127); for (int i = 7; i >= 0; i--) b.write((int) (((long) p.length >> (8 * i)) & 255)); }
        b.write(p, 0, p.length);
        return b.toByteArray();
    }

    private void serve(Socket s) {
        Conn c = null;
        try {
            s.setTcpNoDelay(true);
            s.setSoTimeout(5000);
            InputStream in = new BufferedInputStream(s.getInputStream(), 8192);
            OutputStream out = s.getOutputStream();
            // ---- Read the HTTP request ----
            String head = readHead(in);
            if (head == null) { s.close(); return; }
            String[] lines = head.split("\r\n");
            String[] rl = lines[0].split(" ");
            Map<String, String> h = new java.util.HashMap<>();
            for (int i = 1; i < lines.length; i++) { int k = lines[i].indexOf(':'); if (k > 0) h.put(lines[i].substring(0, k).trim().toLowerCase(Locale.ROOT), lines[i].substring(k + 1).trim()); }
            String host = h.getOrDefault("host", "");
            boolean hostOk = host.equals("127.0.0.1:" + port) || host.equals("localhost:" + port) || host.equals("[::1]:" + port);
            String origin = h.get("origin");
            boolean originOk = origin == null || origin.equals("null") || origin.startsWith("file:") || origins.contains(origin);
            boolean ws = rl.length >= 2 && rl[0].equals("GET") && "websocket".equalsIgnoreCase(h.getOrDefault("upgrade", "")) && h.containsKey("sec-websocket-key");
            if (!hostOk || !originOk || !ws) {
                String body = !ws ? "BuildSync: WebSocket only." : "Not allowed.";
                out.write(("HTTP/1.1 " + (!ws ? "426 Upgrade Required" : "403 Forbidden") + "\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\nConnection: close\r\n\r\n" + body).getBytes(StandardCharsets.UTF_8));
                out.flush(); s.close();
                if (!originOk) log.accept("rejected: foreign origin " + origin);
                return;
            }
            String acc = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((h.get("sec-websocket-key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + acc + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            c = new Conn(s, out);
            Thread w = new Thread(c::writer, "buildsync-write"); w.setDaemon(true); w.start();
            c.send("{\"t\":\"hello\",\"v\":1,\"auth\":true}");
            DataInputStream din = new DataInputStream(in);
            ByteArrayOutputStream msg = new ByteArrayOutputStream();
            boolean first = true;
            while (!c.dead) {
                int b0, b1;
                try { b0 = din.read(); } catch (SocketTimeoutException e) {
                    if (!c.authed) break;
                    break; // no message (not even a ping) for 40 s -> disconnect
                }
                if (b0 < 0) break;
                b1 = din.read();
                if (b1 < 0) break;
                boolean fin = (b0 & 0x80) != 0; int op = b0 & 15;
                boolean mask = (b1 & 0x80) != 0; long len = b1 & 0x7f;
                if (!mask) break; // clients must mask their frames
                if (len == 126) len = din.readUnsignedShort();
                else if (len == 127) len = din.readLong();
                if (len < 0 || len > Limits.MAX_MSG_IN) break;
                byte[] mk = new byte[4]; din.readFully(mk);
                byte[] p = new byte[(int) len]; din.readFully(p);
                for (int i = 0; i < p.length; i++) p[i] ^= mk[i & 3];
                if (op == 0x8) { try { c.sendNow(0x8, new byte[0]); } catch (IOException ignored) {} break; }
                if (op == 0x9) { c.sendNow(0xA, p); continue; }
                if (op == 0xA) continue;
                if (op == 0x1 || op == 0x0) {
                    msg.write(p);
                    if (msg.size() > Limits.MAX_MSG_IN) break;
                    if (!fin) continue;
                    String text = new String(msg.toByteArray(), StandardCharsets.UTF_8); msg.reset();
                    Map<String, Object> m;
                    try { m = Json.parseObject(text); } catch (RuntimeException e) { break; }
                    if (!c.authed) {
                        if (!"auth".equals(Json.str(m, "t"))) break;
                        if (!tryAuth(c, Json.str(m, "code"))) break;
                        s.setSoTimeout(40_000);
                        Conn old = active; active = c;
                        if (old != null && old != c) old.close();
                        c.authed = true; first = false;
                        c.send("{\"t\":\"auth.ok\"}");
                        listener.onAuthed(c);
                    } else listener.onMessage(c, m);
                } else break; // binary etc. not supported
            }
        } catch (Exception e) {
            // connection error: end quietly
        } finally {
            if (c != null) { c.close(); if (active == c) active = null; if (c.authed) listener.onClosed(c); }
            else try { s.close(); } catch (IOException ignored) {}
        }
    }

    private boolean tryAuth(Conn c, String given) {
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (now < lockUntil) { c.send("{\"t\":\"auth.err\",\"reason\":\"locked\",\"retryMs\":" + (lockUntil - now) + "}"); pause(); return false; }
            boolean ok = MessageDigest.isEqual(norm(given).getBytes(StandardCharsets.UTF_8), code);
            if (ok) { fails = 0; lockLen = 30_000; return true; }
            fails++;
            long retry = 0;
            if (fails >= 5) { lockUntil = now + lockLen; retry = lockLen; lockLen = Math.min(lockLen * 2, 600_000); fails = 0; log.accept("Too many wrong pairing codes – locked for " + retry / 1000 + " s"); }
            c.send("{\"t\":\"auth.err\",\"reason\":\"wrong code\",\"retryMs\":" + retry + "}");
            pause();
            return false;
        }
    }

    private static void pause() { try { Thread.sleep(400); } catch (InterruptedException ignored) {} }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int m = 0;
        while (b.size() < 8192) {
            int c = in.read();
            if (c < 0) return null;
            b.write(c);
            m = (c == '\r' || c == '\n') ? m + 1 : 0;
            if (m == 4) return new String(b.toByteArray(), StandardCharsets.ISO_8859_1).trim();
        }
        return null;
    }
}
