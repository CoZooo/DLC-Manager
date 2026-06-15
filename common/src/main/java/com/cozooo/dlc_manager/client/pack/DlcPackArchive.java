package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.core.DlcZipUtil;
import com.cozooo.dlc_manager.core.DcConfigIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

public final class DlcPackArchive {
    private static final String[] METADATA_PATHS = {
        "overrides/metadata/pack.dc",
        "overrides/dlcpack_metadata/pack.dc",
        "metadata/pack.dc",
        "dlcpack_metadata/pack.dc"
    };

    private DlcPackArchive() {
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
                    DlcPackMetadata.Result parsed = DlcPackMetadata.read(DcConfigIO.read(metadata, DcConfigIO.Layout.GENERIC));
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

        public ExtractResult(Path stagingDir) {
            this.stagingDir = stagingDir;
        }
    }
}
