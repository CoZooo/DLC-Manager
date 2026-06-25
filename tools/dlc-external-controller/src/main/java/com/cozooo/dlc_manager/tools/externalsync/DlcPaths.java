package com.cozooo.dlc_manager.tools.externalsync;

import java.nio.file.Path;

final class DlcPaths {
    final Path gameDir;
    final Path modsDir;
    final Path dlcRoot;

    private DlcPaths(Path gameDir, Path modsDir, Path dlcRoot) {
        this.gameDir = gameDir;
        this.modsDir = modsDir;
        this.dlcRoot = dlcRoot;
    }

    static DlcPaths from(SyncModels.SyncRequest request) {
        Path gameDir = Path.of(required(request.gameDir, "missing --game-dir")).toAbsolutePath().normalize();
        Path modsDir = request.modsDir == null || request.modsDir.isBlank()
            ? gameDir.resolve("mods")
            : Path.of(request.modsDir).toAbsolutePath().normalize();
        Path dlcRoot = request.dlcRoot == null || request.dlcRoot.isBlank()
            ? gameDir.resolve("DLC")
            : Path.of(request.dlcRoot).toAbsolutePath().normalize();
        return new DlcPaths(gameDir, modsDir, dlcRoot);
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
