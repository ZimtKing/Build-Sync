package de.buildsync.core;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Two-step confirmation for LAN mode: "/buildsync lan on" only asks, "/buildsync lan confirm" within {@link #WINDOW_MS} switches it on.
 * Nothing here is persisted: after a game restart LAN mode is always off.
 */
public final class LanGate {
    public static final long WINDOW_MS = 30_000;
    /** The text shown before LAN mode can be confirmed (one line per entry). */
    public static final String[] WARNING = {
        "WARNING: LAN mode opens BuildSync to your home network.",
        "- Anyone on your Wi-Fi/LAN who knows the pairing code can read the filtered build data.",
        "- The connection is NOT encrypted. Use it only on a network you trust (not at a cafe, school or on public Wi-Fi).",
        "- It is never opened to the internet, and it switches off when you restart the game.",
        "To continue, type /buildsync lan confirm within 30 seconds. To cancel, do nothing."
    };

    private long requestedAt = -1;

    /** Remember that the warning was shown now. */
    public synchronized void request(long now) { requestedAt = now; }

    /** True once if a request is pending and not older than the window; the request is used up either way. */
    public synchronized boolean confirm(long now) {
        boolean ok = requestedAt >= 0 && now - requestedAt <= WINDOW_MS && now >= requestedAt;
        requestedAt = -1;
        return ok;
    }

    public synchronized void cancel() { requestedAt = -1; }

    /** Private IPv4 addresses of this computer (what a phone in the same network would type). */
    public static List<String> localAddresses() {
        List<String> r = new ArrayList<>();
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e != null && e.hasMoreElements(); ) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) {
                    InetAddress x = a.nextElement();
                    if (x instanceof Inet4Address && x.isSiteLocalAddress()) r.add(x.getHostAddress());
                }
            }
        } catch (Exception ignored) { }
        return r;
    }
}
