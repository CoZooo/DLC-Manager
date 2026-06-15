package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.core.DcConfigIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class DlcPackPackage {
    private static final String[] METADATA_DIRS = {"metadata", "dlcpack_metadata"};
    private static final String[] ICON_EXTENSIONS = {"png", "jpg", "jpeg", "icn", "ico", "webp"};

    public final Path root;
    public final Format format;
    public final Path contentRoot;
    public final Path installRoot;
    public final Path metadataDir;
    public final Path packFile;
    public final Path iconFile;
    public final Path manifestFile;
    public final List<DlcPackManifest.FileRef> manifestFiles;
    public final DlcPackMetadata metadata;

    public DlcPackPackage(Path root, Format format, Path contentRoot, Path installRoot, Path metadataDir, Path packFile, Path iconFile, Path manifestFile,
                          List<DlcPackManifest.FileRef> manifestFiles, DlcPackMetadata metadata) {
        this.root = root;
        this.format = format;
        this.contentRoot = contentRoot;
        this.installRoot = installRoot;
        this.metadataDir = metadataDir;
        this.packFile = packFile;
        this.iconFile = iconFile;
        this.manifestFile = manifestFile;
        this.manifestFiles = manifestFiles == null ? List.of() : List.copyOf(manifestFiles);
        this.metadata = metadata;
    }

    public static Result inspect(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return Result.invalid("package root is not a directory");
        }
        Path root = path.toAbsolutePath().normalize();
        Result cf = detectCurseForge(root);
        if (cf.valid) {
            return cf;
        }
        return detectStandard(root);
    }

    private static Result detectCurseForge(Path root) {
        for (Path candidate : contentRootCandidates(root)) {
            Path manifestFile = candidate.resolve("manifest.json");
            if (!Files.exists(manifestFile) || !Files.isDirectory(candidate.resolve("overrides"))) {
                continue;
            }
            MetadataLocation metadata = findCurseForgeMetadata(candidate);
            if (metadata == null) {
                continue;
            }
            DlcPackMetadata.Result parsed = readMetadata(metadata.packFile);
            if (!parsed.valid || parsed.metadata == null) {
                return Result.invalid(parsed.error == null ? "invalid pack.dc" : parsed.error);
            }
            DlcPackManifest manifest = DlcPackManifest.read(manifestFile);
            return Result.valid(new DlcPackPackage(
                root,
                Format.CURSEFORGE,
                candidate,
                candidate.resolve("overrides"),
                metadata.dir,
                metadata.packFile,
                metadata.iconFile,
                manifestFile,
                manifest.files,
                parsed.metadata
            ));
        }
        return Result.invalid("missing CurseForge metadata");
    }

    private static Result detectStandard(Path root) {
        for (Path candidate : contentRootCandidates(root)) {
            MetadataLocation metadata = findMetadata(candidate);
            if (metadata == null) {
                continue;
            }
            DlcPackMetadata.Result parsed = readMetadata(metadata.packFile);
            if (!parsed.valid || parsed.metadata == null) {
                return Result.invalid(parsed.error == null ? "invalid pack.dc" : parsed.error);
            }
            return Result.valid(new DlcPackPackage(root, Format.STANDARD, candidate, candidate, metadata.dir, metadata.packFile, metadata.iconFile, null, List.of(), parsed.metadata));
        }
        return Result.invalid("missing metadata/pack.dc");
    }

    private static List<Path> contentRootCandidates(Path root) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(root);
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .forEach(candidates::add);
        } catch (IOException ignored) {
        }
        return candidates;
    }

    private static MetadataLocation findCurseForgeMetadata(Path contentRoot) {
        MetadataLocation overrides = findMetadata(contentRoot.resolve("overrides"));
        return overrides != null ? overrides : findMetadata(contentRoot);
    }

    private static MetadataLocation findMetadata(Path contentRoot) {
        for (String name : METADATA_DIRS) {
            Path dir = contentRoot.resolve(name);
            Path pack = dir.resolve("pack.dc");
            if (Files.isRegularFile(pack)) {
                return new MetadataLocation(dir, pack, findIcon(dir));
            }
        }
        return null;
    }

    private static Path findIcon(Path dir) {
        for (String ext : ICON_EXTENSIONS) {
            Path icon = dir.resolve("icon." + ext);
            if (Files.isRegularFile(icon)) {
                return icon;
            }
        }
        return null;
    }

    private static DlcPackMetadata.Result readMetadata(Path path) {
        return DlcPackMetadata.read(DcConfigIO.read(path, DcConfigIO.Layout.GENERIC));
    }

    public enum Format {
        STANDARD,
        CURSEFORGE
    }

    private static class MetadataLocation {
        private final Path dir;
        private final Path packFile;
        private final Path iconFile;

        private MetadataLocation(Path dir, Path packFile, Path iconFile) {
            this.dir = dir;
            this.packFile = packFile;
            this.iconFile = iconFile;
        }
    }

    public static class Result {
        public final boolean valid;
        public final DlcPackPackage pack;
        public final String error;

        private Result(boolean valid, DlcPackPackage pack, String error) {
            this.valid = valid;
            this.pack = pack;
            this.error = error;
        }

        public static Result valid(DlcPackPackage pack) {
            return new Result(true, pack, null);
        }

        public static Result invalid(String error) {
            return new Result(false, null, error);
        }
    }
}
