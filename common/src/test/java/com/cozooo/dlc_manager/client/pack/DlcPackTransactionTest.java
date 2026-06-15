package com.cozooo.dlc_manager.client.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void saveAndLoadRoundTripsTransactionAsDc() {
        Path txnPath = tempDir.resolve("DLC").resolve("tmp").resolve("_staging").resolve("Example-1").resolve("txn.dc");
        DlcPackTransaction txn = DlcPackTransaction.running("Example", "Example-1");
        txn.addMovedPath("mods/old.jar");
        txn.addMovedPath("mods/old.jar");
        txn.addWrittenPath("mods/new.jar");
        txn.addWrittenPath("../bad");

        assertTrue(txn.save(txnPath));
        DlcPackTransaction loaded = DlcPackTransaction.load(txnPath);

        assertEquals(DlcPackTransaction.Status.RUNNING, loaded.status);
        assertEquals("Example", loaded.identifier);
        assertEquals("Example-1", loaded.stagingId);
        assertEquals(List.of("mods/old.jar"), loaded.movedPaths);
        assertEquals(List.of("mods/new.jar"), loaded.writtenPaths);
    }

    @Test
    void parsesStatusesAndRejectsInvalidData() {
        assertEquals(DlcPackTransaction.Status.CANCELLED, DlcPackTransaction.parseStatus("cancelled"));
        assertNull(DlcPackTransaction.parseStatus("unknown"));
        assertNull(DlcPackTransaction.load(tempDir.resolve("missing.dc")));
        assertFalse(DlcPackTransaction.running("Example", "1").save(null));
    }
}
