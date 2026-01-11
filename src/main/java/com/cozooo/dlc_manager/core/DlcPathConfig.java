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
    private static final boolean DEFAULT_ENABLE_ALL_SHOWN = true;
    private static final boolean DEFAULT_CONFIG_BUTTON_SHOWN = true;

    private static Path configPath;
    private static DlcPathData data;

    public static void load() {
        configPath = FMLPaths.CONFIGDIR.get().resolve("dlc_manager/config.json");
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                data = GSON.fromJson(reader, DlcPathData.class);
            } catch (IOException ignored) {
                data = null;
            }
        }
        if (data == null) {
            data = new DlcPathData();
            data.paths = new PathSection();
            data.paths.dlcRoot = DEFAULT_DLC_ROOT;
            data.paths.taczRoot = DEFAULT_TACZ_DLC_ROOT;
            data.paths.resourcepackRoot = DEFAULT_RESOURCEPACK_ROOT;
            data.paths.datapackRoot = DEFAULT_DATAPACK_ROOT;
            data.paths.otherRoot = DEFAULT_OTHER_ROOT;
            data.ui = new UiSection();
            data.ui.isEnableAllButtonShown = DEFAULT_ENABLE_ALL_SHOWN;
            data.ui.isConfigButtonShown = DEFAULT_CONFIG_BUTTON_SHOWN;
            save();
        } else {
            normalizeData();
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
        return resolvePaths(data.paths.dlcRoot, DEFAULT_DLC_ROOT);
    }

    public static List<Path> getTaczDlcRoots() {
        return resolvePathsRelativeToDlc(data.paths.taczRoot, DEFAULT_TACZ_DLC_ROOT);
    }

    public static List<Path> getResourcepackRoots() {
        return resolvePaths(data.paths.resourcepackRoot, DEFAULT_RESOURCEPACK_ROOT);
    }

    public static List<Path> getDatapackRoots() {
        return resolvePaths(data.paths.datapackRoot, DEFAULT_DATAPACK_ROOT);
    }

    public static List<Path> getOtherRoots() {
        return resolvePaths(data.paths.otherRoot, DEFAULT_OTHER_ROOT);
    }

    public static String getDlcRootRaw() {
        return data.paths.dlcRoot == null ? DEFAULT_DLC_ROOT : data.paths.dlcRoot;
    }

    public static String getTaczRootRaw() {
        return data.paths.taczRoot == null ? DEFAULT_TACZ_DLC_ROOT : data.paths.taczRoot;
    }

    public static String getResourcepackRootRaw() {
        return data.paths.resourcepackRoot == null ? DEFAULT_RESOURCEPACK_ROOT : data.paths.resourcepackRoot;
    }

    public static String getDatapackRootRaw() {
        return data.paths.datapackRoot == null ? DEFAULT_DATAPACK_ROOT : data.paths.datapackRoot;
    }

    public static String getOtherRootRaw() {
        return data.paths.otherRoot == null ? DEFAULT_OTHER_ROOT : data.paths.otherRoot;
    }

    public static boolean isEnableAllButtonShown() {
        return data.ui.isEnableAllButtonShown == null ? DEFAULT_ENABLE_ALL_SHOWN : data.ui.isEnableAllButtonShown;
    }

    public static boolean isConfigButtonShown() {
        return data.ui.isConfigButtonShown == null ? DEFAULT_CONFIG_BUTTON_SHOWN : data.ui.isConfigButtonShown;
    }

    public static void setDlcRoot(String value) {
        data.paths.dlcRoot = value;
    }

    public static void setTaczRoot(String value) {
        data.paths.taczRoot = value;
    }

    public static void setResourcepackRoot(String value) {
        data.paths.resourcepackRoot = value;
    }

    public static void setDatapackRoot(String value) {
        data.paths.datapackRoot = value;
    }

    public static void setOtherRoot(String value) {
        data.paths.otherRoot = value;
    }

    public static void setEnableAllButtonShown(boolean value) {
        data.ui.isEnableAllButtonShown = value;
    }

    public static void setConfigButtonShown(boolean value) {
        data.ui.isConfigButtonShown = value;
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

    private static void normalizeData() {
        if (data.paths == null) {
            data.paths = new PathSection();
        }
        if (data.ui == null) {
            data.ui = new UiSection();
        }
        if (data.paths.dlcRoot == null || data.paths.dlcRoot.isBlank()) {
            data.paths.dlcRoot = firstNonBlank(data.dlcRoot, DEFAULT_DLC_ROOT);
        }
        if (data.paths.taczRoot == null || data.paths.taczRoot.isBlank()) {
            data.paths.taczRoot = firstNonBlank(data.taczRoot, DEFAULT_TACZ_DLC_ROOT);
        }
        if (data.paths.resourcepackRoot == null || data.paths.resourcepackRoot.isBlank()) {
            data.paths.resourcepackRoot = firstNonBlank(data.resourcepackRoot, DEFAULT_RESOURCEPACK_ROOT);
        }
        if (data.paths.datapackRoot == null || data.paths.datapackRoot.isBlank()) {
            data.paths.datapackRoot = firstNonBlank(data.datapackRoot, DEFAULT_DATAPACK_ROOT);
        }
        if (data.paths.otherRoot == null || data.paths.otherRoot.isBlank()) {
            data.paths.otherRoot = firstNonBlank(data.otherRoot, DEFAULT_OTHER_ROOT);
        }
        if (data.ui.isEnableAllButtonShown == null) {
            data.ui.isEnableAllButtonShown = data.isEnableAllButtonShown != null
                ? data.isEnableAllButtonShown
                : DEFAULT_ENABLE_ALL_SHOWN;
        }
        if (data.ui.isConfigButtonShown == null) {
            data.ui.isConfigButtonShown = data.isConfigButtonShown != null
                ? data.isConfigButtonShown
                : DEFAULT_CONFIG_BUTTON_SHOWN;
        }
        data.dlcRoot = null;
        data.taczRoot = null;
        data.resourcepackRoot = null;
        data.datapackRoot = null;
        data.otherRoot = null;
        data.isEnableAllButtonShown = null;
        data.isConfigButtonShown = null;
        save();
    }

    private static String firstNonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static class DlcPathData {
        PathSection paths;
        UiSection ui;
        // Legacy flat fields for migration
        String dlcRoot;
        String taczRoot;
        String resourcepackRoot;
        String datapackRoot;
        String otherRoot;
        Boolean isEnableAllButtonShown;
        Boolean isConfigButtonShown;
    }

    private static class PathSection {
        String dlcRoot;
        String taczRoot;
        String resourcepackRoot;
        String datapackRoot;
        String otherRoot;
    }

    private static class UiSection {
        Boolean isEnableAllButtonShown;
        Boolean isConfigButtonShown;
    }
}
