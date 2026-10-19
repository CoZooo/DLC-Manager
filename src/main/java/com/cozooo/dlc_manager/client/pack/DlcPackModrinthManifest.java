package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPathUtil;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class DlcPackModrinthManifest {
    private DlcPackModrinthManifest() {}

    @FunctionalInterface public interface Downloader {
        Path download(String url, Path directory, String name, long size, DlcDownloadService.ProgressListener progress,
            DlcDownloadService.CancelChecker cancel) throws IOException;
    }

    public static List<FileRef> read(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            JsonObject index = JsonParser.parseReader(reader).getAsJsonObject();
            if (!"1".equals(index.get("formatVersion").getAsString()) || !"minecraft".equals(index.get("game").getAsString())) {
                throw new IOException("Unsupported Modrinth pack format or game");
            }
            List<FileRef> files = new ArrayList<>();
            Set<String> paths = new HashSet<>();
            for (JsonElement element : index.getAsJsonArray("files")) {
                JsonObject file = element.getAsJsonObject();
                String relative = file.get("path").getAsString().replace('\\', '/');
                if (relative.startsWith("/") || relative.endsWith("/") || relative.contains(":") || relative.indexOf(0) >= 0
                    || Arrays.stream(relative.split("/", -1)).anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))
                    || !paths.add(relative.toLowerCase(Locale.ROOT))) throw new IOException("Invalid or duplicate Modrinth file path: " + relative);
                JsonObject hashes = file.getAsJsonObject("hashes");
                String sha1 = hashes.get("sha1").getAsString(), sha512 = hashes.get("sha512").getAsString();
                if (!sha1.matches("(?i)[0-9a-f]{40}") || !sha512.matches("(?i)[0-9a-f]{128}")) throw new IOException("Invalid Modrinth file hashes: " + relative);
                long size = file.get("fileSize").getAsBigDecimal().longValueExact();
                if (size < 0) throw new IOException("Invalid Modrinth file size: " + relative);
                List<String> downloads = new ArrayList<>();
                for (JsonElement url : file.getAsJsonArray("downloads")) {
                    URI uri = URI.create(url.getAsString());
                    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) throw new IOException("Invalid Modrinth download URL: " + relative);
                    downloads.add(uri.toASCIIString());
                }
                if (downloads.isEmpty()) throw new IOException("Missing Modrinth download URLs: " + relative);
                JsonObject env = file.has("env") ? file.getAsJsonObject("env") : null;
                files.add(new FileRef(relative, sha1, sha512, size, List.copyOf(downloads), environment(env, "client"), environment(env, "server")));
            }
            return List.copyOf(files);
        } catch (RuntimeException error) {
            throw new IOException("Invalid modrinth.index.json", error);
        }
    }

    private static String environment(JsonObject env, String side) throws IOException {
        String value = env == null ? "required" : env.get(side).getAsString();
        if (!Set.of("required", "optional", "unsupported").contains(value)) throw new IOException("Invalid Modrinth environment: " + side);
        return value;
    }

    public static List<FileRef> filesForEnvironment(DlcPackPackage pack) {
        boolean server = DlcPlatformServices.isDedicatedServer();
        return pack.modrinthFiles.stream().filter(file -> !"unsupported".equals(server ? file.server : file.client))
            .filter(file -> !DlcPackInstallPlan.isMetadataFile(file.path) && !DlcPackInstallPlan.isIgnoredModpackJarPath(file.path)).toList();
    }

    public static List<DlcPackInstallPlan.FileEntry> resolveManifestTargets(DlcPackPackage pack) {
        return filesForEnvironment(pack).stream().filter(file -> !hasOverride(pack, file.path))
            .map(file -> new DlcPackInstallPlan.FileEntry(pack.manifestFile, file.path)).toList();
    }

    public static List<DlcPackInstallPlan.FileEntry> downloadManifestFiles(DlcPackPackage pack, Path staging, Path packDir,
        DlcDownloadService.CancelChecker cancel, DlcPackInstaller.ProgressListener progress) throws IOException, InterruptedException {
        return downloadManifestFiles(pack, staging, packDir, cancel, progress, DlcDownloadService::downloadKnown);
    }

    public static List<DlcPackInstallPlan.FileEntry> downloadManifestFiles(DlcPackPackage pack, Path staging, Path packDir,
        DlcDownloadService.CancelChecker cancel, DlcPackInstaller.ProgressListener progress, Downloader downloader) throws IOException, InterruptedException {
        List<FileRef> files = filesForEnvironment(pack).stream().filter(file -> !hasOverride(pack, file.path)).toList();
        if (files.isEmpty()) return List.of();
        AtomicInteger completed = new AtomicInteger();
        ExecutorService worker = Executors.newFixedThreadPool(Math.max(1, Math.min(DlcConfig.getMaxConcurrentDownloads(), files.size())));
        try {
            List<Callable<DlcPackInstallPlan.FileEntry>> tasks = new ArrayList<>();
            for (int index = 0; index < files.size(); index++) {
                FileRef file = files.get(index);
                int fileIndex = index;
                tasks.add(() -> {
                    checkCanceled(cancel);
                    Path cached = DlcPathUtil.resolveInside(packDir.resolve("mr_cache"), file.path);
                    if (!verifyFile(cached, file)) {
                        IOException failure = null;
                        boolean downloaded = false;
                        for (String url : file.downloads) {
                            checkCanceled(cancel);
                            try {
                                Path output = downloader.download(url, staging.resolve("mr_manifest").resolve(Integer.toString(fileIndex)),
                                    Path.of(file.path).getFileName().toString(), file.fileSize,
                                    (done, total) -> { if (progress != null) progress.onProgress("Downloading " + file.path,
                                        (completed.get() + (total > 0 ? Math.min(1f, done / (float) total) : 0f)) / files.size()); }, cancel);
                                if (!verifyFile(output, file)) throw new IOException("Modrinth file integrity check failed: " + file.path);
                                Files.createDirectories(cached.getParent());
                                Files.move(output, cached, StandardCopyOption.REPLACE_EXISTING);
                                downloaded = true;
                                break;
                            } catch (DlcDownloadService.DownloadCanceledException error) { throw error; }
                            catch (IOException error) { failure = error; }
                        }
                        if (!downloaded) throw failure == null ? new IOException("Failed to download " + file.path) : failure;
                    }
                    checkCanceled(cancel);
                    int done = completed.incrementAndGet();
                    if (progress != null) progress.onProgress("Resolved " + file.path, done / (float) files.size());
                    return new DlcPackInstallPlan.FileEntry(cached, file.path);
                });
            }
            List<DlcPackInstallPlan.FileEntry> entries = new ArrayList<>();
            for (Future<DlcPackInstallPlan.FileEntry> future : worker.invokeAll(tasks)) {
                try { entries.add(future.get()); }
                catch (ExecutionException error) {
                    if (error.getCause() instanceof IOException io) throw io;
                    throw new IOException("Failed to download Modrinth pack files", error.getCause());
                }
            }
            return entries;
        } finally {
            worker.shutdownNow();
            boolean interrupted = false;
            while (!worker.isTerminated()) {
                try { worker.awaitTermination(1, TimeUnit.SECONDS); }
                catch (InterruptedException error) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static boolean hasOverride(DlcPackPackage pack, String relative) {
        return pack.installRoots().stream().anyMatch(root -> Files.isRegularFile(root.resolve(relative)));
    }

    private static void checkCanceled(DlcDownloadService.CancelChecker cancel) throws DlcDownloadService.DownloadCanceledException {
        if (Thread.currentThread().isInterrupted() || cancel != null && cancel.isCanceled()) throw new DlcDownloadService.DownloadCanceledException("canceled");
    }

    public static boolean verifyFile(Path path, FileRef file) throws IOException {
        return Files.isRegularFile(path) && Files.size(path) == file.fileSize
            && file.sha512.equalsIgnoreCase(DlcDownloadUtil.computeHashHex(path, "SHA-512"))
            && file.sha1.equalsIgnoreCase(DlcDownloadUtil.computeHashHex(path, "SHA-1"));
    }

    public static final class FileRef {
        public final String path, sha1, sha512, client, server;
        public final long fileSize;
        public final List<String> downloads;
        public FileRef(String path, String sha1, String sha512, long fileSize, List<String> downloads, String client, String server) {
            this.path = path; this.sha1 = sha1; this.sha512 = sha512; this.fileSize = fileSize;
            this.downloads = List.copyOf(downloads); this.client = client; this.server = server;
        }
    }
}
