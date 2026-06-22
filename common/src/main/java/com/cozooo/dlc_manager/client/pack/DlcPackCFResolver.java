package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.download.DlcCurseForgeClient;
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.core.DlcConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class DlcPackCFResolver {
    private static final String SELECTING_SOURCE_STATUS = "dlc_manager.pack.selecting_source";

    private DlcPackCFResolver() {
    }

    public static List<DlcPackInstallPlan.FileEntry> resolveManifestTargets(DlcPackPackage pack) throws IOException, InterruptedException {
        if (pack == null || pack.format != DlcPackPackage.Format.CURSEFORGE || pack.manifestFiles.isEmpty()) {
            return List.of();
        }
        List<DlcPackInstallPlan.FileEntry> entries = new ArrayList<>();
        for (DlcPackManifest.FileRef ref : pack.manifestFiles) {
            ManifestTarget target = resolveManifestTarget(pack, ref);
            if (target != null && !hasDownloadedEntry(entries, target.relativePath)) {
                entries.add(new DlcPackInstallPlan.FileEntry(null, target.relativePath));
            }
        }
        return entries;
    }

    public static List<DlcPackInstallPlan.FileEntry> downloadManifestFiles(DlcPackPackage pack, Path staging, Path packDir,
                                                                           DlcDownloadService.CancelChecker cancelChecker)
        throws IOException, InterruptedException {
        return downloadManifestFiles(pack, staging, packDir, cancelChecker, null);
    }

    public static List<DlcPackInstallPlan.FileEntry> downloadManifestFiles(DlcPackPackage pack, Path staging, Path packDir,
                                                                           DlcDownloadService.CancelChecker cancelChecker,
                                                                           DlcPackInstaller.ProgressListener progressListener)
        throws IOException, InterruptedException {
        if (pack == null || pack.format != DlcPackPackage.Format.CURSEFORGE || pack.manifestFiles.isEmpty()) {
            return List.of();
        }
        Path downloadRoot = staging.resolve("manifest");
        Path cacheRoot = packDir.resolve("cf_cache");
        List<DlcPackInstallPlan.FileEntry> entries = new ArrayList<>();
        List<ManifestDownload> downloads = new ArrayList<>();
        Set<String> seenRelative = new LinkedHashSet<>();
        if (progressListener != null) {
            progressListener.onProgress(SELECTING_SOURCE_STATUS, -1f);
        }
        for (DlcPackManifest.FileRef ref : pack.manifestFiles) {
            if (cancelChecker != null && cancelChecker.isCanceled()) {
                throw new DlcDownloadService.DownloadCanceledException("canceled");
            }
            ManifestTarget target = resolveManifestTarget(pack, ref);
            if (target == null || !seenRelative.add(target.relativePath)) {
                continue;
            }
            List<DlcDownloadCandidate> candidates = DlcDownloadCandidate.curseForgeCandidates(target.file);
            if (candidates.isEmpty()) {
                continue;
            }
            DlcDownloadCandidate candidate = candidates.get(0);
            Path cached = cacheFile(cacheRoot, target.targetDirectory, target.fileName);
            if (!isUsableCachedFile(cached, candidate)) {
                Files.deleteIfExists(cached);
                Path outputDir = downloadRoot.resolve(target.targetDirectory).resolve(safeSegment(ref.projectId)).resolve(safeSegment(ref.fileId));
                downloads.add(new ManifestDownload(target, candidates, cached, outputDir));
            } else {
                entries.add(new DlcPackInstallPlan.FileEntry(cached, target.relativePath));
            }
        }
        if (!downloads.isEmpty()) {
            entries.addAll(downloadManifestFilesConcurrent(downloads, cancelChecker, progressListener));
        }
        if (progressListener != null) {
            progressListener.onProgress("Resolved CurseForge manifest files", 1f);
        }
        return entries;
    }

    private static List<DlcPackInstallPlan.FileEntry> downloadManifestFilesConcurrent(
        List<ManifestDownload> downloads,
        DlcDownloadService.CancelChecker cancelChecker,
        DlcPackInstaller.ProgressListener progressListener
    ) throws IOException, InterruptedException {
        int total = downloads.size();
        int maxConcurrent = Math.max(1, Math.min(DlcConfig.getMaxConcurrentDownloads(), total));
        AtomicReferenceArray<Float> perFileProgress = new AtomicReferenceArray<>(total);
        for (int i = 0; i < total; i++) {
            perFileProgress.set(i, 0f);
        }
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrent);
        List<Future<DlcPackInstallPlan.FileEntry>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < downloads.size(); index++) {
                int fileIndex = index;
                ManifestDownload download = downloads.get(index);
                futures.add(executor.submit(() -> {
                    if (cancelChecker != null && cancelChecker.isCanceled()) {
                        throw new DlcDownloadService.DownloadCanceledException("canceled");
                    }
                    Path downloaded = downloadManifestFile(download, fileIndex, perFileProgress, total, cancelChecker, progressListener);
                    Files.createDirectories(download.cached.getParent());
                    Files.move(downloaded, download.cached, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    perFileProgress.set(fileIndex, 1f);
                    if (progressListener != null) {
                        progressListener.onProgress("Resolved " + download.target.fileName, aggregateProgress(perFileProgress, total));
                    }
                    return new DlcPackInstallPlan.FileEntry(download.cached, download.target.relativePath);
                }));
            }
            List<DlcPackInstallPlan.FileEntry> entries = new ArrayList<>();
            for (Future<DlcPackInstallPlan.FileEntry> future : futures) {
                DlcPackInstallPlan.FileEntry entry;
                try {
                    entry = future.get();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof DlcDownloadService.DownloadCanceledException canceled) {
                        throw canceled;
                    }
                    if (cause instanceof IOException io) {
                        throw io;
                    }
                    if (cause instanceof InterruptedException interrupted) {
                        throw interrupted;
                    }
                    throw new IOException(cause == null ? ex : cause);
                }
                if (entry != null && !hasDownloadedEntry(entries, entry.relativePath)) {
                    entries.add(entry);
                }
            }
            return entries;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Path downloadManifestFile(
        ManifestDownload download,
        int fileIndex,
        AtomicReferenceArray<Float> perFileProgress,
        int total,
        DlcDownloadService.CancelChecker cancelChecker,
        DlcPackInstaller.ProgressListener progressListener
    ) throws IOException {
        IOException lastError = null;
        for (DlcDownloadCandidate candidate : download.candidates) {
            if (cancelChecker != null && cancelChecker.isCanceled()) {
                throw new DlcDownloadService.DownloadCanceledException("canceled");
            }
            perFileProgress.set(fileIndex, 0f);
            try {
                Path downloaded = DlcDownloadService.downloadKnown(
                    candidate.url,
                    download.outputDir,
                    download.target.fileName,
                    candidate.expectedSize,
                    manifestProgressListener(download.target.fileName, fileIndex, perFileProgress, total, progressListener),
                    cancelChecker
                );
                verifyDownloaded(downloaded, candidate);
                return downloaded;
            } catch (DlcDownloadService.DownloadCanceledException ex) {
                throw ex;
            } catch (IOException ex) {
                lastError = ex;
            }
        }
        throw lastError == null ? new IOException("download failed") : lastError;
    }

    private static DlcDownloadService.ProgressListener manifestProgressListener(
        String fileName,
        int fileIndex,
        AtomicReferenceArray<Float> perFileProgress,
        int total,
        DlcPackInstaller.ProgressListener progressListener
    ) {
        return new DlcDownloadService.StatusProgressListener() {
            @Override
            public void onProgress(long done, long fileTotal) {
                if (progressListener != null) {
                    float fileProgress = fileTotal > 0 ? done / (float) fileTotal : 0f;
                    perFileProgress.set(fileIndex, Math.max(0f, Math.min(1f, fileProgress)));
                    progressListener.onProgress("Downloading " + fileName, aggregateProgress(perFileProgress, total));
                }
            }

            @Override
            public void onStatusKey(String statusKey) {
                if (progressListener != null && "selecting_source".equals(statusKey) && aggregateProgress(perFileProgress, total) <= 0f) {
                    progressListener.onProgress(SELECTING_SOURCE_STATUS, -1f);
                }
            }
        };
    }

    private static float aggregateProgress(AtomicReferenceArray<Float> perFileProgress, int total) {
        if (total <= 0) {
            return 1f;
        }
        float progress = 0f;
        for (int i = 0; i < total; i++) {
            Float value = perFileProgress.get(i);
            progress += Math.max(0f, Math.min(1f, value == null ? 0f : value));
        }
        return Math.max(0f, Math.min(1f, progress / total));
    }

    private static ManifestTarget resolveManifestTarget(DlcPackPackage pack, DlcPackManifest.FileRef ref) throws IOException, InterruptedException {
        DlcCurseForgeClient.CurseForgeFile file = DlcCurseForgeClient.fetchFile(ref.projectId, ref.fileId);
        if (file == null || file.url == null || file.url.isBlank()) {
            throw new IOException("Failed to resolve CurseForge file: " + ref.projectId + "/" + ref.fileId);
        }
        DlcCurseForgeClient.CurseForgeMod mod = DlcCurseForgeClient.fetchMod(ref.projectId);
        String fileName = DlcDownloadUtil.sanitizeFileName(file.fileName);
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(file.url));
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IOException("CurseForge file name is not available: " + ref.projectId + "/" + ref.fileId);
        }
        if (DlcPackInstallPlan.isIgnoredModpackJarFileName(fileName)) {
            return null;
        }
        Path targetDirectory = targetDirectory(file, mod, fileName);
        String relative = targetDirectory.resolve(fileName).toString().replace('\\', '/');
        if (hasBundledOverride(pack, relative)) {
            return null;
        }
        return new ManifestTarget(file, targetDirectory, fileName, relative);
    }

    private static Path targetDirectory(DlcCurseForgeClient.CurseForgeFile file, DlcCurseForgeClient.CurseForgeMod mod, String fileName) {
        return Path.of(resolveTargetDirectoryName(file, mod, fileName));
    }

    public static String resolveTargetDirectoryName(DlcCurseForgeClient.CurseForgeFile file, DlcCurseForgeClient.CurseForgeMod mod) {
        return resolveTargetDirectoryName(file, mod, null);
    }

    public static String resolveTargetDirectoryName(DlcCurseForgeClient.CurseForgeFile file, DlcCurseForgeClient.CurseForgeMod mod, String fileName) {
        if (isJarFileName(fileName)) {
            return "mods";
        }
        String type = normalize(file == null ? null : file.classSlug);
        if (type == null) {
            type = normalize(mod == null ? null : mod.classSlug);
        }
        if (type != null) {
            if (type.contains("resource") || type.contains("texture")) {
                return "resourcepacks";
            }
            if (isDataPackSlug(type)) {
                return "datapacks";
            }
        }
        if (mod != null) {
            for (String slug : mod.categorySlugs) {
                String normalized = normalize(slug);
                if (normalized == null) {
                    continue;
                }
                if (normalized.contains("resource") || normalized.contains("texture")) {
                    return "resourcepacks";
                }
                if (isDataPackSlug(normalized)) {
                    return "datapacks";
                }
            }
        }
        return "resourcepacks";
    }

    private static boolean isJarFileName(String fileName) {
        return fileName != null && fileName.trim().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static boolean isDataPackSlug(String value) {
        if (value == null) {
            return false;
        }
        return value.equals("datapack")
            || value.equals("data-pack")
            || value.equals("data packs")
            || value.equals("data-packs")
            || value.equals("datapacks")
            || value.contains("data pack")
            || value.contains("data-pack")
            || value.contains("datapack");
    }

    private static boolean hasBundledOverride(DlcPackPackage pack, String relative) {
        if (pack == null || relative == null) {
            return false;
        }
        String normalized = DlcPackMeta.normalizeRelativePath(relative);
        return normalized != null && Files.exists(pack.installRoot.resolve(normalized));
    }

    private static boolean hasDownloadedEntry(List<DlcPackInstallPlan.FileEntry> entries, String relative) {
        for (DlcPackInstallPlan.FileEntry entry : entries) {
            if (entry.relativePath.equals(relative)) {
                return true;
            }
        }
        return false;
    }

    private static Path cacheFile(Path cacheRoot, Path targetDirectory, String fileName) {
        return cacheRoot
            .resolve(targetDirectory)
            .resolve(fileName);
    }

    private static String safeSegment(String value) {
        String sanitized = DlcDownloadUtil.sanitizeFileName(value);
        return sanitized == null || sanitized.isBlank() ? "_" : sanitized;
    }

    private static boolean isUsableCachedFile(Path path, DlcDownloadCandidate candidate) throws IOException {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            verifyDownloaded(path, candidate);
            return true;
        } catch (IOException ex) {
            Files.deleteIfExists(path);
            return false;
        }
    }

    private static void verifyDownloaded(Path path, DlcDownloadCandidate candidate) throws IOException {
        if (candidate.hashType != null && candidate.hashValue != null && !candidate.hashValue.isBlank()) {
            String algorithm = mapHashAlgorithm(candidate.hashType);
            String actual = DlcDownloadUtil.computeHashHex(path, algorithm);
            if (actual == null || !actual.equalsIgnoreCase(DlcDownloadUtil.normalizeHash(candidate.hashValue))) {
                Files.deleteIfExists(path);
                throw new IOException("hash mismatch");
            }
            return;
        }
        if (candidate.expectedSize > 0 && Files.size(path) != candidate.expectedSize) {
            Files.deleteIfExists(path);
            throw new IOException("size mismatch");
        }
    }

    private static String mapHashAlgorithm(String hashType) {
        if (hashType == null) {
            return null;
        }
        return switch (hashType.toLowerCase(Locale.ROOT)) {
            case "sha1", "sha-1" -> "SHA-1";
            case "sha512", "sha-512" -> "SHA-512";
            case "md5" -> "MD5";
            default -> hashType;
        };
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class ManifestTarget {
        private final DlcCurseForgeClient.CurseForgeFile file;
        private final Path targetDirectory;
        private final String fileName;
        private final String relativePath;

        private ManifestTarget(DlcCurseForgeClient.CurseForgeFile file, Path targetDirectory, String fileName, String relativePath) {
            this.file = file;
            this.targetDirectory = targetDirectory;
            this.fileName = fileName;
            this.relativePath = relativePath;
        }
    }

    private static final class ManifestDownload {
        private final ManifestTarget target;
        private final List<DlcDownloadCandidate> candidates;
        private final Path cached;
        private final Path outputDir;

        private ManifestDownload(ManifestTarget target, List<DlcDownloadCandidate> candidates, Path cached, Path outputDir) {
            this.target = target;
            this.candidates = candidates;
            this.cached = cached;
            this.outputDir = outputDir;
        }
    }
}
