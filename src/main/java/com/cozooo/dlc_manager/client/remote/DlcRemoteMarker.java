package com.cozooo.dlc_manager.client.remote;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DlcRemoteMarker {
    public static final String SKIP_MARKER = "SKIP";
    public static final String PART_DLC = "dlc";
    public static final String PART_DLC_PACK = "dlcpack";

    private DlcRemoteMarker() {
    }

    public static boolean hasPartMarker(Path dlcRoot) {
        return hasPartMarker(dlcRoot, PART_DLC);
    }

    public static boolean hasPartMarker(Path dlcRoot, String key) {
        if (dlcRoot == null) {
            return false;
        }
        Path marker = resolveRemoteRoot(dlcRoot).resolve(SKIP_MARKER);
        if (!Files.exists(marker)) {
            return false;
        }
        String normalized = normalizeKey(key);
        return normalized != null && readPartKeys(marker).contains(normalized);
    }

    public static void writePartMarker(Path dlcRoot) {
        writePartMarker(dlcRoot, PART_DLC);
    }

    public static void writePartMarker(Path dlcRoot, String key) {
        if (dlcRoot == null) {
            return;
        }
        String normalized = normalizeKey(key);
        if (normalized == null) {
            return;
        }
        Path remoteRoot = resolveRemoteRoot(dlcRoot);
        try {
            Files.createDirectories(remoteRoot);
            Path marker = remoteRoot.resolve(SKIP_MARKER);
            Set<String> keys = readPartKeys(marker);
            keys.add(normalized);
            writePartKeys(marker, keys);
        } catch (IOException ignored) {
        }
    }

    public static void clearPartMarker(Path dlcRoot) {
        if (dlcRoot == null) {
            return;
        }
        Path marker = resolveRemoteRoot(dlcRoot).resolve(SKIP_MARKER);
        try {
            Files.deleteIfExists(marker);
        } catch (IOException ignored) {
        }
    }

    public static void clearPartMarker(Path dlcRoot, String key) {
        if (dlcRoot == null) {
            return;
        }
        String normalized = normalizeKey(key);
        if (normalized == null) {
            return;
        }
        Path marker = resolveRemoteRoot(dlcRoot).resolve(SKIP_MARKER);
        try {
            Set<String> keys = readPartKeys(marker);
            keys.remove(normalized);
            if (keys.isEmpty()) {
                Files.deleteIfExists(marker);
            } else {
                writePartKeys(marker, keys);
            }
        } catch (IOException ignored) {
        }
    }

    public static Path resolveRemoteRoot(Path dlcRoot) {
        return dlcRoot.resolve("remote");
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    private static Set<String> readPartKeys(Path marker) {
        Set<String> keys = new LinkedHashSet<>();
        if (!Files.exists(marker)) {
            return keys;
        }
        try {
            for (String line : Files.readAllLines(marker)) {
                String normalized = normalizeKey(line);
                if (normalized != null) {
                    keys.add(normalized);
                }
            }
            return keys;
        } catch (IOException ignored) {
            return keys;
        }
    }

    private static void writePartKeys(Path marker, Set<String> keys) throws IOException {
        Files.createDirectories(marker.getParent());
        List<String> sorted = new ArrayList<>(keys);
        sorted.sort(String::compareTo);
        Files.write(marker, sorted);
    }
}
