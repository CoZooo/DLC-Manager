package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.core.DlcZipUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

public final class DlcPackArchive {
    private static final String[] METADATA_PATHS = {
        "client-overrides/metadata/pack.dc",
        "client-overrides/dlcpack_metadata/pack.dc",
        "server-overrides/metadata/pack.dc",
        "server-overrides/dlcpack_metadata/pack.dc",
        "overrides/metadata/pack.dc",
        "overrides/dlcpack_metadata/pack.dc",
        "metadata/pack.dc",
        "dlcpack_metadata/pack.dc"
    };
    private static final Set<String> PREVIEW_PATHS = previewPaths();

    private DlcPackArchive() {
    }

    public static boolean isArchiveFileName(String name) {
        if (name == null) return false;
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".zip") || normalized.endsWith(".mrpack");
    }

    public static List<Path> findArchives(Path dlcRoot) throws IOException {
        Set<Path> archives = new LinkedHashSet<>();
        Set<Path> shadowed = new java.util.HashSet<>();
        for (var entry : com.cozooo.dlc_manager.client.remote.DlcRemoteLoader.load(dlcRoot)) {
            if (entry.getType() != com.cozooo.dlc_manager.core.DlcType.DLC_PACK) continue;
            Path preferred = entry.getTargetPath();
            if (preferred != null && Files.isRegularFile(preferred)) archives.add(preferred.toAbsolutePath().normalize());
            Path downloaded = entry.resolveTargetPath(entry.getFileNameHint());
            if (downloaded != null && Files.isRegularFile(downloaded)) shadowed.add(dlcRoot.resolve("pack").resolve(downloaded.getFileName()).toAbsolutePath().normalize());
        }
        Path local = dlcRoot.resolve("pack");
        if (Files.isDirectory(local)) {
            try (var paths = Files.list(local)) {
                paths.filter(Files::isRegularFile).filter(path -> isArchiveFileName(path.getFileName().toString()))
                    .map(path -> path.toAbsolutePath().normalize()).filter(path -> !shadowed.contains(path)).forEach(archives::add);
            }
        }
        return archives.stream().sorted().toList();
    }

    public static ExtractResult extractToStaging(Path zipPath, Path dlcRoot) throws IOException {
        if (zipPath == null || !Files.isRegularFile(zipPath)) {
            throw new IOException("DLC Pack archive is not a file");
        }
        if (dlcRoot == null) {
            throw new IOException("DLC root is not available");
        }
        Path normalizedZip = zipPath.toAbsolutePath().normalize();
        String identifier = readIdentifier(normalizedZip);
        if (identifier == null) {
            identifier = "pack";
        }
        Path stagingRoot = dlcRoot.resolve("tmp").resolve("_staging");
        Path staging = DlcZipUtil.createUniqueDir(stagingRoot, identifier);
        try {
            DlcZipUtil.extractZip(normalizedZip, staging);
            return new ExtractResult(staging);
        } catch (Throwable error) {
            DlcZipUtil.deleteRecursive(staging);
            if (error instanceof IOException io) {
                throw io;
            }
            throw new IOException("Failed to extract DLC Pack archive: " + normalizedZip, error);
        }
    }

    public static ExtractResult extractPreviewToStaging(Path zipPath, Path dlcRoot) throws IOException {
        if (zipPath == null || !Files.isRegularFile(zipPath) || dlcRoot == null) {
            throw new IOException("DLC Pack archive or root is not available");
        }
        Path staging = DlcZipUtil.createUniqueDir(dlcRoot.resolve("tmp").resolve("_staging"), "preview");
        Set<String> overrides = new LinkedHashSet<>();
        Set<String> manifests = new LinkedHashSet<>();
        try {
            DlcZipUtil.extractZip(zipPath, staging, name -> {
                String[] parts = name.split("/");
                if (name.equals("overrides/") || (parts.length > 1 && "overrides".equals(parts[0]))) overrides.add("overrides");
                if (name.endsWith("/overrides/") && parts.length == 2) overrides.add(parts[0] + "/overrides");
                if (parts.length > 2 && "overrides".equals(parts[1])) overrides.add(parts[0] + "/overrides");
                if (isManifestEntry(name)) manifests.add(name);
                return isPreviewEntry(name);
            });
            for (String dir : overrides) {
                Path target = staging.resolve(dir).normalize();
                if (target.startsWith(staging)) Files.createDirectories(target);
            }
            return new ExtractResult(staging, manifests);
        } catch (Throwable error) {
            DlcZipUtil.deleteRecursive(staging);
            if (error instanceof IOException io) throw io;
            throw new IOException("Failed to inspect DLC Pack archive: " + zipPath, error);
        }
    }

    private static boolean isPreviewEntry(String name) {
        if (PREVIEW_PATHS.contains(name)) return true;
        int slash = name.indexOf('/');
        return slash > 0 && PREVIEW_PATHS.contains(name.substring(slash + 1));
    }

    private static boolean isManifestEntry(String name) {
        int slash = name.indexOf('/');
        String manifest = slash > 0 ? name.substring(slash + 1) : name;
        return manifest.equals("manifest.json") || manifest.equals("modrinth.index.json");
    }

    private static Set<String> previewPaths() {
        Set<String> paths = new HashSet<>();
        for (String metadata : METADATA_PATHS) {
            String dir = metadata.substring(0, metadata.lastIndexOf('/') + 1);
            paths.add(metadata);
            for (String extension : new String[] {"png", "jpg", "jpeg", "icn", "ico", "webp"}) {
                paths.add(dir + "icon." + extension);
            }
        }
        return Set.copyOf(paths);
    }

    private static String readIdentifier(Path zipPath) {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            for (String entryName : metadataEntryCandidates(zip)) {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null || entry.isDirectory()) {
                    continue;
                }
                Path metadata = Files.createTempFile("dlc-pack-", ".dc");
                try {
                    try (var input = zip.getInputStream(entry); var output = Files.newOutputStream(metadata)) {
                        input.transferTo(output);
                    }
                    DlcPackMetadata.Result parsed = DlcPackMetadata.read(metadata);
                    if (parsed.valid && parsed.metadata != null) {
                        return parsed.metadata.identifier;
                    }
                } finally {
                    try {
                        Files.deleteIfExists(metadata);
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static List<String> metadataEntryCandidates(ZipFile zip) {
        Set<String> names = new LinkedHashSet<>();
        for (String path : METADATA_PATHS) {
            names.add(path);
        }
        List<String> firstLevelDirs = zip.stream()
            .map(entry -> firstSegment(entry.getName()))
            .filter(segment -> segment != null)
            .distinct()
            .sorted(Comparator.comparing(segment -> segment.toLowerCase(Locale.ROOT)))
            .toList();
        for (String dir : firstLevelDirs) {
            for (String path : METADATA_PATHS) {
                names.add(dir + "/" + path);
            }
        }
        return List.copyOf(names);
    }

    private static String firstSegment(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int slash = normalized.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        String segment = normalized.substring(0, slash);
        if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
            return null;
        }
        return segment;
    }

    public static void deleteRecursive(Path path) {
        DlcZipUtil.deleteRecursive(path);
    }

    public static final class ExtractResult {
        public final Path stagingDir;
        public final Set<String> manifestPaths;

        public ExtractResult(Path stagingDir) {
            this(stagingDir, Set.of());
        }

        public ExtractResult(Path stagingDir, Set<String> manifestPaths) {
            this.stagingDir = stagingDir;
            this.manifestPaths = Set.copyOf(manifestPaths);
        }
    }
}
