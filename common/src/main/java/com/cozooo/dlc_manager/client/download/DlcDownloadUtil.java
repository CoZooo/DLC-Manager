package com.cozooo.dlc_manager.client.download;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

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

    public static String computeHashHex(Path file, String algorithm) throws IOException {
        byte[] hash = computeHashBytes(file, algorithm);
        if (hash == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String computeMd5Base64(Path file) throws IOException {
        byte[] hash = computeHashBytes(file, "MD5");
        if (hash == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(hash);
    }

    private static byte[] computeHashBytes(Path file, String algorithm) throws IOException {
        if (file == null || algorithm == null || algorithm.isBlank()) {
            return null;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    public static String normalizeHash(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replace("\"", "");
    }
}
