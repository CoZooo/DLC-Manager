package com.cozooo.dlc_manager.core;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import com.cozooo.dlc_manager.platform.PlatformServices;

public class DlcPaths {
    public static Path gameDir() {
        return CrossPlatformRuntime.gameDir();
    }

    public static Path dlcRoot() {
        return DlcPathConfig.getDlcRoot();
    }

    public static Path tmpRoot() {
        return dlcRoot().resolve("tmp");
    }

    public static Path modsDir() {
        return gameDir().resolve("mods");
    }

    public static Path resourcePacksDir() {
        return DlcPathConfig.getResourcepackRoot();
    }

    public static Path taczDir() {
        return gameDir().resolve("tacz");
    }

    public static Path datapackStagingDir() {
        return DlcPathConfig.getDatapackRoot();
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
        try {
            return Path.of(raw);
        } catch (InvalidPathException ignored) {
        }
        String repaired = repairEscapedControlChars(raw);
        if (repaired.equals(raw) || repaired.isBlank()) {
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
