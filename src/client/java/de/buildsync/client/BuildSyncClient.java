/*
 * BuildSync v0.2.1 – Fabric mod (client side) for Minecraft 26.2
 * Author: ZimtKing · Date: 2026-10-23
 */
package de.buildsync.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;

import de.buildsync.core.Bridge;
import de.buildsync.core.LiveServer;
import de.buildsync.core.Pairing;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public final class BuildSyncClient implements ClientModInitializer {
	static final String VERSION = "0.2.1";
	private static McWorld world;
	private static Bridge bridge;
	private static LiveServer server;
	private static Path dir;
	private static String code = "";
	private static volatile String dimNow = null;
	private static String worldId = "";
	private static boolean wasIn;
	private static final Set<String> ORIGINS = new HashSet<>();

	private static void log(String s) { System.out.println("[BuildSync] " + s); }

	@Override
	public void onInitializeClient() {
		dir = FabricLoader.getInstance().getConfigDir().resolve("buildsync");
		world = new McWorld();
		bridge = new Bridge(dir, VERSION, "26.2", world.pal, BuildSyncClient::log);
		try {
			code = Pairing.load(dir);
			Path of = dir.resolve("origins.txt");   // optional: additional allowed origins (e.g. your own web page), one per line
			if (Files.exists(of)) for (String l : Files.readAllLines(of)) if (!l.isBlank() && !l.startsWith("#")) ORIGINS.add(l.trim());
			server = new LiveServer(38517, 20, code, ORIGINS, bridge, BuildSyncClient::log);
			int port = server.start();
			log("Local server on 127.0.0.1:" + port + " – pairing code is stored in " + dir.resolve("pairing.txt"));
		} catch (IOException e) {
			log("Server could not start: " + e);
		}

		ClientTickEvents.END_CLIENT_TICK.register(BuildSyncClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> leave());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { bridge.save(); if (server != null) server.close(); });
		Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { bridge.save(); } catch (Throwable ignored) { } }));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> registerCommands(dispatcher));
	}

	private static void tick(Minecraft mc) {
		try {
			if (mc.level == null || mc.player == null) {
				if (wasIn) leave();
				bridge.tick(null, null, "");
				return;
			}
			if (!wasIn) { wasIn = true; worldId = WorldId.of(mc); }
			world.bind(mc.level, mc.player);
			dimNow = mc.level.dimension().identifier().toString();
			bridge.tick(world, dimNow, worldId);
		} catch (Throwable t) {
			log("Error in tick: " + t);
		}
	}

	private static void leave() {
		wasIn = false; dimNow = null;
		try { bridge.leaveWorld(); } catch (Throwable ignored) { }
	}

	/** Called by the mixin: a block changed in the client world. */
	public static void onBlockSet(Level level, int x, int y, int z) {
		String d = dimNow;
		if (d != null && bridge != null && world != null && level == world.level()) bridge.blockChanged(d, x, y, z);
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("buildsync")
			.executes(c -> { c.getSource().sendFeedback(Component.literal(status())); return 1; })
			.then(ClientCommands.literal("code").requires(FabricClientCommandSource::attended).executes(c -> {
				c.getSource().sendFeedback(Component.literal("BuildSync pairing code: " + code + "  (Port " + (server == null ? "?" : server.port()) + ")"));
				return 1;
			}))
			.then(ClientCommands.literal("newcode").requires(FabricClientCommandSource::attended).executes(c -> {
				try {
					code = Pairing.renew(dir);
					if (server != null) server.close();
					server = new LiveServer(38517, 20, code, ORIGINS, bridge, BuildSyncClient::log);
					int port = server.start();
					c.getSource().sendFeedback(Component.literal("New code: " + code + "  (Port " + port + ")"));
				} catch (IOException e) { c.getSource().sendError(Component.literal("Error: " + e.getMessage())); }
				return 1;
			}))
			.then(ClientCommands.literal("stop").requires(FabricClientCommandSource::attended).executes(c -> {
				bridge.leaveWorld(); c.getSource().sendFeedback(Component.literal("BuildSync session ended (state saved).")); return 1;
			}))
		);
	}

	private static String status() {
		return "BuildSync " + VERSION + " – server: " + (server == null ? "off" : "127.0.0.1:" + server.port())
			+ " · session: " + (bridge.active() ? "active (" + bridge.engine().loadedCount() + "/" + bridge.engine().total() + " chunks)" : "none")
			+ " · /buildsync code shows the pairing code";
	}
}
