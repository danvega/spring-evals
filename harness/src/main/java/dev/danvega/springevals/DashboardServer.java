package dev.danvega.springevals;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;

import com.sun.net.httpserver.SimpleFileServer;

/**
 * Serves the dashboard with the JDK's built-in file server. Lives outside
 * Main on purpose: Main is part of the benchmark identity hash, and a static
 * file server has nothing to do with judging. UI conveniences must never
 * rotate the benchmark version.
 */
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
