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
        return normalizeRelativePath(pattern);
    }

    public static String normalizeRelativePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String raw = path.replace('\\', '/').trim();
        String value = trimSlashes(raw);
        if (value.isBlank()
            || value.startsWith("../")
            || value.equals("..")
            || value.contains("/../")
            || value.endsWith("/..")
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

    public static Path resolveInside(Path root, String relative) throws IOException {
        Path normalizedRoot = root == null ? null : root.toAbsolutePath().normalize();
        if (normalizedRoot == null) {
            throw new IOException("Root directory is not available");
        }
        String normalizedRelative = normalizeRelativePath(relative);
        if (normalizedRelative == null) {
            throw new IOException("Invalid relative path: " + relative);
        }
        Path target = normalizedRoot.resolve(normalizedRelative).normalize();
        if (!target.startsWith(normalizedRoot)) {
            throw new IOException("Path escapes root: " + relative);
        }
        return target;
    }

    public static String toGameRelativePath(Path path) {
        return toRelativePath(DlcPaths.gameDir(), path);
    }

    public static String toRelativePath(Path root, Path path) {
        if (path == null) {
            return "";
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (root == null) {
            return normalized.toString().replace('\\', '/');
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        try {
            Path rootReal = normalizedRoot.toRealPath();
            Path pathReal = normalized.toRealPath();
            if (pathReal.startsWith(rootReal)) {
                return rootReal.relativize(pathReal).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
        }
        try {
            if (normalized.startsWith(normalizedRoot)) {
                return normalizedRoot.relativize(normalized).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
        }
        return normalized.toString().replace('\\', '/');
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
