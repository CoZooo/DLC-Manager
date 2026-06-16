package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.download.CurseForgeClient;
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DlcPackCFResolver {
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
        int totalRefs = pack.manifestFiles.size();
        for (int index = 0; index < pack.manifestFiles.size(); index++) {
            DlcPackManifest.FileRef ref = pack.manifestFiles.get(index);
            if (cancelChecker != null && cancelChecker.isCanceled()) {
                throw new DlcDownloadService.DownloadCanceledException("canceled");
            }
            ManifestTarget target = resolveManifestTarget(pack, ref);
            if (target == null || hasDownloadedEntry(entries, target.relativePath)) {
                continue;
            }
            DlcDownloadCandidate candidate = DlcDownloadCandidate.curseForge(target.file);
            Path cached = cacheFile(cacheRoot, target.targetDirectory, ref, target.fileName);
            if (!isUsableCachedFile(cached, candidate)) {
                Files.deleteIfExists(cached);
                Path outputDir = downloadRoot.resolve(target.targetDirectory).resolve(safeSegment(ref.projectId)).resolve(safeSegment(ref.fileId));
                int fileIndex = index;
                if (progressListener != null) {
                    progressListener.onProgress("Downloading " + target.fileName, fileIndex / (float) totalRefs);
                }
                Path downloaded = DlcDownloadService.downloadKnown(
                    candidate.url,
                    outputDir,
                    target.fileName,
                    candidate.expectedSize,
                    (done, total) -> {
                        if (progressListener != null) {
                            float fileProgress = total > 0 ? done / (float) total : 0f;
                            fileProgress = Math.max(0f, Math.min(1f, fileProgress));
                            progressListener.onProgress("Downloading " + target.fileName, (fileIndex + fileProgress) / (float) totalRefs);
                        }
                    },
                    cancelChecker
                );
                verifyDownloaded(downloaded, candidate);
                Files.createDirectories(cached.getParent());
                Files.move(downloaded, cached, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            entries.add(new DlcPackInstallPlan.FileEntry(cached, target.relativePath));
            if (progressListener != null) {
                progressListener.onProgress("Resolved " + target.fileName, (index + 1) / (float) totalRefs);
            }
        }
        return entries;
    }

    private static ManifestTarget resolveManifestTarget(DlcPackPackage pack, DlcPackManifest.FileRef ref) throws IOException, InterruptedException {
        CurseForgeClient.CurseForgeFile file = CurseForgeClient.fetchFile(ref.projectId, ref.fileId);
        if (file == null || file.url == null || file.url.isBlank()) {
            throw new IOException("Failed to resolve CurseForge file: " + ref.projectId + "/" + ref.fileId);
        }
        CurseForgeClient.CurseForgeMod mod = CurseForgeClient.fetchMod(ref.projectId);
        String fileName = DlcDownloadUtil.sanitizeFileName(file.fileName);
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(file.url));
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IOException("CurseForge file name is not available: " + ref.projectId + "/" + ref.fileId);
        }
        Path targetDirectory = targetDirectory(file, mod);
        String relative = targetDirectory.resolve(fileName).toString().replace('\\', '/');
        if (hasBundledOverride(pack, relative)) {
            return null;
        }
        return new ManifestTarget(file, targetDirectory, fileName, relative);
    }

    private static Path targetDirectory(CurseForgeClient.CurseForgeFile file, CurseForgeClient.CurseForgeMod mod) {
        return Path.of(resolveTargetDirectoryName(file, mod));
    }

    public static String resolveTargetDirectoryName(CurseForgeClient.CurseForgeFile file, CurseForgeClient.CurseForgeMod mod) {
        String type = normalize(file == null ? null : file.classSlug);
        if (type == null) {
            type = normalize(mod == null ? null : mod.classSlug);
        }
        if (type != null) {
            if (type.contains("resource") || type.contains("texture")) {
                return "resourcepacks";
            }
            if (type.contains("data-pack") || type.contains("datapack") || type.contains("world")) {
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
                if (normalized.contains("data-pack") || normalized.contains("datapack") || normalized.contains("world")) {
                    return "datapacks";
                }
            }
        }
        return "mods";
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

    private static Path cacheFile(Path cacheRoot, Path targetDirectory, DlcPackManifest.FileRef ref, String fileName) {
        return cacheRoot
            .resolve(targetDirectory)
            .resolve(safeSegment(ref.projectId))
            .resolve(safeSegment(ref.fileId))
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
        private final CurseForgeClient.CurseForgeFile file;
        private final Path targetDirectory;
        private final String fileName;
        private final String relativePath;

        private ManifestTarget(CurseForgeClient.CurseForgeFile file, Path targetDirectory, String fileName, String relativePath) {
            this.file = file;
            this.targetDirectory = targetDirectory;
            this.fileName = fileName;
            this.relativePath = relativePath;
        }
    }
}
