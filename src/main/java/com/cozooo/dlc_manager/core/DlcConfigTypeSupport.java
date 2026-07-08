package com.cozooo.dlc_manager.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

public final class DlcConfigTypeSupport {
    private DlcConfigTypeSupport() {
    }

    public static boolean isConfigType(DlcItem item) {
        return item != null && item.getType() == DlcType.UNKNOWN && item.isConfigType();
    }

    public static Path backupRoot() {
        List<Path> roots = DlcConfig.getDlcRoots();
        Path base = roots.isEmpty() ? DlcPaths.gameDir() : roots.get(0);
        return DlcTmpPaths.resolve(base, "config_backup");
    }

    public static Path backupRootForItem(String itemId) {
        String key = itemId == null || itemId.isBlank() ? "unknown" : sanitizeFilePart(itemId);
        return backupRoot().resolve(key);
    }

    public static Path backupPathForItemTarget(String itemId, Path target) {
        Path normalized = target.toAbsolutePath().normalize();
        String digest = sha1(normalized.toString());
        String leaf = normalized.getFileName() == null ? "root" : sanitizeFilePart(normalized.getFileName().toString());
        return backupRootForItem(itemId).resolve(digest + "_" + leaf + ".bak");
    }

    public static Path backupPathForTarget(Path target) {
        Path normalized = target.toAbsolutePath().normalize();
        String digest = sha1(normalized.toString());
        String leaf = normalized.getFileName() == null ? "root" : sanitizeFilePart(normalized.getFileName().toString());
        return backupRoot().resolve(digest + "_" + leaf + ".bak");
    }

    public static Path missingMarkerPath(Path backupPath) {
        return backupPath.resolveSibling(backupPath.getFileName().toString() + ".missing");
    }

    public static boolean hasBackupForItemTarget(String itemId, Path target) {
        Path backup = backupPathForItemTarget(itemId, target);
        return java.nio.file.Files.exists(backup) || java.nio.file.Files.exists(missingMarkerPath(backup));
    }

    public static boolean hasBackupForTarget(Path target) {
        Path backup = backupPathForTarget(target);
        return java.nio.file.Files.exists(backup) || java.nio.file.Files.exists(missingMarkerPath(backup));
    }

    private static String sha1(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String sanitizeFilePart(String raw) {
        String cleaned = raw.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "target" : cleaned;
    }
}
