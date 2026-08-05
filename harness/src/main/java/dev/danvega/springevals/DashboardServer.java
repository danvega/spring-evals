package dev.danvega.springevals;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;

import com.sun.net.httpserver.SimpleFileServer;

/** Kept out of Main so UI conveniences never rotate the benchmark identity hash. */
final class DashboardServer {

    private DashboardServer() {
    }

    static void serve(Path repoRoot, Map<String, String> opts) throws Exception {
        int port = Integer.parseInt(opts.getOrDefault("port", "4173"));
        Path dashboard = repoRoot.resolve("dashboard").toRealPath();
        var server = SimpleFileServer.createFileServer(new InetSocketAddress(port), dashboard,
                SimpleFileServer.OutputLevel.INFO);
        server.start();
        System.out.println("Dashboard: http://localhost:" + port + "  (Ctrl+C to stop)");
        Thread.currentThread().join();
    }
}
