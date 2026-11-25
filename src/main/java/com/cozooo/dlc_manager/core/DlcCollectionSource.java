package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.client.core.DlcZipUtil;
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.client.pack.DlcPackCFResolver;
import com.cozooo.dlc_manager.client.pack.DlcPackInstallPlan;
import com.cozooo.dlc_manager.client.pack.DlcPackManifest;
import com.cozooo.dlc_manager.client.pack.DlcPackMetadata;
import com.cozooo.dlc_manager.client.pack.DlcPackModrinthManifest;
import com.cozooo.dlc_manager.client.pack.DlcPackPackage;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DlcCollectionSource {
    private static final Set<String> GAME_ROOT_DIRECTORIES = Set.of(
        "mods", "config", "kubejs", "resourcepacks", "shaderpacks", "datapacks",
        "defaultconfigs", "scripts", "openloader", "tacz", "saves", "worlds"
    );

    private DlcCollectionSource() {
    }

    public static Path resolve(Path dlcRoot, String raw) {
        if (dlcRoot == null || raw == null || raw.isBlank()) return null;
        Path parsed;
        try {
            parsed = Path.of(raw.replace('\\', '/').trim());
        } catch (RuntimeException ignored) {
            return null;
        }
        if (parsed.isAbsolute()) return null;
        Path root = dlcRoot.toAbsolutePath().normalize();
        Path resolved = root.resolve(parsed).normalize();
        return resolved.startsWith(root) ? resolved : null;
    }

    public static List<DlcPackInstallPlan.FileEntry> entries(Path source, Path dlcRoot, Path gameRoot, boolean allowDownloads) throws IOException {
        return entries(source, dlcRoot, gameRoot, allowDownloads, null, List.of());
    }

    public static List<DlcPackInstallPlan.FileEntry> entries(Path source, Path dlcRoot, Path gameRoot, boolean allowDownloads, String collectionIdentifier) throws IOException {
        return entries(source, dlcRoot, gameRoot, allowDownloads, collectionIdentifier, List.of());
    }

    public static List<DlcPackInstallPlan.FileEntry> entries(Path source, Path dlcRoot, Path gameRoot, boolean allowDownloads, String collectionIdentifier, List<JsonObject> remoteEntries) throws IOException {
        if (source == null || dlcRoot == null || gameRoot == null) return List.of();
        Path packageRoot = materialize(source, dlcRoot);
        CollectionInfo info = inspect(packageRoot, dlcRoot, source);
        if (info == null) return List.of();
        DlcPackInstallPlan plan = DlcPackInstallPlan.build(info.pack, gameRoot, dlcRoot);
        if (info.pack.format == DlcPackPackage.Format.STANDARD && (remoteEntries == null || remoteEntries.isEmpty())) return plan.files;
        List<DlcPackInstallPlan.FileEntry> cached = new ArrayList<>();
        for (DlcRequiredEntry entry : completionEntries(info, dlcRoot, collectionIdentifier, remoteEntries)) {
            String name = entry.getRequiredFileName();
            if (name == null || name.isBlank()) name = entry.getResolvedFileName();
            if (name == null || name.isBlank()) name = DlcRequiredService.resolveExistingFileName(entry);
            Path path = entry.resolveRequiredPath(name);
            if (path != null && Files.isRegularFile(path)) {
                for (Path target : entry.resolveInstallationTargets(name)) {
                    cached.add(new DlcPackInstallPlan.FileEntry(path, DlcPathUtil.toGameRelativePath(target)));
                }
            }
        }
        return DlcPackInstallPlan.merge(plan, allowDownloads ? cached : List.of()).files;
    }

    public static List<DlcRequiredEntry> completionEntries(Path source, Path dlcRoot, String collectionIdentifier) throws IOException {
        return completionEntries(source, dlcRoot, collectionIdentifier, List.of());
    }

    public static List<DlcRequiredEntry> completionEntries(Path source, Path dlcRoot, String collectionIdentifier, List<JsonObject> remoteEntries) throws IOException {
        if (source == null || dlcRoot == null) return List.of();
        Path packageRoot = materialize(source, dlcRoot);
        CollectionInfo info = inspect(packageRoot, dlcRoot, source);
        return completionEntries(info, dlcRoot, collectionIdentifier, remoteEntries);
    }

    private static List<DlcRequiredEntry> completionEntries(CollectionInfo info, Path dlcRoot, String collectionIdentifier, List<JsonObject> remoteEntries) throws IOException {
        if (info == null) return List.of();
        Path requiredDir = dlcRoot.resolve("required");
        String prefix = collectionIdentifier == null || collectionIdentifier.isBlank() ? "collection" : collectionIdentifier;
        List<DlcRequiredEntry> result = new ArrayList<>();
        try {
            if (info.pack.format == DlcPackPackage.Format.CURSEFORGE) {
                for (DlcPackCFResolver.ManifestRequirement requirement : DlcPackCFResolver.resolveManifestRequirements(info.pack)) {
                    DlcRequiredEntry entry = new DlcRequiredEntry(null, requiredDir);
                    entry.setIdentifier(prefix + ":" + requirement.relativePath);
                    entry.setRequiredFileName(requirement.fileName);
                    entry.setCurseforgeProjectId(requirement.projectId);
                    entry.setCurseforgeFileId(requirement.fileId);
                    entry.setAppliedTargetRaw(requirement.relativePath);
                    result.add(entry);
                }
            } else if (info.pack.format == DlcPackPackage.Format.MODRINTH) {
                for (DlcPackModrinthManifest.FileRef file : DlcPackModrinthManifest.resolveManifestRequirements(info.pack)) {
                    DlcRequiredEntry entry = new DlcRequiredEntry(null, requiredDir);
                    entry.setIdentifier(prefix + ":" + file.path);
                    entry.setRequiredFileName(Path.of(file.path).getFileName().toString());
                    entry.setDirectUrls(file.downloads);
                    entry.setAppliedTargetRaw(file.path);
                    result.add(entry);
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Collection file resolution was interrupted", error);
        }
        if (remoteEntries != null) {
            for (JsonObject remote : remoteEntries) {
                String identifier = stringValue(remote, "identifier");
                if (identifier == null || identifier.isBlank()) continue;
                DlcRequiredEntry entry = new DlcRequiredEntry(null, requiredDir);
                entry.setIdentifier(prefix + ":remote:" + identifier);
                entry.setRequiredFileName(stringValue(remote, "source"));
                entry.setModrinthProjectId(stringValue(remote, "modrinth_projectid"));
                entry.setModrinthVersionId(stringValue(remote, "modrinth_versionid"));
                entry.setCurseforgeProjectId(stringValue(remote, "curseforge_projectid"));
                entry.setCurseforgeFileId(stringValue(remote, "curseforge_fileid"));
                entry.setDirectUrls(stringList(remote.get("directUrl")));
                entry.setDownloadPriority(DlcDownloadCandidate.readPriority(remote));
                entry.setAppliedTargetRaw(stringValue(remote, "target"));
                result.add(entry);
            }
        }
        return result;
    }

    public static List<DlcRequiredEntry> completionEntries(DlcItem item) throws IOException {
        if (item == null || item.getType() != DlcType.COLLECTION) return List.of();
        return completionEntries(item, new HashSet<>());
    }

    private static List<DlcRequiredEntry> completionEntries(DlcItem item, Set<String> visited) throws IOException {
        List<DlcRequiredEntry> result = new ArrayList<>();
        String id = item.getId();
        if (id != null && !visited.add(id)) return result;
        List<Path> sources = item.getSourcePaths().isEmpty() ? (item.getSourcePath() == null ? List.of() : List.of(item.getSourcePath())) : item.getSourcePaths();
        for (Path source : sources) result.addAll(completionEntries(source, item.getDlcRoot(), item.getIdentifier(), item.getCollectionRemoteEntries()));
        for (DlcItem included : item.getCollectionIncludedItems()) result.addAll(completionEntries(included, visited));
        if (id != null) visited.remove(id);
        return result;
    }

    public static Path materialize(Path source, Path dlcRoot) throws IOException {
        Path normalized = source.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) return normalized;
        if (Files.isRegularFile(normalized) && isManifestFile(normalized)) return normalized;
        if (!Files.isRegularFile(normalized) || !isArchive(normalized)) {
            throw new IOException("Collection source is not a directory, metadata file, or archive: " + source);
        }
        Path cache = dlcRoot.toAbsolutePath().normalize().resolve("tmp/collection").resolve(cacheKey(normalized));
        Path content = cache.resolve("content");
        Path marker = cache.resolve("complete");
        if (!Files.isRegularFile(marker)) {
            DlcZipUtil.deleteRecursive(content);
            Files.createDirectories(content);
            DlcZipUtil.extractZip(normalized, content);
            Files.writeString(marker, "1");
        }
        return normalizeGameRoot(content);
    }

    private static CollectionInfo inspect(Path root, Path dlcRoot, Path source) throws IOException {
        if (Files.isRegularFile(source) && isManifestFile(source)) return inspectMetadata(source, dlcRoot);
        Path normalized = normalizeGameRoot(root);
        Path cacheRoot = dlcRoot.toAbsolutePath().normalize().resolve("tmp/collection").resolve(Files.isRegularFile(source) ? cacheKey(source) : "dir-" + cacheKeyForDirectory(source));
        Path index = normalized.resolve("modrinth.index.json");
        if (Files.isRegularFile(index)) {
            var pack = new DlcPackPackage(
                normalized, DlcPackPackage.Format.MODRINTH, normalized, normalized.resolve("overrides"), normalized,
                index, null, index, List.of(), syntheticMetadata(), DlcPackModrinthManifest.read(index)
            );
            return new CollectionInfo(pack, cacheRoot);
        }
        Path manifest = normalized.resolve("manifest.json");
        if (Files.isRegularFile(manifest)) {
            var pack = new DlcPackPackage(
                normalized, DlcPackPackage.Format.CURSEFORGE, normalized, normalized.resolve("overrides"), normalized,
                manifest, null, manifest, DlcPackManifest.read(manifest).files, syntheticMetadata()
            );
            return new CollectionInfo(pack, cacheRoot);
        }
        var pack = new DlcPackPackage(
            normalized, DlcPackPackage.Format.STANDARD, normalized, normalized, normalized,
            normalized, null, null, List.of(), syntheticMetadata()
        );
        return new CollectionInfo(pack, cacheRoot);
    }

    private static CollectionInfo inspectMetadata(Path source, Path dlcRoot) throws IOException {
        Path parent = source.getParent() == null ? dlcRoot : source.getParent();
        Path emptyRoot = dlcRoot.resolve("tmp/collection").resolve("standalone-empty").resolve(cacheKey(source));
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.equals("modrinth.index.json")) {
            var pack = new DlcPackPackage(parent, DlcPackPackage.Format.MODRINTH, emptyRoot, emptyRoot, parent,
                source, null, source, List.of(), syntheticMetadata(), DlcPackModrinthManifest.read(source));
            return new CollectionInfo(pack, dlcRoot.resolve("tmp/collection").resolve(cacheKey(source)));
        }
        var pack = new DlcPackPackage(parent, DlcPackPackage.Format.CURSEFORGE, emptyRoot, emptyRoot, parent,
            source, null, source, DlcPackManifest.read(source).files, syntheticMetadata());
        return new CollectionInfo(pack, dlcRoot.resolve("tmp/collection").resolve(cacheKey(source)));
    }

    private static Path normalizeGameRoot(Path root) {
        try {
            List<Path> children;
            try (var stream = Files.list(root)) {
                children = stream.toList();
            }
            List<Path> directories = children.stream().filter(Files::isDirectory).toList();
            boolean hasFiles = children.stream().anyMatch(Files::isRegularFile);
            if (!hasFiles && directories.size() == 1) {
                Path candidate = directories.get(0);
                if (Files.isRegularFile(candidate.resolve("manifest.json")) || Files.isRegularFile(candidate.resolve("modrinth.index.json"))) {
                    return candidate;
                }
                long recognized = 0;
                try (var stream = Files.list(candidate)) {
                    recognized = stream.filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                        .filter(GAME_ROOT_DIRECTORIES::contains)
                        .distinct()
                        .count();
                }
                if (recognized >= 2) return candidate;
            }
        } catch (IOException ignored) {
        }
        return root;
    }

    private static boolean isArchive(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip") || name.endsWith(".mrpack");
    }

    private static boolean isManifestFile(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals("manifest.json") || name.equals("modrinth.index.json");
    }

    private static String stringValue(JsonObject obj, String key) {
        JsonElement value = obj.get(key);
        return value != null && value.isJsonPrimitive() && !value.getAsString().isBlank() ? value.getAsString().trim() : null;
    }

    private static List<String> stringList(JsonElement element) {
        if (element == null || element.isJsonNull()) return List.of();
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            return value.isBlank() ? List.of() : List.of(value);
        }
        if (!element.isJsonArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) if (value.isJsonPrimitive() && !value.getAsString().isBlank()) result.add(value.getAsString().trim());
        return result;
    }

    private static String cacheKey(Path path) throws IOException {
        String value = path + "\u0000" + Files.getLastModifiedTime(path).toMillis() + "\u0000" + Files.size(path);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte valueByte : digest) result.append(String.format("%02x", valueByte));
            return result.toString();
        } catch (Exception error) {
            throw new IOException("Unable to create collection cache key", error);
        }
    }

    private static String cacheKeyForDirectory(Path path) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(path.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception error) {
            return Integer.toHexString(path.toAbsolutePath().normalize().toString().hashCode());
        }
    }

    private static DlcPackMetadata syntheticMetadata() {
        return new DlcPackMetadata(
            "collection", "0", "universal", "", "", List.of("Unknown Author"), List.of(), false,
            List.of(), List.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), List.of(), List.of()
        );
    }

    private static final class CollectionInfo {
        private final DlcPackPackage pack;
        private final Path cacheRoot;

        private CollectionInfo(DlcPackPackage pack, Path cacheRoot) {
            this.pack = pack;
            this.cacheRoot = cacheRoot;
        }
    }
}
