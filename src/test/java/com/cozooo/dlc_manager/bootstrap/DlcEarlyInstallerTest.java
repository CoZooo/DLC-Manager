package com.cozooo.dlc_manager.bootstrap;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcEarlyInstallerTest {
    @TempDir Path game;
    private final Locale originalLocale = Locale.getDefault();

    @AfterEach void reset() {
        DlcPlatformServices.setProvider(null);
        DlcConfig.setDlcRoot("DLC");
        DlcConfig.setMaxSegmentsPerFile(DlcConfig.DEFAULT_MAX_SEGMENTS_PER_FILE);
        DlcConfig.setMinSegmentBytes(DlcConfig.DEFAULT_MIN_SEGMENT_MB * 1024L * 1024L);
        Thread.interrupted();
        Locale.setDefault(originalLocale);
    }

    private void resource(Path root, String id, String target) throws IOException {
        Files.createDirectories(root.resolve("required"));
        Files.writeString(root.resolve("required/" + id + ".dc"), "identifier = \"" + id + "\"\nfile_name = \"" + id + ".jar\"\nappliedTarget = \"" + target + "\"\n");
        Files.writeString(root.resolve("required/" + id + ".jar"), id);
    }

    private DlcEarlyInstaller.Result install(boolean server) throws Exception {
        return DlcEarlyInstaller.install(game, game.resolve("config"), "forge", "1.20.1", server, message -> {});
    }

    @Test void pendingHotUpdateCompletesBeforeRequiredResourcesAreValidated() throws Exception {
        Path root = game.resolve("DLC");
        Path task = root.resolve("tmp/update/jobs/12345678-1234-1234-1234-123456789abc");
        Files.createDirectories(task);
        Files.createDirectories(root.resolve("info"));
        Files.writeString(root.resolve("info/version.dc"), "base='1'\ndlc='1'\n");
        Files.createDirectories(game.resolve("mods"));
        Files.writeString(game.resolve("mods/old.jar"), "old");
        Path archive = task.resolve("archive-0.zip");
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("update.dc"));
            zip.write("""
                [version]
                base='1'
                dlc='1'
                dlc_new='2'
                [file]
                  [del]
                    'mods/old.jar'
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Files.writeString(root.resolve("tmp/update/pending_update.dc"), """
            format=1
            job='12345678-1234-1234-1234-123456789abc'
            archives=[{file:'archive-0.zip', hash:'%s', ignore_unknown:false}]
            """.formatted(com.cozooo.dlc_manager.core.DlcFileMatch.hash(archive)));
        install(false);
        assertFalse(Files.exists(game.resolve("mods/old.jar")));
        assertFalse(Files.exists(root.resolve("tmp/update/pending_update.dc")));
        assertTrue(Files.readString(root.resolve("info/version.dc")).contains("2"));
        assertTrue(Files.exists(game.resolve(".dlc-manager/pending_update_result.dc")));
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

    @Test void invalidArchiveReportsLocalizedResourceAndConfigurationAndNeverCompletes() throws Exception {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        Files.writeString(game.resolve("options.txt"), "lang:en_us\n");
        Path required = game.resolve("DLC/required");
        Files.createDirectories(required);
        Files.writeString(required.resolve("broken.dc"), "identifier='broken'\nfile_name='broken.zip'\nappliedTarget='config/'\nunzip=true\n");
        Files.writeString(required.resolve("broken.zip"), "not a zip archive");
        List<DlcEarlyInstaller.Progress> progress = new ArrayList<>();
        List<String> log = new ArrayList<>();
        IOException failure = assertThrows(IOException.class, () -> DlcEarlyInstaller.install(game, game.resolve("config"),
            "forge", "1.20.1", false, log::add, progress::add, () -> false));
        assertTrue(failure.getMessage().contains("无法安装必要资源"));
        assertTrue(failure.getMessage().contains("broken"));
        assertTrue(failure.getMessage().contains("broken.dc"));
        assertNotNull(failure.getCause());
        assertTrue(log.stream().anyMatch(line -> line.contains("正在扫描必要资源")));
        assertEquals(DlcEarlyInstaller.Stage.FAILED, progress.get(progress.size() - 1).getStage());
        assertTrue(progress.stream().noneMatch(value -> value.getStage() == DlcEarlyInstaller.Stage.COMPLETE));
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

    private DlcEarlyInstaller.Result install(Consumer<DlcEarlyInstaller.Progress> progress, BooleanSupplier cancelled) throws Exception {
        return DlcEarlyInstaller.install(game, game.resolve("config"), "forge", "1.20.1", false, message -> {}, progress, cancelled);
    }

    private static List<DlcEarlyInstaller.Stage> stages(List<DlcEarlyInstaller.Progress> events) {
        return events.stream().map(DlcEarlyInstaller.Progress::getStage).toList();
    }

    private static void assertFailedOnce(List<DlcEarlyInstaller.Progress> events) {
        assertEquals(1, events.stream().filter(event -> event.getStage() == DlcEarlyInstaller.Stage.FAILED).count());
        assertFalse(stages(events).contains(DlcEarlyInstaller.Stage.COMPLETE));
        assertEquals(DlcEarlyInstaller.Stage.FAILED, events.get(events.size() - 1).getStage());
    }

    @Test void emptyPlanPublishesUnknownScanTotalThenCompletesWithoutDividingByZero() throws Exception {
        var events = new ArrayList<DlcEarlyInstaller.Progress>();
        var result = install(events::add, () -> false);
        assertEquals(List.of(DlcEarlyInstaller.Stage.SCANNING, DlcEarlyInstaller.Stage.PLANNED,
            DlcEarlyInstaller.Stage.VERIFYING, DlcEarlyInstaller.Stage.COMPLETE), stages(events));
        assertEquals(-1, events.get(0).getTotalCount());
        assertEquals(0, events.get(1).getTotalCount());
        assertEquals(0, events.get(events.size() - 1).getCompletedCount());
        assertEquals(0, result.getCheckedCount());
    }

    @Test void cachedAndUnchangedEntriesBothAdvanceTheCompletedCountWithoutDownloads() throws Exception {
        resource(game.resolve("DLC"), "first", "mods/first.jar");
        resource(game.resolve("DLC"), "second", "mods/second.jar");
        for (int launch = 0; launch < 2; launch++) {
            var events = new ArrayList<DlcEarlyInstaller.Progress>();
            var result = install(events::add, () -> false);
            assertEquals(launch == 0 ? 2 : 0, result.getInstalledCount());
            assertEquals(0, result.getDownloadedCount());
            assertFalse(stages(events).contains(DlcEarlyInstaller.Stage.DOWNLOADING));
            assertEquals(List.of(1, 2), events.stream()
                .filter(event -> event.getStage() == DlcEarlyInstaller.Stage.ENTRY_COMPLETE)
                .map(DlcEarlyInstaller.Progress::getCompletedCount).toList());
            assertEquals(2, events.get(events.size() - 1).getCompletedCount());
            assertEquals(2, events.get(events.size() - 1).getTotalCount());
            assertEquals(DlcEarlyInstaller.Stage.COMPLETE, events.get(events.size() - 1).getStage());
        }
    }

    @Test void downloadPublishesActualFinalBytesBeforeInstallationAndThrottlesIntermediateEvents() throws Exception {
        byte[] data = new byte[256 * 1024];
        try (DownloadServer http = downloadServer(data, true, false, false)) {
            downloadable(http.url());
            List<DlcEarlyInstaller.Progress> events = Collections.synchronizedList(new ArrayList<>());
            long start = System.nanoTime();
            assertEquals(1, install(events::add, () -> false).getDownloadedCount());
            long duration = System.nanoTime() - start;
            var downloading = events.stream().filter(event -> event.getStage() == DlcEarlyInstaller.Stage.DOWNLOADING).toList();
            var last = downloading.get(downloading.size() - 1);
            assertEquals(data.length, last.getDownloadedBytes());
            assertEquals(data.length, last.getTotalBytes());
            assertTrue(downloading.size() <= duration / 100_000_000L + 4, "Intermediate progress must be coalesced");
            assertTrue(events.indexOf(last) < stages(events).indexOf(DlcEarlyInstaller.Stage.INSTALLING));
            assertArrayEquals(data, Files.readAllBytes(game.resolve("mods/download.jar")));
            assertEquals(DlcEarlyInstaller.Stage.COMPLETE, events.get(events.size() - 1).getStage());
        }
    }

    @Test void chunkedDownloadKeepsItsTotalUnknownAndStillReportsTheFinalByteCount() throws Exception {
        byte[] data = new byte[32 * 1024];
        try (DownloadServer http = downloadServer(data, false, false, false)) {
            downloadable(http.url());
            List<DlcEarlyInstaller.Progress> events = Collections.synchronizedList(new ArrayList<>());
            install(events::add, () -> false);
            var downloading = events.stream().filter(event -> event.getStage() == DlcEarlyInstaller.Stage.DOWNLOADING).toList();
            assertTrue(downloading.stream().allMatch(event -> event.getTotalBytes() == -1));
            assertEquals(data.length, downloading.get(downloading.size() - 1).getDownloadedBytes());
            assertArrayEquals(data, Files.readAllBytes(game.resolve("mods/download.jar")));
        }
    }

    @Test void throwingProgressObserverFallsBackToLogsWithoutBreakingDownload() throws Exception {
        byte[] data = new byte[16 * 1024];
        try (DownloadServer http = downloadServer(data, true, false, false)) {
            downloadable(http.url());
            var warnings = new ArrayList<String>();
            var failedObserverCalls = new AtomicInteger();
            var previous = DlcPlatformServices.get();
            var result = DlcEarlyInstaller.install(game, game.resolve("config"), "forge", "1.20.1", false,
                warnings::add, event -> {
                    if (event.getStage() == DlcEarlyInstaller.Stage.DOWNLOADING && event.getDownloadedBytes() > 0) {
                        failedObserverCalls.incrementAndGet();
                        throw new IllegalStateException("renderer unavailable");
                    }
                }, () -> false);
            assertEquals(1, failedObserverCalls.get());
            assertEquals(1, result.getInstalledCount());
            assertArrayEquals(data, Files.readAllBytes(game.resolve("mods/download.jar")));
            assertTrue(warnings.stream().anyMatch(message -> message.contains("Progress observer unavailable")));
            assertTrue(warnings.stream().anyMatch(message -> message.startsWith("Required resources ready:")));
            assertSame(previous, DlcPlatformServices.get());
        }
    }

    @Test void installationFailurePublishesOnlyOneFailureAndRestoresProvider() throws Exception {
        resource(game.resolve("DLC"), "main", "mods/main.jar");
        Files.writeString(game.resolve("mods"), "not a directory");
        var previous = DlcPlatformServices.get();
        var events = new ArrayList<DlcEarlyInstaller.Progress>();
        assertThrows(IOException.class, () -> install(events::add, () -> false));
        assertFailedOnce(events);
        assertEquals(0, events.get(events.size() - 1).getCompletedCount());
        assertEquals(1, events.get(events.size() - 1).getTotalCount());
        assertFalse(events.get(events.size() - 1).getResourceName().isBlank());
        assertSame(previous, DlcPlatformServices.get());
    }

    @Test void downloadFailureDoesNotExposeRetryMarkersAsMeasuredByteTotals() throws Exception {
        try (DownloadServer http = downloadServer(new byte[0], true, false, false)) {
            http.server().createContext("/failure.jar", exchange -> {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
            });
            downloadable(http.url().replace("download.jar", "failure.jar"));
            var events = new ArrayList<DlcEarlyInstaller.Progress>();
            assertThrows(IOException.class, () -> install(events::add, () -> false));
            assertFailedOnce(events);
            assertFalse(events.stream().anyMatch(event -> event.getTotalBytes() == 1));
            assertFalse(Files.exists(game.resolve("mods/download.jar")));
        }
    }

    @Test void interruptionBeforeScanningStillPublishesFailureAndPreservesTheFlag() {
        var previous = DlcPlatformServices.get();
        var events = new ArrayList<DlcEarlyInstaller.Progress>();
        Thread.currentThread().interrupt();
        assertThrows(InterruptedException.class, () -> install(events::add, () -> false));
        assertEquals(List.of(DlcEarlyInstaller.Stage.FAILED), stages(events));
        assertTrue(Thread.currentThread().isInterrupted());
        assertSame(previous, DlcPlatformServices.get());
    }

    @Test void cancellationAtTheInstallingStagePreventsWrites() throws Exception {
        resource(game.resolve("DLC"), "main", "mods/main.jar");
        var cancelled = new AtomicBoolean();
        var events = new ArrayList<DlcEarlyInstaller.Progress>();
        assertThrows(InterruptedException.class, () -> install(event -> {
            events.add(event);
            if (event.getStage() == DlcEarlyInstaller.Stage.INSTALLING) cancelled.set(true);
        }, cancelled::get));
        assertFailedOnce(events);
        assertFalse(Files.exists(game.resolve("mods/main.jar")));
    }

    @Test void cancellationImmediatelyBeforeCompletionCannotPublishSuccess() throws Exception {
        resource(game.resolve("DLC"), "main", "mods/main.jar");
        var cancelled = new AtomicBoolean();
        var events = new ArrayList<DlcEarlyInstaller.Progress>();
        assertThrows(InterruptedException.class, () -> DlcEarlyInstaller.install(game, game.resolve("config"), "forge", "1.20.1", false,
            message -> { if (message.startsWith("Required resources ready:")) cancelled.set(true); }, events::add, cancelled::get));
        assertFailedOnce(events);
        assertEquals(1, events.get(events.size() - 1).getCompletedCount());
    }

    @Test void segmentedWorkerProgressCanInterruptTheInstallerOwnerWithoutContinuingStartup() throws Exception {
        Files.createDirectories(game.resolve("config/dlc_manager"));
        Files.writeString(game.resolve("config/dlc_manager/config.dc"), "[downloads]\nmaxSegmentsPerFile = 2\nminSegmentMB = 1\n");
        byte[] data = new byte[2 * 1024 * 1024];
        try (DownloadServer http = downloadServer(data, true, true, true)) {
            downloadable(http.url());
            Thread owner = Thread.currentThread();
            var workerInterruptedOwner = new AtomicBoolean();
            List<DlcEarlyInstaller.Progress> events = Collections.synchronizedList(new ArrayList<>());
            var previous = DlcPlatformServices.get();
            assertThrows(InterruptedException.class, () -> install(event -> {
                events.add(event);
                if (event.getStage() == DlcEarlyInstaller.Stage.PLANNED) {
                    assertEquals(2, DlcConfig.getMaxSegmentsPerFile());
                    assertEquals(1024L * 1024L, DlcConfig.getMinSegmentBytes());
                }
                if (event.getStage() == DlcEarlyInstaller.Stage.DOWNLOADING && event.getDownloadedBytes() > 0
                    && Thread.currentThread() != owner && workerInterruptedOwner.compareAndSet(false, true)) owner.interrupt();
            }, () -> false));
            assertTrue(workerInterruptedOwner.get());
            assertTrue(owner.isInterrupted());
            assertFailedOnce(events);
            assertFalse(Files.exists(game.resolve("mods/download.jar")));
            assertSame(previous, DlcPlatformServices.get());
        }
    }

    private void downloadable(String url) throws IOException {
        Path required = game.resolve("DLC/required");
        Files.createDirectories(required);
        Files.writeString(required.resolve("download.dc"), "identifier = \"download\"\nfile_name = \"download.jar\"\nappliedTarget = \"mods/download.jar\"\npriority = \"directurl\"\ndirectUrl = \"" + url + "\"\n");
    }

    private DownloadServer downloadServer(byte[] data, boolean knownLength, boolean ranges, boolean paced) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "early-installer-http-test");
            thread.setDaemon(true);
            return thread;
        });
        http.setExecutor(executor);
        http.createContext("/download.jar", exchange -> {
            try {
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=download.jar");
                if (ranges) exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                if (exchange.getRequestMethod().equals("HEAD")) {
                    if (knownLength) exchange.getResponseHeaders().set("Content-Length", Integer.toString(data.length));
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }
                int start = 0;
                int end = data.length - 1;
                String range = exchange.getRequestHeaders().getFirst("Range");
                boolean partial = ranges && range != null;
                if (partial) {
                    String[] parts = range.substring("bytes=".length()).split("-", -1);
                    start = Integer.parseInt(parts[0]);
                    if (!parts[1].isEmpty()) end = Math.min(end, Integer.parseInt(parts[1]));
                    exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + data.length);
                }
                exchange.sendResponseHeaders(partial ? 206 : 200, knownLength ? end - start + 1 : 0);
                for (int offset = start; offset <= end; offset += 8192) {
                    exchange.getResponseBody().write(data, offset, Math.min(8192, end - offset + 1));
                    if (paced) {
                        exchange.getResponseBody().flush();
                        LockSupport.parkNanos(5_000_000L);
                    }
                }
            } finally {
                exchange.close();
            }
        });
        http.start();
        return new DownloadServer(http, executor);
    }

    private record DownloadServer(HttpServer server, ExecutorService executor) implements AutoCloseable {
        String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/download.jar"; }
        @Override public void close() { server.stop(0); executor.shutdownNow(); }
    }
    @Test void zipResourcesOnlyExtractWhenRequestedAndPassFinalVerification() throws Exception {
        Path required = game.resolve("DLC/required");
        Files.createDirectories(required);
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(required.resolve("bundle.zip")))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("nested/inside.jar"));
            zip.write("inside".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Files.writeString(required.resolve("extract.dc"), "identifier='extract'\nfile_name='bundle.zip'\nappliedTarget='mods/'\nunzip=true\n");
        Files.writeString(required.resolve("copy.dc"), "identifier='copy'\nfile_name='bundle.zip'\nappliedTarget='packs/renamed.zip'\n");
        install(false);
        assertEquals("inside", Files.readString(game.resolve("mods/nested/inside.jar")));
        assertArrayEquals(Files.readAllBytes(required.resolve("bundle.zip")), Files.readAllBytes(game.resolve("packs/renamed.zip")));
        assertFalse(Files.exists(game.resolve("mods/bundle.zip")));
        install(false);
    }

}
