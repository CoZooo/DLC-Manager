package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.client.core.DlcZipUtil;
import com.cozooo.dlc_manager.core.DlcTmpPaths;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
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
    private Path extractionRoot;
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
            ZipEntry entry = zipFile.getEntry("update.dc");
            if (entry == null) {
                entry = zipFile.getEntry("./update.dc");
            }
            if (entry == null) {
                entry = zipFile.getEntry("update.json");
                if (entry == null) {
                    entry = zipFile.getEntry("./update.json");
                }
            }
            if (entry == null) {
                for (var e = zipFile.entries(); e.hasMoreElements();) {
                    ZipEntry candidate = e.nextElement();
                    if (candidate == null) {
                        continue;
                    }
                    String name = candidate.getName().toLowerCase(Locale.ROOT);
                    if (name.endsWith("update.dc") || name.endsWith("update.json")) {
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
            JsonObject obj = null;
            try (var in = zipFile.getInputStream(entry)) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (entryName.toLowerCase(Locale.ROOT).endsWith(".dc")) {
                    String trimmed = text == null ? "" : text.stripLeading();
                    if (trimmed.startsWith("{")) {
                        obj = GSON.fromJson(trimmed, JsonObject.class);
                    } else {
                        obj = DlcUpdateDcParser.parse(text);
                        if (obj == null || obj.entrySet().isEmpty()) {
                            obj = DlcDcFormatUtil.readGenericText(text);
                        }
                    }
                }
                if (obj == null) {
                    obj = GSON.fromJson(text, JsonObject.class);
                }
            }
            if (obj == null) {
                return null;
            }
            DlcUpdateManifest manifest = DlcUpdateManifest.fromJsonObject(obj);
            return new ManifestInfo(manifest, root);
        } catch (Exception ignored) {
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
        Path stagingBase = DlcTmpPaths.resolve(dlcRoot, "_staging");
        Path target;
        try {
            target = DlcZipUtil.createUniqueDir(stagingBase, name);
        } catch (IOException ignored) {
            return null;
        }
        try {
            DlcZipUtil.extractZip(archivePath, target);
        } catch (IOException ignored) {
            DlcZipUtil.deleteRecursive(target);
            return null;
        }
        extractionRoot = target;
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
        Path root = extractionRoot == null ? stagingRoot : extractionRoot;
        if (root == null) {
            return;
        }
        DlcZipUtil.deleteRecursive(root);
        extractionRoot = null;
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
