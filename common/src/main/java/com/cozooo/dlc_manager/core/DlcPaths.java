package com.cozooo.dlc_manager.core;

import java.nio.file.Files;
import java.nio.file.Path;
import com.cozooo.dlc_manager.platform.PlatformServices;

public class DlcPaths {
    public static Path gameDir() {
        return PlatformServices.get().gameDir();
    }

    public static Path dlcRoot() {
        return DlcPathConfig.getDlcRoot();
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
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Path path = Path.of(raw);
        if (!path.isAbsolute()) {
            path = gameDir().resolve(path);
        }
        return path.normalize();
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
