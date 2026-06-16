package com.cozooo.dlc_manager.client.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DlcPackManifest {
    public final List<FileRef> files;

    private DlcPackManifest(List<FileRef> files) {
        this.files = files == null ? List.of() : List.copyOf(files);
    }

    public static DlcPackManifest empty() {
        return new DlcPackManifest(List.of());
    }

    public static DlcPackManifest read(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return empty();
        }
        try (var reader = Files.newBufferedReader(path)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                return empty();
            }
            return read(parsed.getAsJsonObject());
        } catch (IOException | RuntimeException ignored) {
            return empty();
        }
    }

    public static DlcPackManifest read(JsonObject obj) {
        if (obj == null || !obj.has("files") || !obj.get("files").isJsonArray()) {
            return empty();
        }
        List<FileRef> out = new ArrayList<>();
        for (JsonElement element : obj.getAsJsonArray("files")) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject file = element.getAsJsonObject();
            String projectId = readId(file, "projectID");
            String fileId = readId(file, "fileID");
            if (projectId != null && fileId != null) {
                out.add(new FileRef(projectId, fileId));
            }
        }
        return new DlcPackManifest(out);
    }

    private static String readId(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            String value = obj.get(key).getAsString();
            return value == null || value.isBlank() ? null : value.trim();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static final class FileRef {
        public final String projectId;
        public final String fileId;

        public FileRef(String projectId, String fileId) {
            this.projectId = projectId;
            this.fileId = fileId;
        }
    }
}
