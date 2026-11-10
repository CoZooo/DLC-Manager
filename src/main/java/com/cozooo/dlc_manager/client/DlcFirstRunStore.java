package com.cozooo.dlc_manager.client;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcUserPaths;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DlcFirstRunStore {
    private static final String STORE_FILE = "first-run.dc";
    private static final String LEGACY_MARKER = "ShownOnFirstRun";
    private static final String SHOWN_KEY = "shown";

    private DlcFirstRunStore() {
    }

    public static Path defaultStorePath() {
        return DlcUserPaths.stateFile(STORE_FILE);
    }

    public static boolean wasShown(Path dlcRoot) {
        return wasShown(dlcRoot, defaultStorePath());
    }

    public static boolean wasShown(Path dlcRoot, Path storePath) {
        if (dlcRoot == null || storePath == null) {
            return false;
        }
        String key = keyFor(dlcRoot);
        if (clearLegacyMarker(dlcRoot)) {
            markShown(dlcRoot, storePath);
            return true;
        }
        return readKeys(storePath).contains(key);
    }

    public static boolean markShown(Path dlcRoot) {
        return markShown(dlcRoot, defaultStorePath());
    }

    public static boolean markShown(Path dlcRoot, Path storePath) {
        if (dlcRoot == null || storePath == null) {
            return false;
        }
        clearLegacyMarker(dlcRoot);
        String key = keyFor(dlcRoot);
        Set<String> keys = readKeys(storePath);
        if (!keys.add(key)) {
            return true;
        }
        JsonObject object = new JsonObject();
        JsonArray shown = new JsonArray();
        for (String existing : keys) {
            shown.add(existing);
        }
        object.add(SHOWN_KEY, shown);
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return DlcDcConfigIO.write(storePath, object, DlcDcConfigIO.Layout.GENERIC);
        } catch (IOException ignored) {
            return false;
        }
    }

    static String keyFor(Path dlcRoot) {
        String modpack = DlcConfig.getModpack();
        if (modpack == null || modpack.isBlank()) {
            return "path:" + dlcRoot.toAbsolutePath().normalize();
        }
        return "pack:" + modpack.trim() + "@" + installedVersion(dlcRoot);
    }

    private static String installedVersion(Path dlcRoot) {
        Path path = DlcPaths.resolveVersionConfigPath(dlcRoot);
        JsonObject object = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
        String version = DlcDcConfigIO.getStringIgnoreCase(object, "version");
        if (version == null || version.isBlank()) {
            version = DlcDcConfigIO.getStringIgnoreCase(object, "base");
        }
        return version == null ? "" : version.trim();
    }

    private static Set<String> readKeys(Path storePath) {
        Set<String> keys = new LinkedHashSet<>();
        JsonObject object = DlcDcConfigIO.read(storePath, DlcDcConfigIO.Layout.GENERIC);
        JsonArray shown = DlcDcConfigIO.getArrayIgnoreCase(object, SHOWN_KEY);
        if (shown == null) {
            return keys;
        }
        for (JsonElement element : shown) {
            if (element != null && element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value != null && !value.isBlank()) {
                    keys.add(value);
                }
            }
        }
        return keys;
    }

    private static boolean clearLegacyMarker(Path dlcRoot) {
        Path legacy = dlcRoot.resolve(LEGACY_MARKER);
        try {
            return Files.deleteIfExists(legacy);
        } catch (IOException ignored) {
            return false;
        }
    }
}
