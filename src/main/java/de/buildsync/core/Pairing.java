package de.buildsync.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

/** Pairing code (12 characters, Crockford base32, 60 bits) – stored only in config/buildsync/pairing.txt. */
public final class Pairing {
    private Pairing() {}
    private static final String ALPHA = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    public static String generate() {
        SecureRandom r = new SecureRandom();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i++) { if (i > 0 && i % 4 == 0) b.append('-'); b.append(ALPHA.charAt(r.nextInt(32))); }
        return b.toString();
    }

    public static String load(Path dir) throws IOException {
        Path f = dir.resolve("pairing.txt");
        if (Files.exists(f)) {
            String s = Files.readString(f, StandardCharsets.UTF_8).trim();
            if (s.matches("[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}")) return s;
        }
        return renew(dir);
    }

    public static String renew(Path dir) throws IOException {
        Files.createDirectories(dir);
        String c = generate();
        Files.writeString(dir.resolve("pairing.txt"), c + "\n", StandardCharsets.UTF_8);
        return c;
    }
}
