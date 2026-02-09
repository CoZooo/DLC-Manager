package com.cozooo.dlc_manager.client.remote;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DlcRemoteMarker {
    public static final String PART_MARKER = "PART";

    private DlcRemoteMarker() {
    }

    public static boolean hasPartMarker(Path dlcRoot) {
        if (dlcRoot == null) {
            return false;
        }
        Path marker = resolveRemoteRoot(dlcRoot).resolve(PART_MARKER);
        return Files.exists(marker);
    }

    public static void writePartMarker(Path dlcRoot) {
        if (dlcRoot == null) {
            return;
        }
        Path remoteRoot = resolveRemoteRoot(dlcRoot);
        try {
            Files.createDirectories(remoteRoot);
            Path marker = remoteRoot.resolve(PART_MARKER);
            if (!Files.exists(marker)) {
                Files.createFile(marker);
            }
        } catch (IOException ignored) {
        }
    }

    public static void clearPartMarker(Path dlcRoot) {
        if (dlcRoot == null) {
            return;
        }
        Path marker = resolveRemoteRoot(dlcRoot).resolve(PART_MARKER);
        try {
            Files.deleteIfExists(marker);
        } catch (IOException ignored) {
        }
    }

    public static Path resolveRemoteRoot(Path dlcRoot) {
        return dlcRoot.resolve("remote");
    }
}
