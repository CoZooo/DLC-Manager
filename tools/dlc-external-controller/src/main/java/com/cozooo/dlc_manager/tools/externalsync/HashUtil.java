package com.cozooo.dlc_manager.tools.externalsync;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

final class HashUtil {
    private HashUtil() {
    }

    static String digest(Path path, String algorithm) throws ExternalSyncException {
        try {
            MessageDigest digest = MessageDigest.getInstance(normalizeAlgorithm(algorithm));
            byte[] buffer = new byte[64 * 1024];
            try (InputStream input = Files.newInputStream(path)) {
                int bytesRead;
                while ((bytesRead = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest()).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new ExternalSyncException("failed to hash " + path, ex);
        }
    }

    static boolean matches(Path path, SyncModels.DownloadCandidate candidate) throws ExternalSyncException {
        if (candidate == null) {
            return true;
        }
        if (candidate.size > 0) {
            try {
                if (Files.size(path) != candidate.size) {
                    return false;
                }
            } catch (IOException ex) {
                throw new ExternalSyncException("failed to stat downloaded file", ex);
            }
        }
        if (candidate.sha512 != null && !candidate.sha512.isBlank()) {
            return candidate.sha512.equalsIgnoreCase(digest(path, "sha-512"));
        }
        if (candidate.sha256 != null && !candidate.sha256.isBlank()) {
            return candidate.sha256.equalsIgnoreCase(digest(path, "sha-256"));
        }
        if (candidate.sha1 != null && !candidate.sha1.isBlank()) {
            return candidate.sha1.equalsIgnoreCase(digest(path, "sha-1"));
        }
        if (candidate.md5 != null && !candidate.md5.isBlank()) {
            return candidate.md5.equalsIgnoreCase(digest(path, "md5"));
        }
        return true;
    }

    private static String normalizeAlgorithm(String algorithm) {
        String value = algorithm.trim().toLowerCase(Locale.ROOT).replace("_", "-");
        return switch (value) {
            case "sha1", "sha-1" -> "SHA-1";
            case "sha256", "sha-256" -> "SHA-256";
            case "sha512", "sha-512" -> "SHA-512";
            case "md5" -> "MD5";
            default -> algorithm;
        };
    }
}
