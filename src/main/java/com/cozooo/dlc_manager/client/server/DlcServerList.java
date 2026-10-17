package com.cozooo.dlc_manager.client.server;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DlcServerList {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath;
    private static List<DlcServerEntry> entries = new ArrayList<>();
    private static String lastSelectedKey;

    public static void load() {
        configPath = DlcPaths.migrateLegacyConfigFile("servers.dc");
        entries = new ArrayList<>();
        lastSelectedKey = null;
        if (!Files.exists(configPath)) {
            return;
        }
        try {
            com.google.gson.JsonObject root = DlcDcConfigIO.read(configPath, DlcDcConfigIO.Layout.GENERIC);
            if (root != null) {
                DlcServerListData data = GSON.fromJson(root, DlcServerListData.class);
                if (data != null && data.servers != null) {
                    entries.addAll(data.servers);
                    lastSelectedKey = data.lastSelected;
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        if (configPath == null) {
            configPath = DlcPaths.migrateLegacyConfigFile("servers.dc");
        }
        try {
            Files.createDirectories(configPath.getParent());
            DlcServerListData data = new DlcServerListData();
            data.servers = entries;
            data.lastSelected = lastSelectedKey;
            com.google.gson.JsonObject root = GSON.toJsonTree(data).getAsJsonObject();
            DlcDcConfigIO.write(configPath, root, DlcDcConfigIO.Layout.GENERIC);
        } catch (IOException ignored) {
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
