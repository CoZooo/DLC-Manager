package com.cozooo.dlc_manager.client.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class DlcPackInstallPlan {
    private static final Set<String> METADATA_DIR_NAMES = Set.of("metadata", "dlcpack_metadata");
    private static final List<String> IGNORED_JAR_PREFIXES = List.of(
        "DLC-Manager-",
        "architectury-",
        "kotlinforforge-",
        "fabric-language-kotlin-",
        "ApricityUI-"
    );
    private static final List<String> GAME_ROOT_FORBIDDEN_PATHS = List.of(
        "config/dlc_manager/"
    );
    private static final List<String> DLC_ROOT_FORBIDDEN_PATHS = List.of(
        "tmp/",
        "info/version.dc",
        "info/notice.dc",
        "update/",
        "shownonfirstrun",
        "skip"
    );

    public final List<FileEntry> files;
    public final List<String> forbiddenPaths;

    private DlcPackInstallPlan(List<FileEntry> files, List<String> forbiddenPaths) {
        this.files = List.copyOf(files);
        this.forbiddenPaths = List.copyOf(forbiddenPaths);
    }

    public boolean getValid() {
        return true;
    }

    public static DlcPackInstallPlan build(DlcPackPackage pack) {
        return build(pack, null, null);
    }

    public static DlcPackInstallPlan build(DlcPackPackage pack, Path instanceRoot, Path dlcRoot) {
        if (pack == null) return new DlcPackInstallPlan(List.of(), List.of());
        var layered = new java.util.LinkedHashMap<String, FileEntry>();
        List<String> forbidden = new ArrayList<>();
        for (Path installRoot : pack.installRoots()) {
            if (!Files.isDirectory(installRoot)) continue;
            try (Stream<Path> stream = Files.walk(installRoot)) {
                stream.filter(Files::isRegularFile).forEach(source -> {
                    String relative = normalizeRelative(installRoot.relativize(source));
                    if (relative == null || isMetadataFile(relative) || isIgnoredModpackJarPath(relative)) return;
                    if (isForbiddenPath(relative, instanceRoot, dlcRoot)) {
                        forbidden.add(relative);
                        return;
                    }
                    layered.put(relative, new FileEntry(source, relative));
                });
            } catch (IOException ignored) {
            }
        }
        if (pack.format == DlcPackPackage.Format.MODRINTH) {
            for (var file : DlcPackModrinthManifest.filesForEnvironment(pack)) {
                if (isForbiddenPath(file.path, instanceRoot, dlcRoot)) forbidden.add(file.path);
            }
        }
        List<FileEntry> files = new ArrayList<>(layered.values());
        files.sort(Comparator.comparing(entry -> entry.relativePath.toLowerCase(Locale.ROOT)));
        forbidden.sort(String::compareTo);
        return new DlcPackInstallPlan(files, forbidden);
    }

    public static DlcPackInstallPlan merge(DlcPackInstallPlan base, List<FileEntry> extraFiles) {
        return merge(base, extraFiles, null, null);
    }

    public static DlcPackInstallPlan merge(DlcPackInstallPlan base, List<FileEntry> extraFiles, Path instanceRoot, Path dlcRoot) {
        if (base == null) {
            base = new DlcPackInstallPlan(List.of(), List.of());
        }
        List<FileEntry> files = new ArrayList<>(base.files);
        if (extraFiles != null) {
            for (FileEntry entry : extraFiles) {
                if (entry == null || entry.relativePath == null || entry.relativePath.isBlank()) {
                    continue;
                }
                if (isMetadataFile(entry.relativePath)) {
                    continue;
                }
                if (isIgnoredModpackJarPath(entry.relativePath)) {
                    continue;
                }
                if (isForbiddenPath(entry.relativePath, instanceRoot, dlcRoot)) {
                    continue;
                }
                boolean exists = false;
                for (FileEntry current : files) {
                    if (current.relativePath.equals(entry.relativePath)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    files.add(entry);
                }
            }
        }
        files.sort(Comparator.comparing(entry -> entry.relativePath.toLowerCase(Locale.ROOT)));
        return new DlcPackInstallPlan(files, base.forbiddenPaths);
    }

    public static boolean isForbiddenPath(String relativePath) {
        return isForbiddenPath(relativePath, null, null);
    }

    public static boolean isForbiddenPath(String relativePath, Path instanceRoot, Path dlcRoot) {
        String normalized = DlcPackMeta.normalizeRelativePath(relativePath);
        if (normalized == null) {
            return true;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (matchesForbiddenPathList(normalized, GAME_ROOT_FORBIDDEN_PATHS, null)) {
            return true;
        }
        return matchesForbiddenPathList(normalized, DLC_ROOT_FORBIDDEN_PATHS, relativeDlcRoot(instanceRoot, dlcRoot));
    }

    private static boolean matchesForbiddenPathList(String normalized, List<String> paths, String prefix) {
        if (paths == null || paths.isEmpty()) {
            return false;
        }
        String normalizedPrefix = normalizeForbiddenPath(prefix);
        for (String path : paths) {
            String resolved = normalizeForbiddenPath(path);
            if (resolved == null) {
                continue;
            }
            if (normalizedPrefix != null && !normalizedPrefix.isBlank()) {
                resolved = normalizedPrefix + "/" + resolved;
            }
            boolean includeChildren = resolved.endsWith("/");
            if (includeChildren) {
                resolved = resolved.substring(0, resolved.length() - 1);
            }
            if (normalized.equals(resolved) || includeChildren && normalized.startsWith(resolved + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeForbiddenPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        boolean includeChildren = path.replace('\\', '/').trim().endsWith("/");
        String normalized = DlcPackMeta.normalizeRelativePath(path);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return includeChildren ? normalized + "/" : normalized;
    }

    private static String relativeDlcRoot(Path instanceRoot, Path dlcRoot) {
        if (instanceRoot == null || dlcRoot == null) {
            return null;
        }
        Path normalizedInstance = instanceRoot.toAbsolutePath().normalize();
        Path normalizedDlc = dlcRoot.toAbsolutePath().normalize();
        if (!normalizedDlc.startsWith(normalizedInstance)) {
            return null;
        }
        String relative = DlcPackMeta.normalizeRelativePath(normalizedInstance.relativize(normalizedDlc).toString());
        return relative == null ? null : relative.toLowerCase(Locale.ROOT);
    }

    private static String normalizeRelative(Path path) {
        return DlcPackMeta.normalizeRelativePath(path.toString());
    }

    static boolean isMetadataFile(String relative) {
        String first = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : relative;
        return METADATA_DIR_NAMES.contains(first.toLowerCase(Locale.ROOT));
    }

    public static boolean isIgnoredModpackJarPath(String relativePath) {
        String normalized = DlcPackMeta.normalizeRelativePath(relativePath);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return isIgnoredModpackJarFileName(fileName);
    }

    public static boolean isIgnoredModpackJarFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String normalized = fileName.trim();
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return false;
        }
        for (String prefix : IGNORED_JAR_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static class FileEntry {
        public final Path source;
        public final String relativePath;

        public FileEntry(Path source, String relativePath) {
            this.source = source;
            this.relativePath = relativePath;
        }
    }

}
