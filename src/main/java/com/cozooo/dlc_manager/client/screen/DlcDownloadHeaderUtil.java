package com.cozooo.dlc_manager.client.screen;

import java.net.HttpURLConnection;

final class DlcDownloadHeaderUtil {
    private DlcDownloadHeaderUtil() {
    }

    static String extractFileNameFromHeaders(HttpURLConnection connection) {
        if (connection == null) {
            return null;
        }
        return parseContentDisposition(connection.getHeaderField("Content-Disposition"));
    }

    static String parseContentDisposition(String disposition) {
        if (disposition == null || disposition.isBlank()) {
            return null;
        }
        String lower = disposition.toLowerCase();
        int idx = lower.indexOf("filename=");
        boolean isStar = false;
        if (idx < 0) {
            idx = lower.indexOf("filename*=");
            isStar = idx >= 0;
        }
        if (idx < 0) {
            return null;
        }
        String value = disposition.substring(idx);
        int equals = value.indexOf('=');
        if (equals >= 0) {
            value = value.substring(equals + 1).trim();
        }
        int semi = value.indexOf(';');
        if (semi >= 0) {
            value = value.substring(0, semi).trim();
        }
        value = value.replace("\"", "");
        if (isStar) {
            value = decodeFilenameStar(value);
        }
        return stripQuery(value);
    }

    private static String decodeFilenameStar(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        String data = trimmed;
        java.nio.charset.Charset charset = java.nio.charset.StandardCharsets.UTF_8;
        int first = trimmed.indexOf('\'');
        int second = first >= 0 ? trimmed.indexOf('\'', first + 1) : -1;
        if (first > 0 && second > first) {
            String charsetName = trimmed.substring(0, first).trim();
            data = trimmed.substring(second + 1);
            if (!charsetName.isBlank()) {
                try {
                    charset = java.nio.charset.Charset.forName(charsetName);
                } catch (Exception ignored) {
                    // Best-effort.
                }
            }
        }
        try {
            return java.net.URLDecoder.decode(data, charset);
        } catch (IllegalArgumentException ignored) {
            return data;
        }
    }

    private static String stripQuery(String name) {
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
