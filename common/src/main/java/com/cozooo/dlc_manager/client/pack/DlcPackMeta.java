package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DlcPackMeta {
    public String identifier;
    public String version;
    public String format;
    public String timestamp;
    public final List<String> installedPaths = new ArrayList<>();

    public DlcPackMeta(String identifier, String version, String format, String timestamp, Collection<String> installedPaths) {
        this.identifier = identifier;
        this.version = version;
        this.format = format;
        this.timestamp = timestamp;
        addInstalledPaths(installedPaths);
    }

    public void addInstalledPath(String path) {
        String normalized = normalizeRelativePath(path);
        if (normalized != null && !installedPaths.contains(normalized)) {
            installedPaths.add(normalized);
        }
    }

    public void addInstalledPaths(Collection<String> paths) {
        if (paths == null) {
            return;
        }
        for (String path : paths) {
            addInstalledPath(path);
        }
    }

    public boolean save(Path path) {
        if (path == null) {
            return false;
        }
        JsonObject obj = new JsonObject();
        JsonObject basic = new JsonObject();
        basic.addProperty("identifier", identifier);
        basic.addProperty("version", version);
        basic.addProperty("format", format);
        basic.addProperty("timestamp", timestamp);
        obj.add("basic", basic);
        JsonArray files = new JsonArray();
        for (String installed : installedPaths) {
            files.add(installed);
        }
        obj.add("installed", files);
        return DlcPackDcFiles.writeAtomic(path, obj);
    }

    public static DlcPackMeta load(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        JsonObject obj = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
        if (obj == null) {
            return null;
        }
        JsonObject basic = DlcDcConfigIO.getObjectIgnoreCase(obj, "basic");
        String identifier = trim(DlcDcConfigIO.getStringIgnoreCase(basic, "identifier"));
        String version = trim(DlcDcConfigIO.getStringIgnoreCase(basic, "version"));
        if (!DlcPackMetadata.isValidIdentifier(identifier) || !DlcPackMetadata.isValidVersion(version)) {
            return null;
        }
        String format = trim(DlcDcConfigIO.getStringIgnoreCase(basic, "format"));
        String timestamp = trim(DlcDcConfigIO.getStringIgnoreCase(basic, "timestamp"));
        List<String> installed = new ArrayList<>();
        JsonArray array = DlcDcConfigIO.getArrayIgnoreCase(obj, "installed");
        if (array != null) {
            array.forEach(element -> {
                if (element != null && element.isJsonPrimitive()) {
                    String normalized = normalizeRelativePath(element.getAsString());
                    if (normalized != null) {
                        installed.add(normalized);
                    }
                }
            });
        }
        return new DlcPackMeta(identifier, version, format == null ? "" : format, timestamp == null ? "" : timestamp, installed);
    }

    public static String normalizeRelativePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String value = trimSlashes(path.replace('\\', '/').trim());
        if (value.isBlank() || value.startsWith("../") || value.equals("..") || value.contains("/../")) {
            return null;
        }
        return value;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimSlashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(start, end);
    }
}
