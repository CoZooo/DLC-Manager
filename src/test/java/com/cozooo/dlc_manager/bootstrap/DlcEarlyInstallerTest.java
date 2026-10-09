package com.cozooo.dlc_manager.bootstrap;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcEarlyInstallerTest {
    @TempDir Path game;

    @AfterEach void reset() {
        DlcPlatformServices.setProvider(null);
        DlcConfig.setDlcRoot("DLC");
        Thread.interrupted();
    }

    private void resource(Path root, String id, String target) throws IOException {
        Files.createDirectories(root.resolve("required"));
        Files.writeString(root.resolve("required/" + id + ".dc"), "identifier = \"" + id + "\"\nfile_name = \"" + id + ".jar\"\nappliedTarget = \"" + target + "\"\n");
        Files.writeString(root.resolve("required/" + id + ".jar"), id);
    }

    private DlcEarlyInstaller.Result install(boolean server) throws Exception {
        return DlcEarlyInstaller.install(game, game.resolve("config"), "forge", "1.20.1", server, message -> {});
    }

    @Test void cachedResourcesAreInstalledBeforeReturningAndRepeatedLaunchDoesNoWork() throws Exception {
        resource(game.resolve("DLC"), "main", "mods/main.jar");
        var previous = DlcPlatformServices.get();
        var result = install(false);
        assertEquals("main", Files.readString(game.resolve("mods/main.jar")));
        assertEquals(1, result.getCheckedCount());
        assertEquals(0, result.getDownloadedCount());
        assertEquals(1, result.getInstalledCount());
        assertSame(previous, DlcPlatformServices.get());
        assertEquals(0, install(false).getInstalledCount());
        Files.delete(game.resolve("mods/main.jar"));
        assertEquals(1, install(false).getInstalledCount());
    }

    @Test void clientAndDedicatedServerUseConfiguredRootsOutsideTheWorkingDirectory() throws Exception {
        Path config = game.resolve("config/dlc_manager");
        Files.createDirectories(config);
        Files.writeString(config.resolve("config.dc"), "[paths]\ndlcRoot = \"first,second\"\n");
        resource(game.resolve("first"), "a", "mods/a.jar");
        resource(game.resolve("second"), "b", "mods/b.jar");
        assertEquals(2, install(false).getInstalledCount());
        Files.delete(game.resolve("mods/b.jar"));
        assertEquals(1, install(true).getInstalledCount());
        assertEquals("b", Files.readString(game.resolve("mods/b.jar")));
    }

    @Test void nestedDependenciesAreInstalled() throws Exception {
        resource(game.resolve("DLC"), "parent", "mods/parent.jar");
        Files.writeString(game.resolve("DLC/required/parent.dc"), """
            identifier = "parent"
            file_name = "parent.jar"
            appliedTarget = "mods/parent.jar"
            [dependencies.child]
            file_name = "child.jar"
            appliedTarget = "mods/child.jar"
            """);
        Files.writeString(game.resolve("DLC/required/child.jar"), "child");
        assertEquals(2, install(false).getInstalledCount());
        assertEquals("child", Files.readString(game.resolve("mods/child.jar")));
    }

    @Test void downloadCompletesBeforeLoadingCanContinue() throws Exception {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        byte[] body = "downloaded resource".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        http.createContext("/main.jar", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=main.jar");
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(body.length));
            if (exchange.getRequestMethod().equals("HEAD")) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        http.start();
        try {
            Path required = game.resolve("DLC/required");
            Files.createDirectories(required);
            Files.writeString(required.resolve("main.dc"), "identifier = \"main\"\nfile_name = \"main.jar\"\nappliedTarget = \"mods/main.jar\"\npriority = \"directurl\"\ndirectUrl = \"http://127.0.0.1:" + http.getAddress().getPort() + "/main.jar\"\n");
            assertEquals(1, install(false).getDownloadedCount());
            assertArrayEquals(body, Files.readAllBytes(game.resolve("mods/main.jar")));
            assertArrayEquals(body, Files.readAllBytes(game.resolve("DLC/downloaded/required/main.jar")));
            assertFalse(Files.exists(required.resolve("main.jar")));
            int firstLaunchRequests = requests.get();
            install(false);
            assertEquals(firstLaunchRequests, requests.get());
        } finally {
            http.stop(0);
        }
    }

    @Test void failuresPreventContinuingAndRestorePlatformContext() throws Exception {
        resource(game.resolve("DLC"), "main", "mods/main.jar");
        Files.writeString(game.resolve("mods"), "not a directory");
        var previous = DlcPlatformServices.get();
        IOException failure = assertThrows(IOException.class, () -> install(false));
        assertTrue(failure.getMessage().contains("main"));
        assertSame(previous, DlcPlatformServices.get());
        assertFalse(Files.exists(game.resolve("mods/main.jar")));
    }

    @Test void malformedRequiredConfigurationPreventsContinuing() throws Exception {
        Path required = game.resolve("DLC/required");
        Files.createDirectories(required);
        Files.writeString(required.resolve("broken.dc"), "[unterminated\n");
        assertThrows(IOException.class, () -> install(false));
    }

    @Test void directoryIsBackedUpAndReplacedByTheRequiredFile() throws Exception {
        resource(game.resolve("DLC"), "main", "mods/main.jar");
        Files.createDirectories(game.resolve("mods/main.jar"));
        Files.writeString(game.resolve("mods/main.jar/old.txt"), "old");
        install(false);
        assertEquals("main", Files.readString(game.resolve("mods/main.jar")));
        try (var paths = Files.list(game.resolve("DLC/tmp/required"))) {
            assertTrue(paths.anyMatch(path -> Files.isDirectory(path) && Files.exists(path.resolve("old.txt"))));
        }
    }

    @Test void emptyInterruptedStartupCannotReturnSuccess() {
        Thread.currentThread().interrupt();
        assertThrows(InterruptedException.class, () -> install(false));
    }

    @Test void interruptedInstallationCannotReturnSuccess() throws Exception {
        resource(game.resolve("DLC"), "main", "mods/main.jar");
        Thread.currentThread().interrupt();
        assertThrows(InterruptedException.class, () -> install(false));
        assertFalse(Files.exists(game.resolve("mods/main.jar")));
    }
}
