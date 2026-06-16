package com.cozooo.dlc_manager.client.remote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcRemoteMarkerTest {
    @TempDir
    Path tempDir;

    @Test
    void writesDetectsAndClearsSkipMarker() {
        assertFalse(DlcRemoteMarker.hasPartMarker(tempDir));

        DlcRemoteMarker.writePartMarker(tempDir);

        Path marker = tempDir.resolve("remote").resolve(DlcRemoteMarker.SKIP_MARKER);
        assertTrue(Files.exists(marker));
        assertTrue(DlcRemoteMarker.hasPartMarker(tempDir));

        DlcRemoteMarker.clearPartMarker(tempDir);
        assertFalse(DlcRemoteMarker.hasPartMarker(tempDir));
    }

    @Test
    void nullRootIsNoop() {
        DlcRemoteMarker.writePartMarker(null);
        DlcRemoteMarker.clearPartMarker(null);

        assertFalse(DlcRemoteMarker.hasPartMarker(null));
    }

    @Test
    void remoteRootIsUnderDlcRoot() {
        assertEquals(tempDir.resolve("remote"), DlcRemoteMarker.resolveRemoteRoot(tempDir));
    }
}
