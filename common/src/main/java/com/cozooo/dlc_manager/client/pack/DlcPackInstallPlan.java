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

    public final List<FileEntry> files;
    public final List<String> forbiddenPaths;

    private DlcPackInstallPlan(List<FileEntry> files, List<String> forbiddenPaths) {
        this.files = List.copyOf(files);
        this.forbiddenPaths = List.copyOf(forbiddenPaths);
    }

    public boolean getValid() {
        return forbiddenPaths.isEmpty();
    }

    public static DlcPackInstallPlan build(DlcPackPackage pack) {
        if (pack == null || !Files.isDirectory(pack.installRoot)) {
            return new DlcPackInstallPlan(List.of(), List.of());
        }
        List<FileEntry> files = new ArrayList<>();
        List<String> forbidden = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(pack.installRoot)) {
            stream.filter(Files::isRegularFile).forEach(source -> {
                String relative = normalizeRelative(pack.installRoot.relativize(source));
                if (relative == null || isMetadataFile(relative)) {
                    return;
                }
                if (isForbiddenPath(relative)) {
                    forbidden.add(relative);
                }
                files.add(new FileEntry(source, relative));
            });
        } catch (IOException ignored) {
        }
        files.sort(Comparator.comparing(entry -> entry.relativePath.toLowerCase(Locale.ROOT)));
        forbidden.sort(String::compareTo);
        return new DlcPackInstallPlan(files, forbidden);
    }

    public static DlcPackInstallPlan merge(DlcPackInstallPlan base, List<FileEntry> extraFiles) {
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
                if (isForbiddenPath(entry.relativePath)) {
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
        String normalized = DlcPackMeta.normalizeRelativePath(relativePath);
        if (normalized == null) {
            return true;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return normalized.equals("config/dlc_manager")
            || normalized.startsWith("config/dlc_manager/")
            || normalized.equals("dlc/tmp")
            || normalized.startsWith("dlc/tmp/")
            || normalized.equals("dlc/info/version.dc")
            || normalized.equals("dlc/info/notice.dc")
            || normalized.equals("dlc/required/notice.dc")
            || normalized.equals("dlc/required/skip")
            || normalized.equals("dlc/update")
            || normalized.startsWith("dlc/update/")
            || normalized.equals("dlc/shownonfirstrun");
    }

    private static String normalizeRelative(Path path) {
        return DlcPackMeta.normalizeRelativePath(path.toString());
    }

    private static boolean isMetadataFile(String relative) {
        String first = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : relative;
        return METADATA_DIR_NAMES.contains(first.toLowerCase(Locale.ROOT));
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
