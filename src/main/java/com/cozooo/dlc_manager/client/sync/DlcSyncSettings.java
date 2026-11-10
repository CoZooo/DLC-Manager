package com.cozooo.dlc_manager.client.sync;

import com.cozooo.dlc_manager.core.*;
import com.cozooo.dlc_manager.sync.DlcSyncFailure;
import com.cozooo.dlc_manager.sync.DlcSyncModel.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class DlcSyncSettings {
    private DlcSyncSettings() {}
    static Item item(String key, Path path) throws IOException {
        JsonObject values = DlcConfig.buildSyncConfigJson(read(path));
        byte[] bytes = values.toString().getBytes(StandardCharsets.UTF_8);
        return new Item(key, "config", new File(key, bytes.length, DlcFileMatch.hash(new java.io.ByteArrayInputStream(bytes))), List.of(), null,
            Map.of(), Map.of(), null, values);
    }
    private static JsonObject read(Path path) throws IOException {
        if (!Files.exists(path)) return new JsonObject();
        return com.cozooo.dclanguage.DcDocuments.read(path).toJson();
    }
    static void write(Path path, JsonObject incoming, Journal journal) throws IOException {
        JsonObject values = DlcConfig.buildSyncConfigJson(incoming), original = read(path), previous = DlcConfig.buildSyncConfigJson(original);
        for (var section : values.entrySet()) {
            String category = existingKey(original, section.getKey());
            JsonObject target = DlcDcConfigIO.getObjectIgnoreCase(original, category);
            for (var entry : section.getValue().getAsJsonObject().entrySet()) {
                if (DlcDcConfigIO.getIgnoreCase(DlcDcConfigIO.getObjectIgnoreCase(incoming, section.getKey()), entry.getKey()) == null || entry.getValue().equals(previous.getAsJsonObject(section.getKey()).get(entry.getKey()))) continue;
                if (target == null) { target = new JsonObject(); original.add(category, target); }
                String key = existingKey(target, entry.getKey());
                if (section.getKey().equals("modpack") && entry.getKey().equals("updateGitRepo") && DlcDcConfigIO.getIgnoreCase(target, "updateGithubRepo") != null) target.add(existingKey(target, "updateGithubRepo"), entry.getValue().deepCopy());
                target.add(key, entry.getValue().deepCopy());
            }
        }
        journal.capture(path);
        Files.createDirectories(path.getParent());
        if (!DlcDcConfigIO.write(path, original, DlcDcConfigIO.Layout.GENERIC)) throw new DlcSyncFailure("settings_save");
    }
    private static String existingKey(JsonObject object, String key) {
        for (String existing : object.keySet()) if (existing.equalsIgnoreCase(key)) return existing;
        return key;
    }
}
