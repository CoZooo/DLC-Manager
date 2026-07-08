package com.cozooo.dlc_manager.client.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPackTransactionTest {
    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundTripsTransactionAsDc() throws Exception {
        Path txnPath = tempDir.resolve("DLC").resolve("tmp").resolve("_staging").resolve("Example-1").resolve("txn.dc");
        DlcPackTransaction txn = DlcPackTransaction.running("Example", "Example-1");
        txn.addMovedPath("mods/old.jar");
        txn.addMovedPath("mods/old.jar");
        txn.addWrittenPath("mods/new.jar");
        txn.addWrittenPath("../bad");
        txn.addEnabledDlc("DLC-A");
        txn.addDisabledDlc("DLC:B");
        txn.addEnabledPreset("Preset A");
        txn.addDisabledPreset("Preset-B");

        assertTrue(txn.save(txnPath));
        try (var stream = Files.list(txnPath.getParent())) {
            assertEquals(List.of("txn.dc"), stream.map(path -> path.getFileName().toString()).sorted().toList());
        }
        DlcPackTransaction loaded = DlcPackTransaction.load(txnPath);

        assertEquals(DlcPackTransaction.Status.RUNNING, loaded.status);
        assertEquals("Example", loaded.identifier);
        assertEquals("Example-1", loaded.stagingId);
        assertEquals(List.of("mods/old.jar"), loaded.movedPaths);
        assertEquals(List.of("mods/new.jar"), loaded.writtenPaths);
        assertEquals(List.of("DLC-A"), loaded.enabledDlc);
        assertEquals(List.of("DLC:B"), loaded.disabledDlc);
        assertEquals(List.of("Preset A"), loaded.enabledPresets);
        assertEquals(List.of("Preset-B"), loaded.disabledPresets);
        assertFalse(loaded.replacement);
    }

    @Test
    void saveAndLoadKeepsReplacementMarker() throws Exception {
        Path txnPath = tempDir.resolve("txn.dc");
        DlcPackTransaction txn = DlcPackTransaction.running("Example", "Example-replace");
        txn.replacement = true;

        assertTrue(txn.save(txnPath));
        DlcPackTransaction loaded = DlcPackTransaction.load(txnPath);

        assertTrue(loaded.replacement);
    }

    @Test
    void parsesStatusesAndRejectsInvalidData() {
        assertEquals(DlcPackTransaction.Status.CANCELLED, DlcPackTransaction.parseStatus("cancelled"));
        assertNull(DlcPackTransaction.parseStatus("unknown"));
        assertNull(DlcPackTransaction.load(tempDir.resolve("missing.dc")));
        assertFalse(DlcPackTransaction.running("Example", "1").save(null));
    }
}
