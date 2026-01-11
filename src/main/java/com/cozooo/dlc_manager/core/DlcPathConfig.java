package com.cozooo.dlc_manager.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DlcPathConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_DLC_ROOT = "DLC";
    private static final String DEFAULT_TACZ_DLC_ROOT = "tacz";
    private static final String DEFAULT_RESOURCEPACK_ROOT = "resourcepacks";
    private static final String DEFAULT_DATAPACK_ROOT = "datapacks";
    private static final String DEFAULT_OTHER_ROOT = "dlc_other";

    private static Path configPath;
    private static DlcPathData data;

    public static void load() {
        configPath = FMLPaths.CONFIGDIR.get().resolve("dlc_manager/paths.json");
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                data = GSON.fromJson(reader, DlcPathData.class);
            } catch (IOException ignored) {
                data = null;
            }
        }
        if (data == null) {
            data = new DlcPathData();
            data.dlcRoot = DEFAULT_DLC_ROOT;
            data.taczRoot = DEFAULT_TACZ_DLC_ROOT;
            data.resourcepackRoot = DEFAULT_RESOURCEPACK_ROOT;
            data.datapackRoot = DEFAULT_DATAPACK_ROOT;
            data.otherRoot = DEFAULT_OTHER_ROOT;
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException ignored) {
            // Best-effort persistence.
        }
    }

    public static Path getDlcRoot() {
        return getDlcRoots().get(0);
    }

    public static Path getTaczRoot() {
        return getTaczDlcRoots().get(0);
    }

    public static Path getResourcepackRoot() {
        return getResourcepackRoots().get(0);
    }

    public static Path getDatapackRoot() {
        return getDatapackRoots().get(0);
    }

    public static Path getOtherRoot() {
        return getOtherRoots().get(0);
    }

    public static List<Path> getDlcRoots() {
        return resolvePaths(data.dlcRoot, DEFAULT_DLC_ROOT);
    }

    public static List<Path> getTaczDlcRoots() {
        return resolvePathsRelativeToDlc(data.taczRoot, DEFAULT_TACZ_DLC_ROOT);
    }

    public static List<Path> getResourcepackRoots() {
        return resolvePaths(data.resourcepackRoot, DEFAULT_RESOURCEPACK_ROOT);
    }

    public static List<Path> getDatapackRoots() {
        return resolvePaths(data.datapackRoot, DEFAULT_DATAPACK_ROOT);
    }

    public static List<Path> getOtherRoots() {
        return resolvePaths(data.otherRoot, DEFAULT_OTHER_ROOT);
    }

    public static String getDlcRootRaw() {
        return data.dlcRoot == null ? DEFAULT_DLC_ROOT : data.dlcRoot;
    }

    public static String getTaczRootRaw() {
        return data.taczRoot == null ? DEFAULT_TACZ_DLC_ROOT : data.taczRoot;
    }

    public static String getResourcepackRootRaw() {
        return data.resourcepackRoot == null ? DEFAULT_RESOURCEPACK_ROOT : data.resourcepackRoot;
    }

    public static String getDatapackRootRaw() {
        return data.datapackRoot == null ? DEFAULT_DATAPACK_ROOT : data.datapackRoot;
    }

    public static String getOtherRootRaw() {
        return data.otherRoot == null ? DEFAULT_OTHER_ROOT : data.otherRoot;
    }

    public static void setDlcRoot(String value) {
        data.dlcRoot = value;
    }

    public static void setTaczRoot(String value) {
        data.taczRoot = value;
    }

    public static void setResourcepackRoot(String value) {
        data.resourcepackRoot = value;
    }

    public static void setDatapackRoot(String value) {
        data.datapackRoot = value;
    }

    public static void setOtherRoot(String value) {
        data.otherRoot = value;
    }

    private static Path resolvePath(String value, String fallback) {
        String raw = value == null || value.isBlank() ? fallback : value;
        Path path = Path.of(raw);
        if (!path.isAbsolute()) {
            path = FMLPaths.GAMEDIR.get().resolve(path);
        }
        return path.normalize();
    }

    private static List<Path> resolvePaths(String value, String fallback) {
        String raw = value == null || value.isBlank() ? fallback : value;
        String[] parts = raw.split(",");
        List<Path> paths = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            paths.add(resolvePath(trimmed, fallback));
        }
        if (paths.isEmpty()) {
            paths.add(resolvePath(fallback, fallback));
        }
        return paths;
    }

    private static List<Path> resolvePathsRelativeToDlc(String value, String fallback) {
        String raw = value == null || value.isBlank() ? fallback : value;
        String[] parts = raw.split(",");
        java.util.LinkedHashSet<Path> paths = new java.util.LinkedHashSet<>();
        List<Path> dlcRoots = getDlcRoots();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Path candidate = Path.of(trimmed);
            if (candidate.isAbsolute()) {
                paths.add(candidate.normalize());
            } else {
                boolean added = false;
                for (Path dlcRoot : dlcRoots) {
                    Path resolved = dlcRoot.resolve(trimmed).normalize();
                    if (Files.exists(resolved)) {
                        paths.add(resolved);
                        added = true;
                    }
                }
                if (!added) {
                    Path gameDirPath = FMLPaths.GAMEDIR.get().resolve(trimmed).normalize();
                    if (Files.exists(gameDirPath)) {
                        paths.add(gameDirPath);
                        added = true;
                    }
                }
                if (!added && !dlcRoots.isEmpty()) {
                    paths.add(dlcRoots.get(0).resolve(trimmed).normalize());
                }
            }
        }
        if (paths.isEmpty()) {
            boolean added = false;
            for (Path dlcRoot : dlcRoots) {
                Path resolved = dlcRoot.resolve(fallback).normalize();
                if (Files.exists(resolved)) {
                    paths.add(resolved);
                    added = true;
                }
            }
            if (!added) {
                Path gameDirPath = FMLPaths.GAMEDIR.get().resolve(fallback).normalize();
                if (Files.exists(gameDirPath)) {
                    paths.add(gameDirPath);
                } else if (!dlcRoots.isEmpty()) {
                    paths.add(dlcRoots.get(0).resolve(fallback).normalize());
                }
            }
        }
        return new ArrayList<>(paths);
    }

    private static class DlcPathData {
        String dlcRoot;
        String taczRoot;
        String resourcepackRoot;
        String datapackRoot;
        String otherRoot;
    }
}
