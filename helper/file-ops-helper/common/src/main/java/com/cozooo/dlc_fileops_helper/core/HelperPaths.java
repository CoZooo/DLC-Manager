package com.cozooo.dlc_fileops_helper.core;

import java.nio.file.Path;

public final class HelperPaths {
    private static volatile Path gameDirOverride;
    private static volatile Path tmpRootOverride;

    private HelperPaths() {
    }

    public static void configure(Path gameDir, Path tmpRoot) {
        gameDirOverride = normalize(gameDir);
        tmpRootOverride = normalize(tmpRoot);
    }

    public static Path gameDir() {
        if (gameDirOverride != null) {
            return gameDirOverride;
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    public static Path tmpRoot() {
        if (tmpRootOverride != null) {
            return tmpRootOverride;
        }
        throw new IllegalStateException("FileOps helper tmpRoot is not configured");
    }

    public static Path pendingOpsRoot() {
        return tmpRoot().resolve("pending_file_ops").normalize();
    }

    private static Path normalize(Path path) {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().normalize();
    }
}
