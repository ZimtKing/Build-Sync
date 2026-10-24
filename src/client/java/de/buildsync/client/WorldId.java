package de.buildsync.client;

import net.minecraft.client.Minecraft;

/**
 * Identifier of the current world/server (so saved changes never show up in a different world).
 * Deliberately uses reflection with a fallback: if Mojang renames something, the identifier gets coarser but nothing breaks.
 */
final class WorldId {
    private WorldId() {}

    static String of(Minecraft mc) {
        try {
            Object sd = Minecraft.class.getMethod("getCurrentServer").invoke(mc);
            if (sd != null) {
                Object ip = sd.getClass().getField("ip").get(sd);
                if (ip != null) return "mp:" + ip.toString().toLowerCase();
            }
        } catch (Throwable ignored) { }
        try {
            Object srv = mc.getSingleplayerServer();
            if (srv != null) {
                Object wd = srv.getClass().getMethod("getWorldData").invoke(srv);
                Object name = wd.getClass().getMethod("getLevelName").invoke(wd);
                if (name != null) return "sp:" + name;
            }
        } catch (Throwable ignored) { }
        return mc.getSingleplayerServer() != null ? "sp:unbekannt" : "mp:unbekannt";
    }
}
