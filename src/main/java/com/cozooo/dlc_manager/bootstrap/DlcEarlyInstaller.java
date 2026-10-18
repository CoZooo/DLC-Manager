package com.cozooo.dlc_manager.bootstrap;

import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader;
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntime;
import com.cozooo.dlc_manager.client.required.DlcRequiredService;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class DlcEarlyInstaller {
    public record Result(int checkedCount, int downloadedCount, int installedCount) {
        public int getCheckedCount() { return checkedCount; }
        public int getDownloadedCount() { return downloadedCount; }
        public int getInstalledCount() { return installedCount; }
    }

    public enum Stage {
        SCANNING, PLANNED, DOWNLOADING, INSTALLING, ENTRY_COMPLETE, VERIFYING, COMPLETE, FAILED
    }

    public record Progress(Stage stage, int completedCount, int totalCount, String resourceName,
                           long downloadedBytes, long totalBytes) {
        public Stage getStage() { return stage; }
        public int getCompletedCount() { return completedCount; }
        public int getTotalCount() { return totalCount; }
        public String getResourceName() { return resourceName; }
        public long getDownloadedBytes() { return downloadedBytes; }
        public long getTotalBytes() { return totalBytes; }
    }

    private DlcEarlyInstaller() {}

    public static Result install(Path gameDir, Path configDir, String loader, String minecraftVersion,
                                 boolean server, Consumer<String> log) throws IOException, InterruptedException {
        return install(gameDir, configDir, loader, minecraftVersion, server, log, null, () -> false);
    }

    public static synchronized Result install(Path gameDir, Path configDir, String loader, String minecraftVersion,
                                               boolean server, Consumer<String> log, Consumer<Progress> progress,
                                               BooleanSupplier cancelled) throws IOException, InterruptedException {
        Thread owner = Thread.currentThread();
        Reporter reporter = new Reporter(log, progress);
        DlcPlatformServices.Provider previous = DlcPlatformServices.get();
        try {
            checkCancelled(owner, cancelled);
            DlcPlatformServices.setProvider(new EarlyProvider(gameDir.toAbsolutePath().normalize(), configDir.toAbsolutePath().normalize(), loader, minecraftVersion, server));
            advance(reporter, Stage.SCANNING, owner, cancelled);
            reporter.log(DlcBootstrapI18n.text("scanning"));
            if (server) DlcConfig.loadServer(); else DlcConfig.loadClient();
            checkCancelled(owner, cancelled);
            com.cozooo.dlc_manager.client.update.DlcPendingUpdate.process(reporter::log, false);
            DlcModpackAutoUpdater.runIfConfigured(server, reporter::log);
            checkCancelled(owner, cancelled);
            validateConfigs(DlcConfig.getDlcRoots());
            checkCancelled(owner, cancelled);
            List<DlcRequiredEntry> entries = DlcRequiredRuntime.loadAllEntries();
            reporter.plan(entries.size());
            advance(reporter, Stage.PLANNED, owner, cancelled);
            DlcRequiredService.reconcileEntries(entries);
            int downloaded = 0;
            int installed = 0;
            for (DlcRequiredEntry entry : entries) {
                checkCancelled(owner, cancelled);
                int progressEntry = reporter.beginEntry(entry.resolveDisplayName());
                try {
                    if (!DlcRequiredService.isDownloaded(entry)) {
                        advance(reporter, Stage.DOWNLOADING, owner, cancelled);
                        reporter.log(DlcBootstrapI18n.text("downloading", entry.resolveDisplayName()));
                        DlcRequiredService.downloadOne(entry, (bytes, total) -> reporter.download(progressEntry, bytes, total),
                            () -> cancellationRequested(owner, cancelled));
                        checkCancelled(owner, cancelled);
                        reporter.flushDownload();
                        downloaded++;
                    }
                    advance(reporter, Stage.INSTALLING, owner, cancelled);
                    reporter.log(DlcBootstrapI18n.text("installing", entry.resolveDisplayName()));
                    if (DlcRequiredService.installOne(entry)) installed++;
                    checkCancelled(owner, cancelled);
                    reporter.entryComplete();
                    checkCancelled(owner, cancelled);
                } catch (IOException | RuntimeException ex) {
                    if (cancellationRequested(owner, cancelled)) {
                        InterruptedException interrupted = new InterruptedException(DlcBootstrapI18n.text("interrupted"));
                        interrupted.initCause(ex);
                        throw interrupted;
                    }
                    throw new IOException(DlcBootstrapI18n.text("resource_failed", entry.resolveDisplayName(), entry.getIdentifier(), entry.getConfigPath()), ex);
                }
            }
            advance(reporter, Stage.VERIFYING, owner, cancelled);
            verifyEntries(entries);
            checkCancelled(owner, cancelled);
            reporter.log(DlcBootstrapI18n.text("ready", entries.size(), downloaded, installed));
            checkCancelled(owner, cancelled);
            reporter.stage(Stage.COMPLETE);
            return new Result(entries.size(), downloaded, installed);
        } catch (IOException | InterruptedException | RuntimeException | Error ex) {
            reporter.failed();
            throw ex;
        } finally {
            DlcPlatformServices.setProvider(previous);
        }
    }

    private static boolean cancellationRequested(Thread owner, BooleanSupplier cancelled) {
        return owner.isInterrupted() || Thread.currentThread().isInterrupted()
            || (cancelled != null && cancelled.getAsBoolean());
    }

    private static void checkCancelled(Thread owner, BooleanSupplier cancelled) throws InterruptedException {
        if (cancellationRequested(owner, cancelled)) throw new InterruptedException(DlcBootstrapI18n.text("interrupted"));
    }

    private static void advance(Reporter reporter, Stage stage, Thread owner, BooleanSupplier cancelled) throws InterruptedException {
        checkCancelled(owner, cancelled);
        reporter.stage(stage);
        checkCancelled(owner, cancelled);
    }

    private static final class Reporter {
        private final Consumer<String> log;
        private Consumer<Progress> observer;
        private Progress current = new Progress(Stage.SCANNING, 0, -1, "", 0, -1);
        private long lastBytesNanos;
        private long lastLogNanos;
        private int entryGeneration;
        private boolean terminal;

        Reporter(Consumer<String> log, Consumer<Progress> observer) {
            this.log = log;
            this.observer = observer;
        }

        synchronized void plan(int total) {
            current = new Progress(current.stage(), 0, total, "", 0, -1);
        }

        synchronized int beginEntry(String name) {
            current = new Progress(current.stage(), current.completedCount(), current.totalCount(), name == null ? "" : name, 0, -1);
            lastBytesNanos = 0;
            lastLogNanos = 0;
            return ++entryGeneration;
        }

        synchronized void stage(Stage stage) {
            if (terminal) return;
            boolean summary = stage == Stage.VERIFYING || stage == Stage.COMPLETE;
            current = new Progress(stage, current.completedCount(), current.totalCount(), summary ? "" : current.resourceName(),
                summary ? 0 : current.downloadedBytes(), summary ? -1 : current.totalBytes());
            terminal = stage == Stage.COMPLETE || stage == Stage.FAILED;
            publish();
        }

        synchronized void download(int entry, long bytes, long total) {
            if (terminal || entry != entryGeneration || current.stage() != Stage.DOWNLOADING
                || Thread.currentThread().isInterrupted()) return;
            if (bytes == 0 && total == 1) return;
            long downloaded = bytes <= 0 ? 0 : Math.max(current.downloadedBytes(), bytes);
            current = new Progress(Stage.DOWNLOADING, current.completedCount(), current.totalCount(), current.resourceName(),
                downloaded, total >= 0 ? total : -1);
            long now = System.nanoTime();
            if (lastBytesNanos == 0 || now - lastBytesNanos >= 100_000_000L) {
                lastBytesNanos = now;
                publish();
            }
            if (lastLogNanos == 0 || now - lastLogNanos >= 1_000_000_000L) {
                lastLogNanos = now;
                logBytes();
            }
        }

        synchronized void flushDownload() {
            if (terminal || current.stage() != Stage.DOWNLOADING) return;
            publish();
            logBytes();
        }

        synchronized void entryComplete() {
            if (terminal) return;
            current = new Progress(Stage.ENTRY_COMPLETE, current.completedCount() + 1, current.totalCount(),
                current.resourceName(), current.downloadedBytes(), current.totalBytes());
            publish();
        }

        synchronized void failed() {
            if (terminal) return;
            stage(Stage.FAILED);
            log(DlcBootstrapI18n.text("failed"));
        }

        void log(String message) {
            if (log != null) {
                try { log.accept(message); }
                catch (RuntimeException ignored) { }
            }
        }

        private void logBytes() {
            log(DlcBootstrapI18n.text("download_bytes", current.resourceName(), current.downloadedBytes(),
                current.totalBytes() >= 0 ? "/" + current.totalBytes() : ""));
        }

        private void publish() {
            if (observer == null) return;
            try { observer.accept(current); }
            catch (RuntimeException ex) {
                observer = null;
                log(DlcBootstrapI18n.text("observer_unavailable", ex.getClass().getSimpleName()));
            }
        }
    }

    public static void verifyInstalled() throws IOException {
        validateConfigs(DlcConfig.getDlcRoots());
        verifyEntries(DlcRequiredRuntime.loadAllEntries());
    }

    private static void verifyEntries(List<DlcRequiredEntry> entries) throws IOException {
        List<DlcRequiredEntry> missing = DlcRequiredService.collectMissingOrApply(entries);
        if (!missing.isEmpty()) {
            throw new IOException(DlcBootstrapI18n.text("incomplete", missing.stream()
                .map(entry -> entry.resolveDisplayName() + " (" + entry.getIdentifier() + ")").collect(java.util.stream.Collectors.joining(", "))));
        }
        for (DlcRequiredEntry entry : entries) {
            String fileName = DlcRequiredService.resolveExistingFileName(entry);
            Path source = entry.resolveRequiredPath(fileName);
            if (source == null || !Files.isRegularFile(source)) {
                throw new IOException(DlcBootstrapI18n.text("not_regular", source));
            }
        }
    }

    private static void validateConfigs(List<Path> roots) throws IOException {
        for (Path root : roots) {
            Path required = root.resolve("required");
            if (!Files.exists(required)) continue;
            if (!Files.isDirectory(required)) throw new IOException(DlcBootstrapI18n.text("not_directory", required));
            try (var paths = Files.newDirectoryStream(required)) {
                for (Path path : paths) {
                    String name = path.getFileName().toString();
                    if (!DlcDcFormatUtil.isDcName(name) || "notice".equalsIgnoreCase(DlcDcFormatUtil.stripConfigExtension(name))) continue;
                    if (DlcRequiredLoader.loadSingle(path, root) == null) {
                        throw new IOException(DlcBootstrapI18n.text("invalid_config", path));
                    }
                }
            }
        }
    }

    private record EarlyProvider(Path gameDir, Path configDir, String loaderName, String mcVersion,
                                 boolean isDedicatedServer) implements DlcPlatformServices.Provider {
        @Override public boolean isModLoaded(String modId) { return false; }
        @Override public String languageCode() {
            return DlcBootstrapI18n.languageCode();
        }
        @Override public String modVersion(String modId) { return ""; }
        @Override public Path modFilePath(String modId) { return null; }
        @Override public String playerName() { return ""; }
        @Override public String playerUuid() { return ""; }
    }
}
