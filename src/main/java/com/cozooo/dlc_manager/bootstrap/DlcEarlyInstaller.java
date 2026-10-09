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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class DlcEarlyInstaller {
    public record Result(int checkedCount, int downloadedCount, int installedCount) {
        public int getCheckedCount() { return checkedCount; }
        public int getDownloadedCount() { return downloadedCount; }
        public int getInstalledCount() { return installedCount; }
    }

    private DlcEarlyInstaller() {}

    public static synchronized Result install(Path gameDir, Path configDir, String loader, String minecraftVersion,
                                               boolean server, Consumer<String> log) throws IOException, InterruptedException {
        DlcPlatformServices.Provider previous = DlcPlatformServices.get();
        DlcPlatformServices.setProvider(new EarlyProvider(gameDir.toAbsolutePath().normalize(), configDir.toAbsolutePath().normalize(), loader, minecraftVersion, server));
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Required resource installation interrupted");
            if (server) DlcConfig.loadServer(); else DlcConfig.loadClient();
            validateConfigs(DlcConfig.getDlcRoots());
            List<DlcRequiredEntry> entries = DlcRequiredRuntime.loadAllEntries();
            DlcRequiredService.reconcileEntries(entries);
            int downloaded = 0;
            int installed = 0;
            for (DlcRequiredEntry entry : entries) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Required resource installation interrupted");
                try {
                    if (!DlcRequiredService.isDownloaded(entry)) {
                        log.accept("Downloading required resource: " + entry.resolveDisplayName());
                        AtomicLong lastProgress = new AtomicLong();
                        DlcRequiredService.downloadOne(entry, (bytes, total) -> {
                            long now = System.nanoTime();
                            if (now - lastProgress.get() >= 1_000_000_000L) {
                                lastProgress.set(now);
                                log.accept(entry.resolveDisplayName() + ": " + bytes + (total > 0 ? "/" + total : "") + " bytes");
                            }
                        }, () -> Thread.currentThread().isInterrupted());
                        downloaded++;
                    }
                    if (DlcRequiredService.installOne(entry)) installed++;
                } catch (IOException | RuntimeException ex) {
                    throw new IOException("Cannot install required resource '" + entry.getIdentifier() + "' from " + entry.getConfigPath(), ex);
                }
            }
            verifyEntries(entries);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Required resource installation interrupted");
            log.accept("Required resources ready: " + entries.size() + " checked, " + downloaded + " downloaded, " + installed + " installed");
            return new Result(entries.size(), downloaded, installed);
        } finally {
            DlcPlatformServices.setProvider(previous);
        }
    }

    public static void verifyInstalled() throws IOException {
        validateConfigs(DlcConfig.getDlcRoots());
        verifyEntries(DlcRequiredRuntime.loadAllEntries());
    }

    private static void verifyEntries(List<DlcRequiredEntry> entries) throws IOException {
        if (!DlcRequiredService.collectMissingOrApply(entries).isEmpty()) {
            throw new IOException("Required resource installation incomplete; game loading cannot continue");
        }
        for (DlcRequiredEntry entry : entries) {
            String fileName = DlcRequiredService.resolveExistingFileName(entry);
            Path source = entry.resolveRequiredPath(fileName);
            if (source == null || !Files.isRegularFile(source)) {
                throw new IOException("Required resource is not a regular file: " + source);
            }
            for (Path target : entry.resolveAppliedTargets()) {
                String name = target.getFileName() == null ? "" : target.getFileName().toString();
                Path file = name.endsWith(".jar") || name.endsWith(".zip") ? target : target.resolve(fileName);
                if (!Files.isRegularFile(file)) throw new IOException("Required resource target is not installed: " + file);
            }
        }
    }

    private static void validateConfigs(List<Path> roots) throws IOException {
        for (Path root : roots) {
            Path required = root.resolve("required");
            if (!Files.exists(required)) continue;
            if (!Files.isDirectory(required)) throw new IOException("Required resource directory is not a directory: " + required);
            try (var paths = Files.newDirectoryStream(required)) {
                for (Path path : paths) {
                    String name = path.getFileName().toString();
                    if (!DlcDcFormatUtil.isDcName(name) || "notice".equalsIgnoreCase(DlcDcFormatUtil.stripConfigExtension(name))) continue;
                    if (DlcRequiredLoader.loadSingle(path, root) == null) {
                        throw new IOException("Invalid required resource configuration: " + path);
                    }
                }
            }
        }
    }

    private record EarlyProvider(Path gameDir, Path configDir, String loaderName, String mcVersion,
                                 boolean isDedicatedServer) implements DlcPlatformServices.Provider {
        @Override public boolean isModLoaded(String modId) { return false; }
        @Override public String languageCode() {
            String language = DlcPlatformServices.readLanguageFromOptions(gameDir);
            return language == null ? "en_us" : language;
        }
        @Override public String modVersion(String modId) { return ""; }
        @Override public Path modFilePath(String modId) { return null; }
        @Override public String playerName() { return ""; }
        @Override public String playerUuid() { return ""; }
    }
}
