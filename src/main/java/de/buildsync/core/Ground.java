package de.buildsync.core;

/** Determines the "ground" height of each column: the topmost block that is not transparent (so roofs, hatches etc. count as ground). */
public final class Ground {
    private Ground() {}

    public static int compute(WorldView w, int x, int z) {
        Palette pal = w.palette();
        int minY = w.minY(), top = minY + w.height() - 1;
        for (int y = top; y >= minY; y--) {
            int f = pal.flags(w.stateAt(x, y, z));
            if ((f & Classify.TRANSPARENT) != 0) continue;
            if ((f & Classify.LOGLIKE) != 0) {
                // tree trunk: only "above the ground" if it stands gaplessly on natural terrain – otherwise it is the ground itself (camouflage hatch)
                int yy = y;
                while (yy >= minY && (pal.flags(w.stateAt(x, yy, z)) & Classify.LOGLIKE) != 0) yy--;
                if (yy >= minY && (pal.flags(w.stateAt(x, yy, z)) & Classify.NATURAL) != 0) return yy;
                return y;
            }
            return y;
        }
        return minY - 1;
    }
}
