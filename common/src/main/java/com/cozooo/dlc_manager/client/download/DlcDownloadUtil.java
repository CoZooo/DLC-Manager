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
                String name = decodeUrlPathSegment(path.substring(idx + 1));
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

    public static String decodeUrlPathSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return segment;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(segment.length());
        int i = 0;
        while (i < segment.length()) {
            char c = segment.charAt(i);
            if (c == '%' && i + 2 < segment.length()) {
                int hi = hexValue(segment.charAt(i + 1));
                int lo = hexValue(segment.charAt(i + 2));
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) + lo);
                    i += 3;
                    continue;
                }
            }
            byte[] bytes = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            out.write(bytes, 0, bytes.length);
            i++;
        }
        return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return 10 + (c - 'a');
        }
        if (c >= 'A' && c <= 'F') {
            return 10 + (c - 'A');
        }
        return -1;
    }
}
