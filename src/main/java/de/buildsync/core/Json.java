package de.buildsync.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON parser/writer without dependencies (only what the protocol needs). */
public final class Json {
    private Json() {}

    /** Parses a JSON document. Objects -> Map, arrays -> List, numbers -> Long/Double, otherwise String/Boolean/null. */
    public static Object parse(String s) {
        P p = new P(s);
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.i != s.length()) throw new IllegalArgumentException("Trailing characters");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String s) {
        Object o = parse(s);
        if (!(o instanceof Map)) throw new IllegalArgumentException("Object expected");
        return (Map<String, Object>) o;
    }

    private static final class P {
        final String s;
        int i;
        P(String s) { this.s = s; }
        void ws() { while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++; }
        IllegalArgumentException err(String m) { return new IllegalArgumentException(m + " @" + i); }
        Object value(int depth) {
            if (depth > 32) throw err("too deep");
            if (i >= s.length()) throw err("Ende");
            char c = s.charAt(i);
            if (c == '{') return obj(depth);
            if (c == '[') return arr(depth);
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return num();
        }
        Map<String, Object> obj(int depth) {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            for (;;) {
                ws();
                if (i >= s.length() || s.charAt(i) != '"') throw err("Key expected");
                String k = str(); ws();
                if (i >= s.length() || s.charAt(i) != ':') throw err(": erwartet");
                i++; ws();
                m.put(k, value(depth + 1)); ws();
                if (i >= s.length()) throw err("Ende");
                char c = s.charAt(i++);
                if (c == '}') return m;
                if (c != ',') throw err(", erwartet");
                if (m.size() > 64) throw err("too many fields");
            }
        }
        List<Object> arr(int depth) {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            for (;;) {
                ws();
                l.add(value(depth + 1)); ws();
                if (i >= s.length()) throw err("Ende");
                char c = s.charAt(i++);
                if (c == ']') return l;
                if (c != ',') throw err(", erwartet");
                if (l.size() > 200000) throw err("too many elements");
            }
        }
        String str() {
            StringBuilder b = new StringBuilder();
            i++;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    if (i >= s.length()) break;
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': b.append('\n'); break;
                        case 't': b.append('\t'); break;
                        case 'r': b.append('\r'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw err("\\u");
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: b.append(e);
                    }
                } else b.append(c);
                if (b.length() > 4096) throw err("String too long");
            }
            throw err("String not terminated");
        }
        Object num() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw err("Value expected");
            String t = s.substring(st, i);
            try {
                if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) return Long.parseLong(t);
                return Double.parseDouble(t);
            } catch (NumberFormatException e) { throw err("Zahl"); }
        }
    }

    /** Escapes a string for JSON (including the quotes). */
    public static String q(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2);
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    // ---- Access helpers ----
    public static String str(Map<String, Object> m, String k) { Object v = m.get(k); return v instanceof String ? (String) v : null; }
    public static long lng(Map<String, Object> m, String k, long def) { Object v = m.get(k); return v instanceof Number ? ((Number) v).longValue() : def; }
    public static boolean has(Map<String, Object> m, String k) { return m.get(k) instanceof Number; }
}
