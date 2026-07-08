package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DlcPackTransaction {
    public Status status;
    public String identifier;
    public String stagingId;
    public final List<String> movedPaths = new ArrayList<>();
    public final List<String> writtenPaths = new ArrayList<>();
    public final List<String> enabledDlc = new ArrayList<>();
    public final List<String> disabledDlc = new ArrayList<>();
    public final List<String> enabledPresets = new ArrayList<>();
    public final List<String> disabledPresets = new ArrayList<>();
    public boolean replacement;

    public DlcPackTransaction(Status status, String identifier, String stagingId, Collection<String> movedPaths, Collection<String> writtenPaths) {
        this(status, identifier, stagingId, movedPaths, writtenPaths, List.of(), List.of(), List.of(), List.of());
    }

    public DlcPackTransaction(
        Status status,
        String identifier,
        String stagingId,
        Collection<String> movedPaths,
        Collection<String> writtenPaths,
        Collection<String> enabledDlc,
        Collection<String> disabledDlc,
        Collection<String> enabledPresets,
        Collection<String> disabledPresets
    ) {
        this(status, identifier, stagingId, movedPaths, writtenPaths, enabledDlc, disabledDlc, enabledPresets, disabledPresets, false);
    }

    public DlcPackTransaction(
        Status status,
        String identifier,
        String stagingId,
        Collection<String> movedPaths,
        Collection<String> writtenPaths,
        Collection<String> enabledDlc,
        Collection<String> disabledDlc,
        Collection<String> enabledPresets,
        Collection<String> disabledPresets,
        boolean replacement
    ) {
        this.status = status;
        this.identifier = identifier;
        this.stagingId = stagingId;
        this.replacement = replacement;
        addAll(this.movedPaths, movedPaths);
        addAll(this.writtenPaths, writtenPaths);
        addAll(this.enabledDlc, enabledDlc);
        addAll(this.disabledDlc, disabledDlc);
        addAll(this.enabledPresets, enabledPresets);
        addAll(this.disabledPresets, disabledPresets);
    }

    public void addMovedPath(String path) {
        addPath(movedPaths, path);
    }

    public void addWrittenPath(String path) {
        addPath(writtenPaths, path);
    }

    public void addEnabledDlc(String id) {
        addToken(enabledDlc, id);
    }

    public void addDisabledDlc(String id) {
        addToken(disabledDlc, id);
    }

    public void addEnabledPreset(String id) {
        addToken(enabledPresets, id);
    }

    public void addDisabledPreset(String id) {
        addToken(disabledPresets, id);
    }

    public boolean save(Path path) {
        if (path == null) {
            return false;
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("status", status.name().toLowerCase(java.util.Locale.ROOT));
        obj.addProperty("identifier", identifier);
        obj.addProperty("staging", stagingId);
        obj.add("moved", toArray(movedPaths));
        obj.add("written", toArray(writtenPaths));
        obj.add("enabled_dlc", toArray(enabledDlc));
        obj.add("disabled_dlc", toArray(disabledDlc));
        obj.add("enabled_presets", toArray(enabledPresets));
        obj.add("disabled_presets", toArray(disabledPresets));
        obj.addProperty("replacement", replacement);
        return DlcPackDcFiles.writeAtomic(path, obj);
    }

    public static DlcPackTransaction running(String identifier, String stagingId) {
        return new DlcPackTransaction(Status.RUNNING, identifier, stagingId, List.of(), List.of());
    }

    public static DlcPackTransaction load(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        JsonObject obj = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
        if (obj == null) {
            return null;
        }
        Status status = parseStatus(DlcDcConfigIO.getStringIgnoreCase(obj, "status"));
        String identifier = trim(DlcDcConfigIO.getStringIgnoreCase(obj, "identifier"));
        String staging = trim(DlcDcConfigIO.getStringIgnoreCase(obj, "staging"));
        if (status == null || !DlcPackMetadata.isValidIdentifier(identifier)) {
            return null;
        }
        return new DlcPackTransaction(
            status,
            identifier,
            staging == null ? "" : staging,
            readArray(DlcDcConfigIO.getArrayIgnoreCase(obj, "moved")),
            readArray(DlcDcConfigIO.getArrayIgnoreCase(obj, "written")),
            readTokenArray(DlcDcConfigIO.getArrayIgnoreCase(obj, "enabled_dlc")),
            readTokenArray(DlcDcConfigIO.getArrayIgnoreCase(obj, "disabled_dlc")),
            readTokenArray(DlcDcConfigIO.getArrayIgnoreCase(obj, "enabled_presets")),
            readTokenArray(DlcDcConfigIO.getArrayIgnoreCase(obj, "disabled_presets")),
            getBoolean(obj, "replacement")
        );
    }

    public static Status parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (Status status : Status.values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }

    private static void addAll(List<String> target, Collection<String> paths) {
        if (paths == null) {
            return;
        }
        for (String path : paths) {
            addPath(target, path);
        }
    }

    private static void addPath(List<String> target, String path) {
        String normalized = DlcPackMeta.normalizeRelativePath(path);
        if (normalized != null && !target.contains(normalized)) {
            target.add(normalized);
        }
    }

    private static void addToken(List<String> target, String token) {
        String value = trim(token);
        if (value != null && !value.isBlank() && !target.contains(value)) {
            target.add(value);
        }
    }

    private static JsonArray toArray(List<String> paths) {
        JsonArray array = new JsonArray();
        for (String path : paths) {
            array.add(path);
        }
        return array;
    }

    private static List<String> readArray(JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        array.forEach(element -> {
            if (element != null && element.isJsonPrimitive()) {
                String normalized = DlcPackMeta.normalizeRelativePath(element.getAsString());
                if (normalized != null) {
                    out.add(normalized);
                }
            }
        });
        return out;
    }

    private static List<String> readTokenArray(JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        array.forEach(element -> {
            if (element != null && element.isJsonPrimitive()) {
                String value;
                try {
                    value = trim(element.getAsString());
                } catch (Exception ex) {
                    value = null;
                }
                if (value != null && !value.isBlank() && !out.contains(value)) {
                    out.add(value);
                }
            }
        });
        return out;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean getBoolean(JsonObject obj, String key) {
        var value = DlcDcConfigIO.getIgnoreCase(obj, key);
        if (value == null || !value.isJsonPrimitive()) {
            return false;
        }
        try {
            return value.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    public enum Status {
        RUNNING,
        CANCELLED,
        COMMITTED,
        ROLLED_BACK
    }
}
