package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.core.DcConfigIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class DlcPackOrder {
    private DlcPackOrder() {
    }

    public static List<String> load(Path path) {
        if (path == null || !Files.exists(path)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(path)) {
                String value = normalize(line);
                if (value != null && !out.contains(value)) {
                    out.add(value);
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    public static boolean save(Path path, Collection<String> order) {
        if (path == null) {
            return false;
        }
        List<String> lines = new ArrayList<>();
        if (order != null) {
            for (String key : order) {
                String value = normalize(key);
                if (value != null) {
                    lines.add(value);
                }
            }
        }
        return writeAtomicLines(path, lines);
    }

    public static List<String> normalizeOrder(Collection<String> savedOrder, Collection<String> currentKeys) {
        List<String> out = new ArrayList<>();
        if (savedOrder != null) {
            for (String key : savedOrder) {
                String value = normalize(key);
                if (value != null && currentKeys != null && currentKeys.contains(value) && !out.contains(value)) {
                    out.add(value);
                }
            }
        }
        if (currentKeys != null) {
            for (String key : currentKeys) {
                String value = normalize(key);
                if (value != null && !out.contains(value)) {
                    out.add(value);
                }
            }
        }
        return out;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank() || normalized.startsWith("../") || normalized.equals("..") || normalized.contains("/../")) {
            return null;
        }
        return normalized;
    }

    private static boolean writeAtomicLines(Path path, List<String> lines) {
        try {
            Path target = DcConfigIO.toDcPath(path);
            if (target == null) {
                return false;
            }
            Path parent = target.getParent();
            Path temp;
            if (parent != null) {
                Files.createDirectories(parent);
                temp = Files.createTempFile(parent, target.getFileName().toString() + ".", ".tmp");
            } else {
                temp = Files.createTempFile(target.getFileName().toString() + ".", ".tmp");
            }
            try {
                Files.write(temp, lines);
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException ex) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                if (!target.equals(path)) {
                    Files.deleteIfExists(path);
                }
                return true;
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ignored) {
            return false;
        }
    }
}
