package com.cozooo.dlc_manager.client.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DlcUpdatePackage {
    private static final Gson GSON = new GsonBuilder().create();

    private final Path archivePath;
    private final String name;
    private final DlcUpdateManifest manifest;
    private final String contentRoot;
    private Path stagingRoot;

    private DlcUpdatePackage(Path archivePath, String name, DlcUpdateManifest manifest, String contentRoot) {
        this.archivePath = archivePath;
        this.name = name;
        this.manifest = manifest;
        this.contentRoot = contentRoot == null ? "" : contentRoot;
    }

    public static DlcUpdatePackage fromZip(Path zipPath) {
        if (zipPath == null || !Files.exists(zipPath)) {
            return null;
        }
        ManifestInfo info = readManifest(zipPath);
        if (info == null || info.manifest == null) {
            return null;
        }
        String fileName = zipPath.getFileName() == null ? "update" : zipPath.getFileName().toString();
        String baseName = fileName.endsWith(".zip") ? fileName.substring(0, fileName.length() - 4) : fileName;
        return new DlcUpdatePackage(zipPath, baseName, info.manifest, info.contentRoot);
    }

    private static ManifestInfo readManifest(Path zipPath) {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zipFile.getEntry("update.json");
            if (entry == null) {
                entry = zipFile.getEntry("./update.json");
            }
            if (entry == null) {
                for (var e = zipFile.entries(); e.hasMoreElements();) {
                    ZipEntry candidate = e.nextElement();
                    if (candidate == null) {
                        continue;
                    }
                    String name = candidate.getName().toLowerCase(Locale.ROOT);
                    if (name.endsWith("update.json")) {
                        entry = candidate;
                        break;
                    }
                }
            }
            if (entry == null) {
                return null;
            }
            String entryName = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
            if (entryName.startsWith("./")) {
                entryName = entryName.substring(2);
            }
            String root = "";
            int slash = entryName.lastIndexOf('/');
            if (slash > 0) {
                root = entryName.substring(0, slash);
            }
            try (var reader = new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonReader jsonReader = new JsonReader(reader);
                jsonReader.setLenient(true);
                JsonObject obj = GSON.fromJson(jsonReader, JsonObject.class);
                DlcUpdateManifest manifest = DlcUpdateManifest.fromJsonObject(obj);
                return new ManifestInfo(manifest, root);
            }
        } catch (IOException ignored) {
            return null;
        }
    }

    public Path extractTo(Path dlcRoot) {
        if (stagingRoot != null) {
            return stagingRoot;
        }
        if (dlcRoot == null) {
            return null;
        }
        Path stagingBase = dlcRoot.resolve("update_tmp").resolve("_staging");
        String safeName = name == null ? "update" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = stagingBase.resolve(safeName + "-" + System.currentTimeMillis());
        try {
            Files.createDirectories(target);
        } catch (IOException ignored) {
            return null;
        }
        try (ZipFile zipFile = new ZipFile(archivePath.toFile(), StandardCharsets.UTF_8)) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry == null) {
                    continue;
                }
                String rawName = entry.getName();
                if (rawName == null || rawName.isBlank()) {
                    continue;
                }
                String normalizedName = rawName.replace('\\', '/');
                if (normalizedName.startsWith("__MACOSX/") || normalizedName.startsWith("._") || normalizedName.endsWith("/.DS_Store") || normalizedName.equals(".DS_Store")) {
                    continue;
                }
                Path outPath = target.resolve(rawName).normalize();
                if (!outPath.startsWith(target)) {
                    continue;
                }
                try {
                    if (entry.isDirectory()) {
                        Files.createDirectories(outPath);
                    } else {
                        Files.createDirectories(outPath.getParent());
                        try (var in = zipFile.getInputStream(entry)) {
                            Files.copy(in, outPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } catch (IOException ignored) {
                    // Best-effort per entry.
                }
            }
        } catch (IOException ignored) {
            // Best-effort extraction.
        }
        if (contentRoot != null && !contentRoot.isBlank()) {
            Path rootPath = target.resolve(contentRoot).normalize();
            if (rootPath.startsWith(target)) {
                stagingRoot = rootPath;
            } else {
                stagingRoot = target;
            }
        } else {
            stagingRoot = target;
        }
        return stagingRoot;
    }

    public void cleanupStaging() {
        if (stagingRoot == null) {
            return;
        }
        DlcUpdateService.deleteRecursive(stagingRoot);
        stagingRoot = null;
    }

    public Path getArchivePath() {
        return archivePath;
    }

    public String getName() {
        return name;
    }

    public DlcUpdateManifest getManifest() {
        return manifest;
    }

    public Path getStagingRoot() {
        return stagingRoot;
    }

    private static final class ManifestInfo {
        private final DlcUpdateManifest manifest;
        private final String contentRoot;

        private ManifestInfo(DlcUpdateManifest manifest, String contentRoot) {
            this.manifest = manifest;
            this.contentRoot = contentRoot == null ? "" : contentRoot;
        }
    }
}
