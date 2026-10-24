package de.buildsync.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Starts the bridge + WebSocket server with a simulated world (for end-to-end tests with Node/browser). Arguments: port, code. */
public class ServerDemo {
    public static void main(String[] a) throws Exception {
        int port = Integer.parseInt(a[0]); String code = a[1];
        SimWorld w = CoreTests.world();
        Path dir = Files.createTempDirectory("bsync");
        Bridge b = new Bridge(dir, "test", "26.2", w.pal, s -> System.err.println("LOG " + s));
        LiveServer s = new LiveServer(port, 1, code, Set.of(), b, x -> System.err.println("SRV " + x));
        s.start();
        System.out.println("READY " + s.port());
        System.out.flush();
        java.util.concurrent.ConcurrentLinkedQueue<String> cmds = new java.util.concurrent.ConcurrentLinkedQueue<>();
        Thread t = new Thread(() -> { try (java.util.Scanner sc = new java.util.Scanner(System.in)) { while (sc.hasNextLine()) cmds.add(sc.nextLine()); } });
        t.setDaemon(true); t.start();
        for (;;) {
            String c;
            while ((c = cmds.poll()) != null) {   // "set x y z state" – set a block in the simulated world
                String[] q = c.split(" ", 5);
                if (q.length == 5 && q[0].equals("set")) { int x = Integer.parseInt(q[1]), y = Integer.parseInt(q[2]), z = Integer.parseInt(q[3]); w.set(x, y, z, q[4]); b.blockChanged("minecraft:overworld", x, y, z); }
            }
            b.tick(w, "minecraft:overworld", "sp:test"); Thread.sleep(50);
        }
    }
}
