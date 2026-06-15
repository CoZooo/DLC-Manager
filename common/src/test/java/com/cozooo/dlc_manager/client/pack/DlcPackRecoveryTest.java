package com.cozooo.dlc_manager.client.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPackRecoveryTest {
    @TempDir
    Path tempDir;

    @Test
    void runningTransactionRestoresArchivedFilesAndDeletesPartialWrites() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packDir = dlcRoot.resolve("tmp").resolve("pack").resolve("Example");
        Path staging = dlcRoot.resolve("tmp").resolve("_staging").resolve("Example-1");
        Files.createDirectories(instance.resolve("mods"));
        Files.createDirectories(instance.resolve("config"));
        Files.createDirectories(packDir.resolve("config"));
        Files.createDirectories(staging);
        Files.writeString(instance.resolve("mods").resolve("addon.jar"), "new jar");
        Files.writeString(instance.resolve("config").resolve("old.toml"), "new config");
        Files.writeString(packDir.resolve("config").resolve("old.toml"), "old config");

        DlcPackTransaction txn = DlcPackTransaction.running("Example", "Example-1");
        txn.addWrittenPath("mods/addon.jar");
        txn.addWrittenPath("config/old.toml");
        txn.save(staging.resolve("txn.dc"));

        DlcPackRecovery.RecoveryReport report = DlcPackRecovery.recover(instance, dlcRoot);

        assertEquals(1, report.transactions);
        assertEquals(1, report.rolledBack);
        assertEquals(2, report.deletedWritten);
        assertEquals(1, report.restored);
        assertFalse(Files.exists(instance.resolve("mods").resolve("addon.jar")));
        assertEquals("old config", Files.readString(instance.resolve("config").resolve("old.toml")));
        assertFalse(Files.exists(staging));
        assertFalse(Files.exists(packDir));
    }

    @Test
    void cancelledTransactionOnlyCleansStaging() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packDir = dlcRoot.resolve("tmp").resolve("pack").resolve("Example");
        Path staging = dlcRoot.resolve("tmp").resolve("_staging").resolve("Example-2");
        Files.createDirectories(instance.resolve("mods"));
        Files.createDirectories(packDir.resolve("config"));
        Files.createDirectories(staging);
        Files.writeString(instance.resolve("mods").resolve("addon.jar"), "installed jar");
        Files.writeString(packDir.resolve("config").resolve("old.toml"), "archived config");

        DlcPackTransaction txn = new DlcPackTransaction(
            DlcPackTransaction.Status.CANCELLED,
            "Example",
            "Example-2",
            java.util.List.of("config/old.toml"),
            java.util.List.of("mods/addon.jar"),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of()
        );
        txn.save(staging.resolve("txn.dc"));

        DlcPackRecovery.RecoveryReport report = DlcPackRecovery.recover(instance, dlcRoot);

        assertEquals(1, report.transactions);
        assertEquals(0, report.rolledBack);
        assertEquals(1, report.cleaned);
        assertEquals("installed jar", Files.readString(instance.resolve("mods").resolve("addon.jar")));
        assertTrue(Files.exists(packDir.resolve("config").resolve("old.toml")));
        assertFalse(Files.exists(staging));
    }
}
