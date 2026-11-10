package com.cozooo.dlc_manager.client.sync;

import com.cozooo.dlc_manager.client.server.*;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import com.cozooo.dlc_manager.server.DlcServerService;
import com.cozooo.dlc_manager.sync.*;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcSyncTransportTest {
    @TempDir Path game;
    String password;
    @BeforeEach void setup() throws Exception {
        Files.createDirectories(game.resolve("DLC"));
        PlatformServices.setProvider(new TestPlatformProvider(game, game.resolve("config")));
        DlcConfig.setDlcRoot("DLC"); password = DlcConfig.getServerPassword(); DlcConfig.setServerPassword("sync-test");
    }
    @AfterEach void reset() { DlcConfig.setServerPassword(password); PlatformServices.setProvider(null); DlcConfig.setDlcRoot("DLC"); }
    @Test void keepsMinecraftPortPrefaceAndUsesAuthenticatedSyncHandler() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Future<?> accepted = executor.submit(() -> {
                try (Socket socket = listener.accept()) {
                    assertArrayEquals(new byte[]{'D','L','C','M',1}, socket.getInputStream().readNBytes(5));
                    DlcServerService.handleClient(socket);
                } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
            });
            DlcServerEntry entry = new DlcServerEntry(); entry.host = "127.0.0.1"; entry.port = listener.getLocalPort(); entry.password = "sync-test";
            try (DlcServerSession session = DlcServerSession.connect(entry, 5000)) {
                var items = DlcSyncClient.manifest(request -> session.syncRequest(request));
                assertEquals(java.util.List.of("manager:config.dc"), items.stream().map(DlcSyncModel.Item::key).toList());
                assertFalse(DlcSyncEndpoint.JSON.toJson(items).contains("sync-test"));
            }
            accepted.get(5, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }
    @Test void authenticatedUploadRejectsCorruptionAndDisconnectDiscardsOnlyIncompleteBytes() throws Exception {
        Path root = game.resolve("DLC"); Files.createDirectories(root.resolve("required")); Files.createDirectories(root.resolve("downloaded/required"));
        Files.writeString(root.resolve("required/main.dc"), "identifier='main'\nfile_name='main.txt'\n");
        Path resource = Files.writeString(root.resolve("downloaded/required/main.txt"), "old resource");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Future<?> accepted = executor.submit(() -> {
                try (Socket socket = listener.accept()) { socket.getInputStream().readNBytes(5); DlcServerService.handleClient(socket); }
                catch (IOException error) { throw new java.io.UncheckedIOException(error); }
            });
            DlcServerEntry entry = new DlcServerEntry(); entry.host = "127.0.0.1"; entry.port = listener.getLocalPort(); entry.password = "sync-test";
            try (DlcServerSession session = DlcServerSession.connect(entry, 5000)) {
                DlcSyncModel.Peer peer = session::syncRequest;
                var begin = DlcSyncClient.request("begin"); begin.addProperty("operation", java.util.UUID.randomUUID().toString()); DlcSyncClient.checked(peer, begin);
                var item = DlcSyncClient.request("item"); item.addProperty("key", "0:required/main.dc"); DlcSyncClient.checked(peer, item);
                var write = DlcSyncClient.request("write"); write.add("file", DlcSyncEndpoint.JSON.toJsonTree(new DlcSyncModel.File("0:downloaded/required/main.txt", 3, "wrong"))); write.addProperty("hash", "wrong"); DlcSyncClient.checked(peer, write);
                var chunk = DlcSyncClient.request("write_chunk"); chunk.addProperty("data", "YmFk"); DlcSyncClient.checked(peer, chunk);
                assertThrows(IOException.class, () -> DlcSyncClient.checked(peer, DlcSyncClient.request("write_end")));
                assertEquals("old resource", Files.readString(resource));
            }
            accepted.get(5, TimeUnit.SECONDS); assertEquals("old resource", Files.readString(resource));
            try (var files = Files.list(root.resolve("tmp/sync"))) { assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith("incoming-"))); }
        } finally { executor.shutdownNow(); }
    }
    @Test void disconnectKeepsUploadedAndAppliedFilesUntilAnAuthenticatedRollback() throws Exception {
        Path root = game.resolve("DLC"); Files.createDirectories(root.resolve("required")); Files.createDirectories(root.resolve("downloaded/required"));
        Files.writeString(root.resolve("required/main.dc"), "identifier='main'\nfile_name='main.txt'\nappliedTarget='config/main.txt'\n");
        Path resource = Files.writeString(root.resolve("downloaded/required/main.txt"), "old");
        Files.createDirectories(game.resolve("config")); Path applied = Files.writeString(game.resolve("config/main.txt"), "original");
        String operation = java.util.UUID.randomUUID().toString();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Future<?> accepted = executor.submit(() -> {
                for (int i = 0; i < 2; i++) try (Socket socket = listener.accept()) { socket.getInputStream().readNBytes(5); DlcServerService.handleClient(socket); }
                catch (IOException error) { throw new java.io.UncheckedIOException(error); }
            });
            DlcServerEntry entry = new DlcServerEntry(); entry.host = "127.0.0.1"; entry.port = listener.getLocalPort(); entry.password = "sync-test";
            try (DlcServerSession session = DlcServerSession.connect(entry, 5000)) {
                DlcSyncModel.Peer peer = session::syncRequest;
                var begin = DlcSyncClient.request("begin"); begin.addProperty("operation", operation); DlcSyncClient.checked(peer, begin);
                var item = DlcSyncClient.request("item"); item.addProperty("key", "0:required/main.dc"); DlcSyncClient.checked(peer, item);
                Path payload = Files.writeString(game.resolve("payload.txt"), "new");
                String hash = com.cozooo.dlc_manager.core.DlcFileMatch.hash(payload);
                var write = DlcSyncClient.request("write"); write.add("file", DlcSyncEndpoint.JSON.toJsonTree(new DlcSyncModel.File("0:downloaded/required/main.txt", 3, hash))); write.addProperty("hash", hash); DlcSyncClient.checked(peer, write);
                var chunk = DlcSyncClient.request("write_chunk"); chunk.addProperty("data", "bmV3"); DlcSyncClient.checked(peer, chunk);
                DlcSyncClient.checked(peer, DlcSyncClient.request("write_end")); DlcSyncClient.checked(peer, DlcSyncClient.request("apply"));
                assertEquals("new", Files.readString(resource)); assertEquals("new", Files.readString(applied));
            }
            try (DlcServerSession session = DlcServerSession.connect(entry, 5000)) {
                DlcSyncModel.Peer peer = session::syncRequest;
                DlcSyncClient.manifest(peer);
                assertEquals("new", Files.readString(resource)); assertEquals("new", Files.readString(applied));
                var restore = DlcSyncClient.request("recover_operation"); restore.addProperty("operation", operation); DlcSyncClient.checked(peer, restore);
            }
            accepted.get(5, TimeUnit.SECONDS);
            assertEquals("old", Files.readString(resource)); assertEquals("original", Files.readString(applied));
        } finally { executor.shutdownNow(); }
    }
    @Test void cancellationCanCloseSocketBeforeConnecting() {
        DlcServerEntry entry = new DlcServerEntry(); entry.host = "127.0.0.1"; entry.port = 1;
        AtomicReference<AutoCloseable> opened = new AtomicReference<>();
        assertThrows(IOException.class, () -> DlcServerSession.connect(entry, 5000, connection -> {
            opened.set(connection); try { connection.close(); } catch (Exception error) { throw new IllegalStateException(error); }
        }));
        assertTrue(((Socket)opened.get()).isClosed());
    }
}
