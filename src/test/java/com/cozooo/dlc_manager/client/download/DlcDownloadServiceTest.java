package com.cozooo.dlc_manager.client.download;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcDownloadServiceTest {
    private HttpServer server;
    private byte[] payload;

    @TempDir
    Path tempDir;

    @BeforeEach
    void startServer() throws Exception {
        payload = "download-data".getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", this::handleFile);
        server.createContext("/named", this::handleNamed);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/file");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void suggestFileNameUsesContentDispositionHeader() throws Exception {
        assertEquals("header-name.jar", DlcDownloadService.suggestFileName(url("/named")));
    }

    @Test
    void downloadWritesFileAndReportsProgress() throws Exception {
        List<Long> progress = new ArrayList<>();

        Path downloaded = DlcDownloadService.download(url("/file"), tempDir, null, (done, total) -> progress.add(done), () -> false);

        assertEquals(tempDir.resolve("file.jar"), downloaded);
        assertEquals("download-data", Files.readString(downloaded));
        assertTrue(progress.contains(0L));
        assertTrue(progress.contains((long) payload.length));
        assertTrue(!Files.exists(tempDir.resolve("file.jar.part")));
    }

    @Test
    void downloadKnownUsesDesiredFileNameAndExpectedSize() throws Exception {
        Path downloaded = DlcDownloadService.downloadKnown(url("/file"), tempDir, "known.jar", payload.length, null, () -> false);

        assertEquals(tempDir.resolve("known.jar"), downloaded);
        assertEquals("download-data", Files.readString(downloaded));
    }

    @Test
    void downloadSupportsRedirects() throws Exception {
        Path downloaded = DlcDownloadService.download(url("/redirect"), tempDir, "redirect.jar", null, () -> false);

        assertEquals("download-data", Files.readString(downloaded));
    }

    @Test
    void downloadDeletesPartFileWhenCanceledBeforeMove() {
        AtomicBoolean cancel = new AtomicBoolean(true);

        assertThrows(DlcDownloadService.DownloadCanceledException.class,
            () -> DlcDownloadService.download(url("/file"), tempDir, "cancel.jar", null, cancel::get));
        assertTrue(!Files.exists(tempDir.resolve("cancel.jar.part")));
    }

    @Test
    void invalidArgumentsThrowIoException() {
        assertThrows(IOException.class, () -> DlcDownloadService.download("", tempDir, null, null, null));
        assertThrows(IOException.class, () -> DlcDownloadService.download(url("/file"), null, null, null, null));
        assertThrows(IOException.class, () -> DlcDownloadService.suggestFileName(""));
    }

    private void handleFile(HttpExchange exchange) throws IOException {
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Content-Length", String.valueOf(payload.length));
            exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"file.jar\"");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private void handleNamed(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"header-name.jar\"");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Content-Length", String.valueOf(payload.length));
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
        }
        exchange.close();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
