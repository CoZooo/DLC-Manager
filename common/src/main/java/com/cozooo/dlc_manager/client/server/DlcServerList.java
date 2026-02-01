package com.cozooo.dlc_manager.client.server;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
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
        configPath = PlatformServices.get().configDir().resolve("dlc_manager/servers.json");
        entries = new ArrayList<>();
        lastSelectedKey = null;
        if (!Files.exists(configPath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(configPath)) {
            DlcServerListData data = GSON.fromJson(reader, DlcServerListData.class);
            if (data != null && data.servers != null) {
                entries.addAll(data.servers);
                lastSelectedKey = data.lastSelected;
            }
        } catch (IOException ignored) {
            // Best-effort load.
        }
    }

    public static void save() {
        if (configPath == null) {
            configPath = PlatformServices.get().configDir().resolve("dlc_manager/servers.json");
        }
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                DlcServerListData data = new DlcServerListData();
                data.servers = entries;
                data.lastSelected = lastSelectedKey;
                GSON.toJson(data, writer);
            }
        } catch (IOException ignored) {
            // Best-effort save.
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
