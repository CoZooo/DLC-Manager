package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcUpdateWorldBackupTest {
    @TempDir Path game;

    @AfterEach void reset() {
        PlatformServices.setProvider(null);
        DlcConfig.setDlcRoot("DLC");
    }

    private Path setup() throws Exception {
        PlatformServices.setProvider(new TestPlatformProvider(game, game.resolve("config")));
        Path root = game.resolve("DLC");
        DlcConfig.setDlcRoot(root.toString());
        Files.createDirectories(root.resolve("info"));
        Files.writeString(root.resolve("info/version.dc"), "base='1'\ndlc='1'\n");
        Files.createDirectories(game.resolve("mods"));
        return root;
    }

    private Path world(String name, String value) throws Exception {
        Path world = game.resolve("saves").resolve(name);
        Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("level.dat"), value);
        Files.writeString(world.resolve("region/r.0.0.mca"), value);
        return world;
    }

    private DlcUpdatePackage pack() throws Exception {
        Path zip = game.resolve(UUID.randomUUID() + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("update.dc"));
            out.write("[version]\nbase='1'\ndlc='1'\ndlc_new='2'\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return DlcUpdatePackage.fromZip(zip);
    }

    @Test void worldsAreSortedBySaveTimeWithNewestFirst() throws Exception {
        world("old", "old");
        world("new", "new");
        Files.createDirectories(game.resolve("saves/not-a-world"));
        Files.setLastModifiedTime(game.resolve("saves/old/level.dat"), FileTime.fromMillis(1000));
        Files.setLastModifiedTime(game.resolve("saves/new/level.dat"), FileTime.fromMillis(2000));
        assertEquals(List.of("new", "old"), DlcUpdateWorldBackup.listWorlds(game).stream().map(DlcUpdateWorldBackup.World::name).toList());
    }

    @Test void restoreCopiesBackupAndCanBeRepeatedWithoutTouchingOtherWorlds() throws Exception {
        Path root = setup().resolve("tmp/1+2");
        Path world = world("世界一", "original");
        world("other", "keep");
        String id = DlcUpdateWorldBackup.capture(game, root, List.of("世界一"), "update");
        Files.writeString(world.resolve("level.dat"), "played");
        Files.writeString(world.resolve("region/new.mca"), "extra");
        DlcUpdateWorldBackup.restore(game, root, id, List.of("世界一"));
        assertEquals("original", Files.readString(world.resolve("level.dat")));
        assertFalse(Files.exists(world.resolve("region/new.mca")));
        assertEquals("keep", Files.readString(game.resolve("saves/other/level.dat")));
        assertEquals("original", Files.readString(root.resolve("worlds/data/" + id + "/世界一/level.dat")));
        Files.writeString(world.resolve("level.dat"), "played again");
        DlcUpdateWorldBackup.restore(game, root, id, null);
        assertEquals("original", Files.readString(world.resolve("level.dat")));
        assertEquals("1", DlcUpdateVersion.load(game.resolve("DLC")).getDlc());
        assertEquals(3, DlcUpdateWorldBackup.list(root).size());
    }

    @Test void lockedWorldAndUnsafePathsFailWithoutMutatingWorld() throws Exception {
        Path root = setup().resolve("tmp/1+2");
        Path world = world("world", "original");
        Files.writeString(world.resolve("session.lock"), "lock");
        try (FileChannel channel = FileChannel.open(world.resolve("session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertTrue(lock.isValid());
            assertThrows(IOException.class, () -> DlcUpdateWorldBackup.capture(game, root, List.of("world"), "update"));
        }
        assertThrows(IOException.class, () -> DlcUpdateWorldBackup.capture(game, root, List.of("../outside"), "update"));
        Path linked = world.resolve("linked");
        Files.createSymbolicLink(linked, game.resolve("DLC"));
        assertThrows(IOException.class, () -> DlcUpdateWorldBackup.capture(game, root, List.of("world"), "update"));
        assertEquals("original", Files.readString(world.resolve("level.dat")));
        assertTrue(DlcUpdateWorldBackup.list(root).isEmpty());
    }

    @Test void restoreFailureRecoversWorldsAlreadyReplaced() throws Exception {
        Path root = setup().resolve("tmp/1+2");
        Path a = world("a", "old a");
        world("b", "old b");
        String id = DlcUpdateWorldBackup.capture(game, root, List.of("a", "b"), "update");
        Files.writeString(a.resolve("level.dat"), "new a");
        Files.move(root.resolve("worlds/data/" + id + "/b"), root.resolve("damaged-backup"));
        assertThrows(IOException.class, () -> DlcUpdateWorldBackup.restore(game, root, id, null));
        assertEquals("new a", Files.readString(a.resolve("level.dat")));
        assertEquals("old b", Files.readString(game.resolve("saves/b/level.dat")));
    }

    @Test void updateRollbackAndReinstallHonorWorldChoice() throws Exception {
        Path root = setup();
        Path level = world("world", "before update").resolve("level.dat");
        DlcUpdatePackage pkg = pack();
        pkg.setBackupWorlds(List.of("world"));
        assertTrue(DlcUpdateService.applyUpdates(List.of(pkg), false).isSuccess());
        Files.writeString(level, "played after update");
        assertTrue(DlcUpdateService.rollbackTo("1+1").isSuccess());
        assertEquals("before update", Files.readString(level));
        pkg.setRestoreWorldsAfterReapply(true);
        assertTrue(DlcUpdateService.applyUpdates(List.of(pkg), false).isSuccess());
        assertEquals("played after update", Files.readString(level));
        assertTrue(DlcUpdateService.rollbackTo("1+1").isSuccess());
        Files.writeString(level, "played on old version");
        pkg.setRestoreWorldsAfterReapply(false);
        assertTrue(DlcUpdateService.applyUpdates(List.of(pkg), false).isSuccess());
        assertEquals("played on old version", Files.readString(level));
        assertEquals("2", DlcUpdateVersion.load(root).getDlc());
    }

    @Test void pendingUpdateRetainsWorldBackupAndReinstallChoices() throws Exception {
        Path root = setup();
        Path level = world("world", "before update").resolve("level.dat");
        DlcUpdatePackage pkg = pack();
        pkg.setBackupWorlds(List.of("world"));
        DlcPendingUpdate.schedule(List.of(pkg), false);
        assertTrue(DlcPendingUpdate.process(ignored -> {}, false));
        assertTrue(DlcPendingUpdate.readResult().get("success").getAsBoolean(), DlcPendingUpdate.readResult().toString());
        Files.writeString(level, "played after update");
        assertTrue(DlcUpdateService.rollbackTo("1+1").isSuccess());
        pkg.setRestoreWorldsAfterReapply(true);
        DlcPendingUpdate.schedule(List.of(pkg), false);
        assertTrue(DlcPendingUpdate.process(ignored -> {}, true));
        assertTrue(DlcPendingUpdate.readResult().get("success").getAsBoolean(), DlcPendingUpdate.readResult().toString());
        assertEquals("played after update", Files.readString(level));
        assertEquals("2", DlcUpdateVersion.load(root).getDlc());
    }
    @Test void absentWorldRestoresAbsenceAndBackupsCannotBeInsideSourceWorld() throws Exception {
        Path root = setup().resolve("tmp/1+2");
        String missing = DlcUpdateWorldBackup.capture(game, root, List.of("later"), "update");
        world("later", "created later");
        DlcUpdateWorldBackup.restore(game, root, missing, null);
        assertFalse(Files.exists(game.resolve("saves/later")));
        Path source = world("world", "original");
        assertThrows(IOException.class, () -> DlcUpdateWorldBackup.capture(game, source.resolve("nested-backup"), List.of("world"), "update"));
        assertEquals("original", Files.readString(source.resolve("level.dat")));
    }

    @Test void lockedWorldStopsUpdateBeforeFileOperationsAndVersionChanges() throws Exception {
        Path root = setup();
        Path world = world("world", "original");
        Files.writeString(world.resolve("session.lock"), "lock");
        DlcUpdatePackage pkg = pack();
        pkg.setBackupWorlds(List.of("world"));
        try (FileChannel channel = FileChannel.open(world.resolve("session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertTrue(lock.isValid());
            var result = DlcUpdateService.applyUpdates(List.of(pkg), false);
            assertFalse(result.isSuccess());
            assertEquals("1", DlcUpdateVersion.load(root).getDlc());
        }
        var result = DlcUpdateService.applyUpdates(List.of(pkg), false);
        assertTrue(result.isSuccess(), result.getMessage());
        Path backup = DlcUpdateService.resolveMetaRootByPair(root, "1", "2");
        String before = DlcUpdateWorldBackup.pointer(backup, "before");
        assertFalse(Files.exists(backup.resolve("worlds/data/" + before + "/world/session.lock")));
        try (FileChannel channel = FileChannel.open(world.resolve("session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertTrue(lock.isValid());
            assertFalse(DlcUpdateService.rollbackTo("1+1").isSuccess());
            assertEquals("2", DlcUpdateVersion.load(root).getDlc());
        }
    }

    @Test void failedPendingChainRecoversWorldRestoredDuringReinstall() throws Exception {
        Path root = setup();
        Path level = world("world", "before update").resolve("level.dat");
        DlcUpdatePackage pkg = pack();
        pkg.setBackupWorlds(List.of("world"));
        assertTrue(DlcUpdateService.applyUpdates(List.of(pkg), false).isSuccess());
        Files.writeString(level, "played after update");
        assertTrue(DlcUpdateService.rollbackTo("1+1").isSuccess());
        Path zip = game.resolve("failing-update.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("update.dc"));
            out.write(("[version]\nbase='1'\ndlc='2'\ndlc_new='3'\n[file]\n  [modify]\n    'config/missing.cfg':\n      find='old'\n      replace='new'\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        pkg.setRestoreWorldsAfterReapply(true);
        DlcPendingUpdate.schedule(List.of(pkg, DlcUpdatePackage.fromZip(zip)), false);
        assertTrue(DlcPendingUpdate.process(ignored -> {}, false));
        assertFalse(DlcPendingUpdate.readResult().get("success").getAsBoolean());
        assertEquals("before update", Files.readString(level));
        assertEquals("1", DlcUpdateVersion.load(root).getDlc());
    }

    @Test void journalUsesOriginalExternalRootsAfterConfigurationChanges(@TempDir Path external) throws Exception {
        setup();
        DlcConfig.setDlcRoot(external.toString());
        world("world", "original");
        Path root = external.resolve("tmp/1+2");
        String first = DlcUpdateWorldBackup.capture(game, root, List.of("world"), "update");
        DlcUpdateWorldBackup.point(root, "before", first);
        String second = DlcUpdateWorldBackup.capture(game, root, List.of("world"), "update");
        try (var journal = DlcUpdateJournal.open(game, external.resolve("tmp/update/jobs/test"))) {
            DlcConfig.setDlcRoot(game.resolve("changed-root").toString());
            DlcUpdateWorldBackup.point(root, "before", second);
            assertEquals(second, DlcUpdateWorldBackup.pointer(root, "before"));
            journal.rollback();
        }
        assertEquals(first, DlcUpdateWorldBackup.pointer(root, "before"));
    }

    @Test void worldCanReturnFromAToBAndOnlyTemporaryBackupsAreRemoved() throws Exception {
        Path root = setup().resolve("tmp/1+2");
        Path level = world("world", "A").resolve("level.dat");
        String a = DlcUpdateWorldBackup.capture(game, root, List.of("world"), "update");
        DlcUpdateWorldBackup.point(root, "before", a);
        for (int cycle = 0; cycle < 2; cycle++) {
            Files.writeString(level, "B" + cycle);
            DlcUpdateWorldBackup.restore(game, root, a, List.of("world"));
            assertEquals("A", Files.readString(level));
            String b = DlcUpdateWorldBackup.returnTargets(root).get("world");
            assertNotNull(b);
            DlcUpdateWorldBackup.returnBeforeRestore(game, root, "world");
            assertEquals("B" + cycle, Files.readString(level));
            assertEquals(a, DlcUpdateWorldBackup.pointer(root, "before"));
            assertTrue(DlcUpdateWorldBackup.returnTargets(root).isEmpty());
            assertFalse(Files.exists(root.resolve("worlds/sets/" + b + ".dc")));
            assertEquals(List.of(a), DlcUpdateWorldBackup.list(root).stream().map(DlcUpdateWorldBackup.Snapshot::id).toList());
        }
    }

    @Test void returningOneWorldRetainsOtherWorldsTemporaryState() throws Exception {
        Path root = setup().resolve("tmp/1+2");
        Path first = world("one", "A1").resolve("level.dat");
        Path second = world("two", "A2").resolve("level.dat");
        String a = DlcUpdateWorldBackup.capture(game, root, List.of("one", "two"), "update");
        DlcUpdateWorldBackup.point(root, "before", a);
        Files.writeString(first, "B1");
        Files.writeString(second, "B2");
        DlcUpdateWorldBackup.rollback(game, root);
        String b = DlcUpdateWorldBackup.pointer(root, "after");
        DlcUpdateWorldBackup.returnBeforeRestore(game, root, "one");
        assertEquals("B1", Files.readString(first));
        assertEquals("A2", Files.readString(second));
        assertEquals(List.of("two"), DlcUpdateWorldBackup.worldNames(root, b));
        assertEquals(b, DlcUpdateWorldBackup.returnTargets(root).get("two"));
        DlcUpdateWorldBackup.returnBeforeRestore(game, root, "two");
        assertEquals("B2", Files.readString(second));
        assertNull(DlcUpdateWorldBackup.pointer(root, "after"));
        assertEquals(List.of("one", "two"), DlcUpdateWorldBackup.worldNames(root, a));
    }

    @Test void failedReturnKeepsOriginalAndTemporaryBackups() throws Exception {
        Path root = setup().resolve("tmp/1+2");
        Path level = world("world", "A").resolve("level.dat");
        String a = DlcUpdateWorldBackup.capture(game, root, List.of("world"), "update");
        Files.writeString(level, "B");
        DlcUpdateWorldBackup.restore(game, root, a, null);
        String b = DlcUpdateWorldBackup.returnTargets(root).get("world");
        Files.move(root.resolve("worlds/data/" + b + "/world"), root.resolve("damaged-state"));
        assertThrows(IOException.class, () -> DlcUpdateWorldBackup.returnBeforeRestore(game, root, "world"));
        assertEquals("A", Files.readString(level));
        assertEquals(b, DlcUpdateWorldBackup.returnTargets(root).get("world"));
        assertTrue(Files.exists(root.resolve("worlds/sets/" + a + ".dc")));
        assertTrue(Files.exists(root.resolve("worlds/sets/" + b + ".dc")));
    }

    @Test void manualDeletionCanRemoveOriginalAndAllBackupsWithoutChangingWorldsOrUpdateFiles() throws Exception {
        Path root = setup().resolve("tmp/1+2");
        Path level = world("world", "A").resolve("level.dat");
        world("other", "other");
        String a = DlcUpdateWorldBackup.capture(game, root, List.of("world", "other"), "update");
        DlcUpdateWorldBackup.point(root, "before", a);
        Files.writeString(level, "B");
        DlcUpdateWorldBackup.rollback(game, root);
        Files.writeString(root.resolve("meta.dc"), "original update metadata");
        DlcUpdateWorldBackup.delete(root, a, List.of("world"));
        assertEquals(List.of("other"), DlcUpdateWorldBackup.worldNames(root, a));
        assertEquals("A", Files.readString(level));
        DlcUpdateWorldBackup.deleteAll(root);
        assertTrue(DlcUpdateWorldBackup.list(root).isEmpty());
        assertTrue(DlcUpdateWorldBackup.returnTargets(root).isEmpty());
        assertNull(DlcUpdateWorldBackup.pointer(root, "before"));
        assertEquals("A", Files.readString(level));
        assertEquals("original update metadata", Files.readString(root.resolve("meta.dc")));
        assertEquals("1", DlcUpdateVersion.load(game.resolve("DLC")).getDlc());
    }

    @Test void deletionRejectsLinksAndPathEscapesWithoutTouchingTheirTargets() throws Exception {
        Path root = setup().resolve("tmp/1+2");
        world("world", "A");
        String a = DlcUpdateWorldBackup.capture(game, root, List.of("world"), "update");
        Path external = Files.writeString(game.resolve("keep.txt"), "keep");
        Files.createSymbolicLink(root.resolve("worlds/data/" + a + "/world/link"), external);
        assertThrows(IOException.class, () -> DlcUpdateWorldBackup.delete(root, a, null));
        assertThrows(IOException.class, () -> DlcUpdateWorldBackup.delete(root, "../keep.txt", null));
        assertThrows(IOException.class, () -> DlcUpdateWorldBackup.deleteAll(root));
        assertEquals("keep", Files.readString(external));
        assertTrue(Files.exists(root.resolve("worlds/sets/" + a + ".dc")));
    }

}
