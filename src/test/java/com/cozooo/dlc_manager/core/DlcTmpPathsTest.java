package com.cozooo.dlc_manager.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DlcTmpPathsTest {
    @Test
    void resolvesTmpAndLegacyTmpRoots() {
        Path base = Path.of("game").toAbsolutePath().normalize();

        assertEquals(base.resolve("tmp"), DlcTmpPaths.root(Path.of("game")));
        assertEquals(base.resolve("update_tmp"), DlcTmpPaths.legacyRoot(Path.of("game")));
    }

    @Test
    void resolveSkipsBlankSegments() {
        Path base = Path.of("game").toAbsolutePath().normalize();

        assertEquals(base.resolve("tmp").resolve("backup").resolve("file.jar"), DlcTmpPaths.resolve(Path.of("game"), "backup", "", null, "file.jar"));
    }

    @Test
    void nullBaseReturnsNull() {
        assertNull(DlcTmpPaths.root(null));
        assertNull(DlcTmpPaths.legacyRoot(null));
        assertNull(DlcTmpPaths.resolve(null, "backup"));
    }
}
