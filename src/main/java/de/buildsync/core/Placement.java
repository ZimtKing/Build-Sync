package de.buildsync.core;

/** A cuboid in the world (corner with the smallest coordinates + size) in which the plan stands. */
public final class Placement {
    public final String dim;
    public final int x, y, z, sx, sy, sz;

    public Placement(String dim, int x, int y, int z, int sx, int sy, int sz) {
        this.dim = dim; this.x = x; this.y = y; this.z = z; this.sx = sx; this.sy = sy; this.sz = sz;
    }

    public int x1() { return x + sx - 1; }
    public int y1() { return y + sy - 1; }
    public int z1() { return z + sz - 1; }
    public boolean contains(int X, int Y, int Z) { return X >= x && X <= x1() && Y >= y && Y <= y1() && Z >= z && Z <= z1(); }
    public String key() { return dim + "@" + x + "," + y + "," + z + "+" + sx + "x" + sy + "x" + sz; }

    /** Rough size/height check (without player distance). Returns an error message or null. */
    public static String checkSize(long sx, long sy, long sz) {
        if (sx < 1 || sy < 1 || sz < 1) return "Invalid size";
        if (sx > Limits.MAX_DIM || sy > Limits.MAX_DIM || sz > Limits.MAX_DIM) return "Plan too large (max. " + Limits.MAX_DIM + " blocks per axis)";
        if (sx * sy * sz > Limits.MAX_VOLUME) return "Plan volume too large";
        return null;
    }

    /** Checks the height range and the distance to the player (horizontal, from the cuboid's edge). */
    public String check(WorldView w) {
        String e = checkSize(sx, sy, sz);
        if (e != null) return e;
        if (y < w.minY() || y1() > w.minY() + w.height() - 1) return "Height range is outside the world (Y " + w.minY() + " to " + (w.minY() + w.height() - 1) + ")";
        double dx = Math.max(Math.max(x - w.playerX(), w.playerX() - (x1() + 1)), 0);
        double dz = Math.max(Math.max(z - w.playerZ(), w.playerZ() - (z1() + 1)), 0);
        if (Math.max(dx, dz) > Limits.NEAR_PLACE) return "Too far from the player – stand near the build site (max. " + Limits.NEAR_PLACE + " blocks)";
        return null;
    }
}
