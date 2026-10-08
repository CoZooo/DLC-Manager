package com.cozooo.dlc_manager.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DlcDistributionPaths {
    public static final String DISTRIBUTION_PROPERTY = "dlc_manager.distribution.path";
    public static final String PAYLOAD_PROPERTY = "dlc_manager.distribution.payload";

    private DlcDistributionPaths() {}

    public static Path originalModFile(String modId, Path loadedFile) {
        if (!"dlc_manager".equals(modId) || loadedFile == null) return loadedFile;
        try {
            String distribution = System.getProperty(DISTRIBUTION_PROPERTY);
            String payload = System.getProperty(PAYLOAD_PROPERTY);
            if (distribution != null && payload != null
                && loadedFile.toAbsolutePath().normalize().equals(Path.of(payload).toAbsolutePath().normalize())) {
                Path original = Path.of(distribution).toAbsolutePath().normalize();
                if (Files.isRegularFile(original)) return original;
            }
        } catch (RuntimeException ignored) {
        }
        return loadedFile;
    }
}
