package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.core.DcConfigIO;
import com.cozooo.dlc_manager.core.Json5Util;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DlcUpdateSkipStore {
    public static final String SECTION_SELF_UPDATE = "self_update";
    public static final String SECTION_HOT_UPDATE = "hot_update";
    public static final String SECTION_PACK_UPDATE = "pack_update";

    private static final String STORE_BASENAME = "update_skip";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();

    private DlcUpdateSkipStore() {
    }

    public static JsonObject readSection(String sectionKey) {
        synchronized (LOCK) {
            JsonObject root = readStoreLocked();
            if (!root.has(sectionKey) || !root.get(sectionKey).isJsonObject()) {
                return null;
            }
            return root.getAsJsonObject(sectionKey).deepCopy();
        }
    }

    public static void writeSection(String sectionKey, JsonObject section) {
        synchronized (LOCK) {
            JsonObject root = readStoreLocked();
            if (section == null || section.entrySet().isEmpty()) {
                root.remove(sectionKey);
            } else {
                root.add(sectionKey, section.deepCopy());
            }
            writeStoreLocked(root);
        }
    }

    public static void removeSection(String sectionKey) {
        writeSection(sectionKey, null);
    }

    private static JsonObject readStoreLocked() {
        Path storePath = resolveStorePath(STORE_BASENAME);
        JsonObject root = null;
        if (storePath != null && Files.exists(storePath)) {
            root = DcConfigIO.read(storePath, DcConfigIO.Layout.GENERIC);
        }
        if (root == null) {
            root = new JsonObject();
        }
        return root;
    }

    private static void writeStoreLocked(JsonObject root) {
        Path storePath = resolveStorePath(STORE_BASENAME);
        if (storePath == null) {
            return;
        }
        DcConfigIO.write(storePath, root == null ? new JsonObject() : root, DcConfigIO.Layout.GENERIC);
    }

    private static Path resolveStorePath(String baseName) {
        Path configDir = CrossPlatformRuntime.configDir();
        if (configDir == null) {
            return null;
        }
        Path dir = configDir.resolve("dlc_manager");
        Json5Util.migrateLegacy(dir.resolve(baseName + Json5Util.JSON_EXTENSION), GSON);
        Path path = Json5Util.resolveConfigPath(dir, baseName);
        return Json5Util.migrateLegacy(path, GSON);
    }
}
