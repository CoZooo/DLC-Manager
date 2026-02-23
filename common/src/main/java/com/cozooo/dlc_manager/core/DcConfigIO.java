package com.cozooo.dlc_manager.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class DcConfigIO {
    public enum Layout {
        DLC_STRUCTURED,
        GENERIC
    }

    private DcConfigIO() {
    }

    public static JsonObject read(Path path, Layout layout) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        if (isDc(path)) {
            if (layout == Layout.DLC_STRUCTURED && !isNoticeConfigPath(path)) {
                return DcFormatUtil.read(path);
            }
            return DcFormatUtil.readGeneric(path);
        }
        JsonElement element = Json5Util.readElement(path);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return null;
    }

    public static boolean write(Path originalPath, JsonObject obj, Layout layout) {
        if (originalPath == null || obj == null) {
            return false;
        }
        Path target = Json5Util.toDcPath(originalPath);
        if (target == null) {
            return false;
        }
        JsonObject source = obj;
        if (layout == Layout.DLC_STRUCTURED && isInfoPath(target)) {
            source = stripDownloadKeys(obj);
        }
        boolean written = layout == Layout.DLC_STRUCTURED && !isNoticeConfigPath(target)
            ? DcFormatUtil.write(target, source)
            : DcFormatUtil.writeGeneric(target, source);
        if (!written) {
            return false;
        }
        if (!target.equals(originalPath)) {
            try {
                Files.deleteIfExists(originalPath);
            } catch (IOException ignored) {
            }
        }
        return true;
    }

    public static Path toDcPath(Path path) {
        return Json5Util.toDcPath(path);
    }

    public static boolean isDc(Path path) {
        return path != null
            && path.getFileName() != null
            && DcFormatUtil.isDcName(path.getFileName().toString());
    }

    private static boolean isInfoPath(Path path) {
        if (path == null) {
            return false;
        }
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/info/");
    }

    private static boolean isNoticeConfigPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String base = Json5Util.stripJsonExtension(path.getFileName().toString());
        return "notice".equalsIgnoreCase(base);
    }

    private static JsonObject stripDownloadKeys(JsonObject input) {
        if (input == null) {
            return null;
        }
        JsonObject out = new JsonObject();
        for (var entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            if ("modrinth_projectid".equalsIgnoreCase(key)
                || "modrinth_versionid".equalsIgnoreCase(key)
                || "curseforge_projectid".equalsIgnoreCase(key)
                || "curseforge_fileid".equalsIgnoreCase(key)
                || "directUrl".equalsIgnoreCase(key)
                || "priority".equalsIgnoreCase(key)) {
                continue;
            }
            out.add(key, entry.getValue());
        }
        return out;
    }
}
