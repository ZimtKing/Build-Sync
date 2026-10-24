package de.buildsync.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Directory of all block states of a session (index 0 = air). Use on the client thread only. */
public final class Palette {
    private final List<String> str = new ArrayList<>();
    private int[] flags = new int[256];
    private final Map<String, Integer> idx = new HashMap<>();

    public Palette() { intern("minecraft:air"); }

    public int intern(String s) {
        Integer i = idx.get(s);
        if (i != null) return i;
        int n = str.size();
        if (n >= 30000) return 0; // guard (fits in 16 bits); practically never reached
        str.add(s);
        if (n >= flags.length) flags = java.util.Arrays.copyOf(flags, flags.length * 2);
        flags[n] = Classify.flags(s);
        idx.put(s, n);
        return n;
    }

    public int size() { return str.size(); }
    public String get(int i) { return str.get(i); }
    public int flags(int i) { return flags[i]; }
    public boolean isAir(int i) { return (flags[i] & Classify.AIR) != 0; }
}
