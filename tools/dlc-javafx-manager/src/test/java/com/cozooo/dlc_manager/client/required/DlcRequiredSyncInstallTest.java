package com.cozooo.dlc_manager.client.required;

import com.cozooo.dlc_manager.client.download.DlcLightHash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcRequiredSyncInstallTest {
    @TempDir
    Path gameDir;

    @Test
    void replacesExistingRequiredModAndVerifiesContent() throws Exception {
        DlcRequiredEntry entry = entry("mods/");
        Path source = Files.writeString(entry.resolveRequiredPath("library.jar"), "new version");
        Path target = gameDir.resolve("mods/library.jar");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "old version");

        assertTrue(DlcRequiredService.installSynced(entry, gameDir, () -> false));
        assertEquals("new version", Files.readString(target));
        assertEquals(DlcLightHash.compute(source), DlcLightHash.compute(target));
    }

    @Test
    void appliesEveryConfiguredTarget() throws Exception {
        DlcRequiredEntry entry = entry("mods/, cache/");
        Files.writeString(entry.resolveRequiredPath("library.jar"), "library");

        assertTrue(DlcRequiredService.installSynced(entry, gameDir, () -> false));
        assertEquals("library", Files.readString(gameDir.resolve("mods/library.jar")));
        assertEquals("library", Files.readString(gameDir.resolve("cache/library.jar")));
    }

    @Test
    void storageOnlyEntryIsNotReportedAsReapplied() throws Exception {
        DlcRequiredEntry entry = entry("");
        Path source = Files.writeString(entry.resolveRequiredPath("library.jar"), "library");

        assertFalse(DlcRequiredService.installSynced(entry, gameDir, () -> false));
        assertEquals("library", Files.readString(source));
        assertFalse(Files.exists(gameDir.resolve("mods")));
    }

    @Test
    void cancellationBeforeApplyDoesNotCreateTarget() throws Exception {
        DlcRequiredEntry entry = entry("mods/");
        Files.writeString(entry.resolveRequiredPath("library.jar"), "library");

        assertThrows(InterruptedIOException.class,
                () -> DlcRequiredService.installSynced(entry, gameDir, () -> true));
        assertFalse(Files.exists(gameDir.resolve("mods")));
    }

    @Test
    void cancellationDuringCopyPreservesPreviousTargetAndCleansTemporaryFile() throws Exception {
        DlcRequiredEntry entry = entry("mods/");
        Files.write(entry.resolveRequiredPath("library.jar"), new byte[256 * 1024]);
        Path target = gameDir.resolve("mods/library.jar");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "old version");
        AtomicInteger checks = new AtomicInteger();

        assertThrows(InterruptedIOException.class, () -> DlcRequiredService.installSynced(
                entry, gameDir, () -> checks.incrementAndGet() >= 3));
        assertEquals("old version", Files.readString(target));
        try (var files = Files.list(target.getParent())) {
            assertEquals(1, files.count());
        }
    }

    private DlcRequiredEntry entry(String targets) throws Exception {
        Path requiredDir = gameDir.resolve("DLC/required");
        Files.createDirectories(requiredDir);
        DlcRequiredEntry entry = new DlcRequiredEntry(requiredDir.resolve("library.dc"), requiredDir);
        entry.setRequiredFileName("library.jar");
        entry.setAppliedTargetRaw(targets);
        return entry;
    }
}
