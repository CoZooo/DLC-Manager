package com.cozooo.dlc_manager.core;

import java.nio.file.Path;

public final class DlcTmpPaths {
    public static final String TMP_DIR = "tmp";
    public static final String LEGACY_TMP_DIR = "update_tmp";

    private DlcTmpPaths() {
    }

    public static Path root(Path base) {
        if (base == null) {
            return null;
        }
        return base.toAbsolutePath().normalize().resolve(TMP_DIR);
    }

    public static Path legacyRoot(Path base) {
        if (base == null) {
            return null;
        }
        return base.toAbsolutePath().normalize().resolve(LEGACY_TMP_DIR);
    }

    public static Path resolve(Path base, String first, String... more) {
        Path path = root(base);
        if (path == null) {
            return null;
        }
        if (first != null && !first.isBlank()) {
            path = path.resolve(first);
        }
        if (more != null) {
            for (String segment : more) {
                if (segment != null && !segment.isBlank()) {
                    path = path.resolve(segment);
                }
            }
        }
        return path;
    }

}
