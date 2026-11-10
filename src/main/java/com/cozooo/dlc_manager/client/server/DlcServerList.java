package com.cozooo.dlc_manager.client.server;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcUserPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DlcServerList {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORE_FILE = "servers.dc";
    private static final String PACKS_KEY = "packs";
    private static final String ID_KEY = "id";
    private static List<DlcServerEntry> entries = new ArrayList<>();
    private static String lastSelectedKey;
    private static String activeKey;

    public static Path storePath() {
        return DlcUserPaths.configFile(STORE_FILE);
    }

    public static void load() {
        entries = new ArrayList<>();
        lastSelectedKey = null;
        activeKey = identityKey();
        JsonObject data = findProfile(activeKey);
        if (data != null) {
            apply(data);
            return;
        }
        migrateLegacy();
    }

    public static void save() {
        activeKey = identityKey();
        writeCurrent();
    }

    private static JsonObject findProfile(String key) {
        JsonArray packs = DlcDcConfigIO.getArrayIgnoreCase(
            DlcDcConfigIO.read(storePath(), DlcDcConfigIO.Layout.GENERIC), PACKS_KEY);
        if (packs == null) {
            return null;
        }
        for (var element : packs) {
            if (element != null && element.isJsonObject()) {
                JsonObject profile = element.getAsJsonObject();
                if (key.equals(DlcDcConfigIO.getStringIgnoreCase(profile, ID_KEY))) {
                    return profile;
                }
            }
        }
        return null;
    }

    private static String identityKey() {
        String modpack = DlcConfig.getModpack();
        if (modpack != null && !modpack.isBlank()) {
            return "pack:" + modpack.trim();
        }
        Path root = DlcConfig.getDlcRoot();
        if (root == null) {
            return "path:";
        }
        return "path:" + root.toAbsolutePath().normalize();
    }

    private static void migrateLegacy() {
        Path legacyTmp = DlcPaths.tmpRoot().resolve("servers.dc");
        Path legacyConfig = DlcPaths.managerConfigDir().resolve("servers.dc");
        Path source = Files.exists(legacyTmp) ? legacyTmp : Files.exists(legacyConfig) ? legacyConfig : null;
        if (source == null) {
            return;
        }
        JsonObject legacy = DlcDcConfigIO.read(source, DlcDcConfigIO.Layout.GENERIC);
        if (legacy == null) {
            return;
        }
        apply(legacy);
        if (writeCurrent()) {
            try {
                Files.deleteIfExists(source);
            } catch (IOException ignored) {
            }
        }
    }

    private static void apply(JsonObject data) {
        DlcServerListData parsed = GSON.fromJson(data, DlcServerListData.class);
        if (parsed == null) {
            return;
        }
        entries = new ArrayList<>();
        if (parsed.servers != null) {
            entries.addAll(parsed.servers);
        }
        lastSelectedKey = parsed.lastSelected;
    }

    private static boolean writeCurrent() {
        if (activeKey == null || activeKey.isBlank()) {
            return false;
        }
        JsonObject root = DlcDcConfigIO.read(storePath(), DlcDcConfigIO.Layout.GENERIC);
        if (root == null) {
            root = new JsonObject();
        }
        JsonArray packs = DlcDcConfigIO.getArrayIgnoreCase(root, PACKS_KEY);
        if (packs == null) {
            packs = new JsonArray();
            root.add(PACKS_KEY, packs);
        }
        JsonObject data = new JsonObject();
        data.addProperty(ID_KEY, activeKey);
        DlcServerListData list = new DlcServerListData();
        list.servers = entries;
        list.lastSelected = lastSelectedKey;
        JsonObject serialized = GSON.toJsonTree(list).getAsJsonObject();
        for (var entry : serialized.entrySet()) {
            data.add(entry.getKey(), entry.getValue());
        }
        boolean replaced = false;
        for (int i = 0; i < packs.size(); i++) {
            var element = packs.get(i);
            if (element != null && element.isJsonObject()
                && activeKey.equals(DlcDcConfigIO.getStringIgnoreCase(element.getAsJsonObject(), ID_KEY))) {
                packs.set(i, data);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            packs.add(data);
        }
        return write(root);
    }

    private static boolean write(JsonObject root) {
        try {
            Path parent = storePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return DlcDcConfigIO.write(storePath(), root, DlcDcConfigIO.Layout.GENERIC);
        } catch (IOException ignored) {
            return false;
        }
    }

    public static List<DlcServerEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public static void setEntries(List<DlcServerEntry> newEntries) {
        entries = new ArrayList<>(newEntries == null ? List.of() : newEntries);
    }

    public static void addEntry(DlcServerEntry entry) {
        if (entry == null) {
            return;
        }
        entries.add(entry);
    }

    public static void removeEntry(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        entries.removeIf(entry -> entry != null && key.equals(entry.getKey()));
    }

    public static String getLastSelectedKey() {
        return lastSelectedKey;
    }

    public static void setLastSelectedKey(String key) {
        lastSelectedKey = key;
    }

    private static class DlcServerListData {
        List<DlcServerEntry> servers;
        String lastSelected;
    }
}
