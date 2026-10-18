package com.cozooo.dlc_manager.core;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class DlcPaths {
    public static Path gameDir() {
        return DlcPlatformServices.gameDir();
    }

    public static Path dlcRoot() {
        return DlcConfig.getDlcRoot();
    }

    public static Path tmpRoot() {
        return dlcRoot().resolve("tmp");
    }

    public static Path migrateLegacyConfigFile(String fileName) {
        Path target = tmpRoot().resolve(fileName);
        Path legacy = DlcPlatformServices.configDir().resolve("dlc_manager").resolve(fileName);
        if (Files.exists(target) || !Files.exists(legacy)) {
            return target;
        }
        try {
            Files.createDirectories(target.getParent());
            try {
                Files.move(legacy, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(legacy, target);
            }
            return target;
        } catch (IOException ignored) {
            return legacy;
        }
    }

    public static Path modsDir() {
        return gameDir().resolve("mods");
    }

    public static Path resourcePacksDir() {
        return DlcConfig.getResourcepackRoot();
    }

    public static Path taczDir() {
        return DlcConfig.getTaczRoot();
    }

    public static Path datapackStagingDir() {
        return DlcConfig.getDatapackRoot();
    }

    public static Path resolveAppliedTarget(String raw) {
        Path path = parsePathLenient(raw);
        if (path == null) {
            return null;
        }
        if (!path.isAbsolute()) {
            path = gameDir().resolve(path);
        }
        return path.normalize();
    }

    public static Path resolveAgainstGameDir(String raw) {
        Path path = parsePathLenient(raw);
        if (path == null) {
            return null;
        }
        if (!path.isAbsolute()) {
            path = gameDir().resolve(path);
        }
        return path.normalize();
    }

    public static Path parsePathLenient(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalizedSeparators = raw.replace('\\', '/');
        try {
            return Path.of(normalizedSeparators);
        } catch (InvalidPathException ignored) {
        }
        String repaired = repairEscapedControlChars(normalizedSeparators);
        if (repaired.equals(normalizedSeparators) || repaired.isBlank()) {
            return null;
        }
        try {
            return Path.of(repaired);
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private static String repairEscapedControlChars(String raw) {
        StringBuilder builder = new StringBuilder(raw.length() + 8);
        boolean changed = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            switch (ch) {
                case '\t' -> {
                    builder.append('\\').append('t');
                    changed = true;
                }
                case '\n' -> {
                    builder.append('\\').append('n');
                    changed = true;
                }
                case '\r' -> {
                    builder.append('\\').append('r');
                    changed = true;
                }
                case '\b' -> {
                    builder.append('\\').append('b');
                    changed = true;
                }
                case '\f' -> {
                    builder.append('\\').append('f');
                    changed = true;
                }
                default -> builder.append(ch);
            }
        }
        return changed ? builder.toString() : raw;
    }

    public static boolean isAppliedTargetDirectory(String raw, Path resolved, Path source) {
        if (raw != null && (raw.endsWith("/") || raw.endsWith("\\"))) {
            return true;
        }
        if (source != null && Files.isDirectory(source)) {
            return true;
        }
        if (resolved == null) {
            return false;
        }
        if (!Files.exists(resolved)) {
            return true;
        }
        return Files.isDirectory(resolved);
    }
}
