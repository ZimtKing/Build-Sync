package de.buildsync.core;

/**
 * Fixed safety limits. Deliberately constants and NOT settings: whoever edits the configuration file
 * must not gain any additional insight by doing so.
 */
public final class Limits {
    private Limits() {}
    /** Largest edge length of a plan cuboid (per axis). */
    public static final int MAX_DIM = 256;
    public static final long MAX_VOLUME = 4_000_000L;
    /** This many layers directly below the surface may be shown as real blocks (natural terrain only): y in (g-DEPTH, g]. Deeper = fill. */
    public static final int DEPTH = 2;
    /** When placing, the plan cuboid must be this close to the player (horizontally, measured from the cuboid's edge). */
    public static final int NEAR_PLACE = 128;
    /** Block changes farther away from the player than this are not reported. */
    public static final int NEAR_EVENT = 192;
    public static final int PREVIEW_MARGIN_MAX = 24;
    public static final int PREVIEW_MAX_COLS = 90_000;
    public static final long PREVIEW_MIN_INTERVAL_MS = 250;
    /** Preview: this many layers below/above the plan are included. */
    public static final int PREVIEW_BELOW = 6, PREVIEW_ABOVE = 12;
    public static final int MAX_MSG_IN = 16 * 1024;
    public static final int SET_BATCH = 3000;
}
