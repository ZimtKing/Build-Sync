package de.buildsync.core;

/**
 * The visibility rules. All decisions about what the tool gets to see are made here and only here –
 * the tool itself is never trusted.
 *
 * Display codes (also used in the mod's memory as the "already sent" marker):
 *   0 = nothing sent yet (internal only), 1 = DEFAULT (the tool derives it itself: air or fill), 2 = FILL (fill enforced),
 *   >= 3 : real block, code = 3 + 2*paletteIndex + kind   (kind 0 = changed by a player inside the plan, kind 1 = unchanged terrain).
 */
public final class Rules {
    private Rules() {}
    public static final int UNSENT = 0, DEFAULT = 1, FILL = 2;

    public static int real(int pi, int kind) { return 3 + 2 * pi + kind; }
    public static boolean isReal(int code) { return code >= 3; }
    public static int paletteOf(int code) { return (code - 3) >> 1; }
    public static int kindOf(int code) { return (code - 3) & 1; }

    /**
     * @param B   state when observation started (baseline)
     * @param C   current state
     * @param chg whether the block has changed since the baseline (was observed live)
     * @param y   world Y of the cell
     * @param g   ground height of the column (see Ground)
     */
    public static int disp(Palette pal, int B, int C, boolean chg, int y, int g) {
        if (chg) return real(C, 0);                  // changed inside the plan: always real (also cellars; air = dug-out cavity)
        if (y > g) return pal.isAir(C) ? DEFAULT : real(C, 1);   // above the ground: visible (trees, plants, other players' builds …)
        if (y == g && (pal.flags(C) & Classify.HATCH) == 0) return real(C, 1);               // topmost block of the column: visible from the air anyway (roofs, floors, hatches) – may be shown as real
        if (y > g - Limits.DEPTH)                    // directly below the surface: only harmless terrain is real, everything else becomes fill
            return (pal.flags(C) & Classify.NATURAL) != 0 ? real(C, 1) : FILL;
        return DEFAULT;                               // deeper: the tool fills it in itself – caves/ores/bases stay invisible
    }
}
