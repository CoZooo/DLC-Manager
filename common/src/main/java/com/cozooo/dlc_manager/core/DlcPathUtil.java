package com.cozooo.dlc_manager.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class DlcPathUtil {
    private DlcPathUtil() {
    }

    public static boolean hasWildcard(String pattern) {
        String normalized = normalizePattern(pattern);
        if (normalized == null) {
            return false;
        }
        for (String part : normalized.split("/")) {
            if (part.equals("*")) {
                return true;
            }
        }
        return false;
    }

    public static String normalizePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        String value = trimSlashes(pattern.replace('\\', '/').trim());
        if (value.isBlank()
            || value.startsWith("../")
            || value.equals("..")
            || value.contains("/../")
            || value.contains("//")) {
            return null;
        }
        return value;
    }

    public static boolean matches(String pattern, String relativePath) {
        String normalizedPattern = normalizePattern(pattern);
        String normalizedPath = normalizePattern(relativePath);
        if (normalizedPattern == null || normalizedPath == null) {
            return false;
        }
        String[] patternParts = normalizedPattern.split("/");
        String[] pathParts = normalizedPath.split("/");
        if (patternParts.length != pathParts.length) {
            return false;
        }
        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            if (!patternPart.equals("*") && !patternPart.equals(pathParts[i])) {
                return false;
            }
        }
        return true;
    }

    public static List<Path> findMatches(Path root, String pattern) {
        if (root == null) {
            return List.of();
        }
        String normalizedPattern = normalizePattern(pattern);
        if (normalizedPattern == null) {
            return List.of();
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.exists(normalizedRoot)) {
            return List.of();
        }
        if (!hasWildcard(normalizedPattern)) {
            Path exact = normalizedRoot.resolve(normalizedPattern).normalize();
            if (exact.startsWith(normalizedRoot) && Files.exists(exact)) {
                return List.of(exact);
            }
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(normalizedRoot, normalizedPattern.split("/").length)) {
            stream.filter(path -> !path.equals(normalizedRoot)).forEach(path -> {
                String relative = normalizeRelative(normalizedRoot.relativize(path));
                if (relative != null && matches(normalizedPattern, relative)) {
                    result.add(path);
                }
            });
        } catch (IOException ignored) {
            return List.of();
        }
        result.sort(Comparator.comparing(path -> normalizeRelative(normalizedRoot.relativize(path))));
        return List.copyOf(result);
    }

    private static String normalizeRelative(Path path) {
        return path == null ? null : normalizePattern(path.toString());
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
