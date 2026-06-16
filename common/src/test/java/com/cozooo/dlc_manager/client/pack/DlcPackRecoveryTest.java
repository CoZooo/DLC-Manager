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

    @Test
    void runningReplacementTransactionRestoresOldPackSnapshot() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packDir = dlcRoot.resolve("tmp").resolve("pack").resolve("Example");
        Path staging = dlcRoot.resolve("tmp").resolve("_staging").resolve("Example-replace");
        Path backupDir = staging.resolve("replacement_backup");
        Path backupPackDir = backupDir.resolve("pack_dir");
        Path installedSnapshot = backupDir.resolve("installed");
        Files.createDirectories(instance.resolve("mods"));
        Files.createDirectories(instance.resolve("config"));
        Files.createDirectories(packDir.resolve("config"));
        Files.createDirectories(backupPackDir.resolve("config"));
        Files.createDirectories(installedSnapshot.resolve("mods"));
        Files.createDirectories(installedSnapshot.resolve("config"));
        Files.writeString(instance.resolve("mods").resolve("Example.jar"), "new partial jar");
        Files.writeString(instance.resolve("config").resolve("old.toml"), "new partial config");
        Files.writeString(packDir.resolve("config").resolve("old.toml"), "new partial archived config");
        Files.writeString(backupPackDir.resolve("config").resolve("old.toml"), "original config before old pack");
        Files.writeString(installedSnapshot.resolve("mods").resolve("Example.jar"), "old pack jar");
        Files.writeString(installedSnapshot.resolve("config").resolve("old.toml"), "old pack config");
        new DlcPackMeta(
            "Example",
            "1.0.0",
            "STANDARD",
            "2026-06-15T00:00:00Z",
            java.util.List.of("mods/Example.jar", "config/old.toml")
        ).save(backupPackDir.resolve("meta.dc"));
        DlcPackTransaction txn = DlcPackTransaction.running("Example", "Example-replace");
        txn.replacement = true;
        txn.save(staging.resolve("txn.dc"));

        DlcPackRecovery.RecoveryReport report = DlcPackRecovery.recover(instance, dlcRoot);

        assertEquals(1, report.transactions);
        assertEquals(1, report.rolledBack);
        assertEquals(2, report.restored);
        assertEquals("old pack jar", Files.readString(instance.resolve("mods").resolve("Example.jar")));
        assertEquals("old pack config", Files.readString(instance.resolve("config").resolve("old.toml")));
        assertEquals("original config before old pack", Files.readString(packDir.resolve("config").resolve("old.toml")));
        assertTrue(Files.exists(packDir.resolve("meta.dc")));
        assertFalse(Files.exists(staging));
    }

    @Test
    void runningTransactionWithCommittedMetaOnlyCleansStaging() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packDir = dlcRoot.resolve("tmp").resolve("pack").resolve("Example");
        Path staging = dlcRoot.resolve("tmp").resolve("_staging").resolve("Example-3");
        Files.createDirectories(instance.resolve("mods"));
        Files.createDirectories(packDir.resolve("metadata"));
        Files.createDirectories(staging);
        Files.writeString(instance.resolve("mods").resolve("addon.jar"), "installed jar");
        Files.writeString(packDir.resolve("meta.dc"), """
            [basic]
            identifier = "Example"
            version = "1.0.0"
            format = "STANDARD"
            timestamp = "2026-06-15T00:00:00Z"

            installed = ["mods/addon.jar"]
            """);

        DlcPackTransaction txn = DlcPackTransaction.running("Example", "Example-3");
        txn.addWrittenPath("mods/addon.jar");
        txn.save(staging.resolve("txn.dc"));

        DlcPackRecovery.RecoveryReport report = DlcPackRecovery.recover(instance, dlcRoot);

        assertEquals(1, report.transactions);
        assertEquals(1, report.cleaned);
        assertEquals(0, report.rolledBack);
        assertEquals("installed jar", Files.readString(instance.resolve("mods").resolve("addon.jar")));
        assertFalse(Files.exists(staging));
        assertTrue(Files.exists(packDir.resolve("meta.dc")));
    }

    @Test
    void stagingWithoutTransactionIsKeptAsUnknown() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path staging = dlcRoot.resolve("tmp").resolve("_staging").resolve("Unknown-1");
        Files.createDirectories(staging);
        Files.writeString(staging.resolve("download.cache"), "partial");

        DlcPackRecovery.RecoveryReport report = DlcPackRecovery.recover(instance, dlcRoot);

        assertEquals(0, report.transactions);
        assertEquals(1, report.unknown);
        assertTrue(Files.exists(staging));
        assertEquals("partial", Files.readString(staging.resolve("download.cache")));
    }
}
