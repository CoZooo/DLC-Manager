package com.cozooo.dlc_manager.client.download;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

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

    @Test
    void interruptedSegmentWaitCancelsWithoutFallingBackToASingleDownload() {
        byte[] large = new byte[2 * 1024 * 1024];
        AtomicInteger singleDownloads = new AtomicInteger();
        server.createContext("/slow-range", exchange -> {
            try {
                exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                if (exchange.getRequestMethod().equals("HEAD")) {
                    exchange.getResponseHeaders().set("Content-Length", Integer.toString(large.length));
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }
                String range = exchange.getRequestHeaders().getFirst("Range");
                int start = 0;
                int end = large.length - 1;
                if (range == null) {
                    singleDownloads.incrementAndGet();
                } else {
                    String[] parts = range.substring("bytes=".length()).split("-", -1);
                    start = Integer.parseInt(parts[0]);
                    if (!parts[1].isEmpty()) end = Integer.parseInt(parts[1]);
                    exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + large.length);
                }
                exchange.sendResponseHeaders(range == null ? 200 : 206, end - start + 1);
                for (int offset = start; offset <= end; offset += 8192) {
                    exchange.getResponseBody().write(large, offset, Math.min(8192, end - offset + 1));
                    exchange.getResponseBody().flush();
                    LockSupport.parkNanos(5_000_000L);
                }
            } finally {
                exchange.close();
            }
        });
        int previousSegments = DlcConfig.getMaxSegmentsPerFile();
        long previousMinimum = DlcConfig.getMinSegmentBytes();
        Thread owner = Thread.currentThread();
        AtomicBoolean interruptedByWorker = new AtomicBoolean();
        try {
            DlcConfig.setMaxSegmentsPerFile(2);
            DlcConfig.setMinSegmentBytes(1024L * 1024L);
            assertThrows(DlcDownloadService.DownloadCanceledException.class,
                () -> DlcDownloadService.download(url("/slow-range"), tempDir, "interrupted.jar", (done, total) -> {
                    if (done > 0 && Thread.currentThread() != owner && interruptedByWorker.compareAndSet(false, true)) owner.interrupt();
                }, () -> false));
            assertTrue(interruptedByWorker.get());
            assertTrue(owner.isInterrupted());
            assertEquals(0, singleDownloads.get());
            assertTrue(!Files.exists(tempDir.resolve("interrupted.jar")));
        } finally {
            Thread.interrupted();
            DlcConfig.setMaxSegmentsPerFile(previousSegments);
            DlcConfig.setMinSegmentBytes(previousMinimum);
        }
    }

    @Test
    @Timeout(10)
    void cancellationWaitsForSegmentWorkersBeforeDeletingTheirTemporaryFile() throws Exception {
        byte[] large = new byte[2 * 1024 * 1024];
        server.createContext("/blocking-range", exchange -> {
            try {
                exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                if (exchange.getRequestMethod().equals("HEAD")) {
                    exchange.getResponseHeaders().set("Content-Length", Integer.toString(large.length));
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }
                String[] range = exchange.getRequestHeaders().getFirst("Range").substring("bytes=".length()).split("-");
                int start = Integer.parseInt(range[0]);
                int end = Integer.parseInt(range[1]);
                exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + large.length);
                exchange.sendResponseHeaders(206, end - start + 1);
                exchange.getResponseBody().write(large, start, end - start + 1);
            } finally {
                exchange.close();
            }
        });
        int previousSegments = DlcConfig.getMaxSegmentsPerFile();
        long previousMinimum = DlcConfig.getMinSegmentBytes();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interruptedOnReturn = new AtomicBoolean();
        Thread owner = new Thread(() -> {
            try {
                DlcDownloadService.download(url("/blocking-range"), tempDir, "blocked.jar", (done, total) -> {
                    if (done <= 0) return;
                    workerStarted.countDown();
                    boolean interrupted = false;
                    while (releaseWorker.getCount() != 0) {
                        try { releaseWorker.await(); }
                        catch (InterruptedException exception) { interrupted = true; }
                    }
                    if (interrupted) Thread.currentThread().interrupt();
                }, () -> false);
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                interruptedOnReturn.set(Thread.currentThread().isInterrupted());
                returned.countDown();
            }
        }, "segmented-download-owner-test");
        try {
            DlcConfig.setMaxSegmentsPerFile(2);
            DlcConfig.setMinSegmentBytes(1024L * 1024L);
            owner.start();
            assertTrue(workerStarted.await(5, TimeUnit.SECONDS));
            owner.interrupt();
            assertTrue(!returned.await(100, TimeUnit.MILLISECONDS), "Returned while a segment still owned the temporary file");
        } finally {
            releaseWorker.countDown();
            owner.join(5000);
            DlcConfig.setMaxSegmentsPerFile(previousSegments);
            DlcConfig.setMinSegmentBytes(previousMinimum);
        }
        assertTrue(!owner.isAlive());
        assertTrue(failure.get() instanceof DlcDownloadService.DownloadCanceledException);
        assertTrue(interruptedOnReturn.get());
        assertTrue(!Files.exists(tempDir.resolve("blocked.jar")));
        assertTrue(!Files.exists(tempDir.resolve("blocked.jar.part")));
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
