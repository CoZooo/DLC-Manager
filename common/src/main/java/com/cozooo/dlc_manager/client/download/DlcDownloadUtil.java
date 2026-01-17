package com.cozooo.dlc_manager.client.download;

import java.net.URL;

public final class DlcDownloadUtil {
    private DlcDownloadUtil() {
    }

    public static String extractFileNameFromUrl(String url) {
        try {
            String path = new URL(url).getPath();
            int idx = path.lastIndexOf('/');
            if (idx >= 0 && idx + 1 < path.length()) {
                String name = path.substring(idx + 1);
                return sanitizeFileName(name);
            }
        } catch (Exception ignored) {
            // Best-effort.
        }
        return null;
    }

    public static String sanitizeFileName(String name) {
        String cleaned = stripQuery(name);
        return isLikelyFileName(cleaned) ? cleaned : null;
    }

    public static boolean isLikelyFileName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        int dot = trimmed.lastIndexOf('.');
        if (dot <= 0 || dot >= trimmed.length() - 1) {
            return false;
        }
        String ext = trimmed.substring(dot + 1);
        if (ext.length() > 10) {
            return false;
        }
        return !ext.contains("/") && !ext.contains("\\");
    }

    public static String stripQuery(String name) {
        if (name == null) {
            return null;
        }
        int q = name.indexOf('?');
        if (q >= 0) {
            name = name.substring(0, q);
        }
        int hash = name.indexOf('#');
        if (hash >= 0) {
            name = name.substring(0, hash);
        }
        return name;
    }
}
