package de.buildsync.core;

/** Read access to the world as known on the client – implemented by the Minecraft part, simulated in tests. */
public interface WorldView {
    Palette palette();
    int minY();
    int height();
    /** Token of the loaded chunk (changes when it is reloaded) or null if it is not loaded. */
    Object chunkToken(int cx, int cz);
    /** Palette index of the current block; only call for positions in loaded chunks. */
    int stateAt(int x, int y, int z);
    double playerX();
    double playerZ();
    double playerY();
}
