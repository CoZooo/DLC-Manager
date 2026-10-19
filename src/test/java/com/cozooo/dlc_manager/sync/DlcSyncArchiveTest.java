package com.cozooo.dlc_manager.sync;

import com.cozooo.dlc_manager.core.DlcFileMatch;
import com.cozooo.dlc_manager.core.DlcResourceHistory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcSyncArchiveTest {
    @TempDir Path temp;
    private Path source, target, archive;
    private List<SyncModel.File> reused;

    private void prepare() throws Exception {
        source = Files.createDirectories(temp.resolve("source")); target = Files.createDirectories(temp.resolve("target"));
        Files.writeString(source.resolve("same.txt"), "unchanged".repeat(1000));
        Files.copy(source.resolve("same.txt"), target.resolve("same.txt"));
        Files.writeString(source.resolve("changed.txt"), "new content"); Files.writeString(target.resolve("changed.txt"), "old content");
        Files.writeString(target.resolve("obsolete.txt"), "old extra content"); Files.createDirectory(source.resolve("empty"));
        reused = SyncArchive.reusable(source, SyncArchive.inventory(target, () -> false), () -> false);
        archive = temp.resolve("delta.zip"); SyncArchive.pack(source, archive, () -> false, reused);
    }
    private SyncModel.Journal journal(boolean failBackup) {
        return new SyncModel.Journal() {
            public void capture(Path path) throws IOException {
                assertEquals(target, path);
                assertEquals("old content", Files.readString(path.resolve("changed.txt")));
                if (failBackup) throw new IOException("Backup failed");
                copy(path, temp.resolve("backup"));
            }
            public void rollback() throws IOException { DlcResourceHistory.delete(target); copy(temp.resolve("backup"), target); }
            public void commit() {}
            public void close() {}
        };
    }
    private void copy(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path to = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(to); else { Files.createDirectories(to.getParent()); Files.copy(path, to); }
            }
        }
    }
    @Test void transmitsOnlyChangedDirectoryFilesAndBacksUpBeforeReplacement() throws Exception {
        prepare(); assertEquals(List.of("same.txt"), reused.stream().map(SyncModel.File::id).toList());
        try (ZipFile zip = new ZipFile(archive.toFile())) { assertNull(zip.getEntry("same.txt")); assertNotNull(zip.getEntry("changed.txt")); }
        SyncModel.Journal journal = journal(false);
        SyncArchive.install(archive, target, SyncArchive.size(source), DlcFileMatch.fullHash(source), journal, () -> false, reused);
        assertEquals(DlcFileMatch.fullHash(source), DlcFileMatch.fullHash(target));
        assertEquals("old content", Files.readString(temp.resolve("backup/changed.txt")));
        assertFalse(Files.exists(target.resolve("obsolete.txt"))); assertTrue(Files.isDirectory(target.resolve("empty")));
        journal.rollback(); assertEquals("old content", Files.readString(target.resolve("changed.txt")));
        assertTrue(Files.exists(target.resolve("obsolete.txt")));
    }
    @Test void directoryDeltaTravelsThroughSyncEndpointsAndRetainsItsBackupAfterCommit() throws Exception {
        prepare();
        DirectoryAdapter left = new DirectoryAdapter(temp.resolve("left"), source), right = new DirectoryAdapter(temp.resolve("right"), target);
        List<String> reusedNames = new ArrayList<>();
        try (SyncEndpoint sourceEndpoint = new SyncEndpoint(left); SyncEndpoint targetEndpoint = new SyncEndpoint(right)) {
            SyncModel.Peer tracked = request -> {
                var response = sourceEndpoint.call(request);
                if (request.get("action").getAsString().equals("read") && response.has("reused")) {
                    for (var file : response.getAsJsonArray("reused")) reusedNames.add(file.getAsJsonObject().get("id").getAsString());
                }
                return response;
            };
            var rows = SyncModel.plan(SyncClient.manifest(sourceEndpoint), SyncClient.manifest(targetEndpoint), SyncModel.Direction.PUSH, false, SyncModel.Mode.FILES);
            var result = new SyncClient(tracked, targetEndpoint, targetEndpoint).execute(rows, SyncModel.Mode.FILES, false, (phase, resource, done, total) -> {});
            assertTrue(result.errors().isEmpty(), result.errors().toString()); assertEquals(1, result.completed());
            assertEquals(List.of("same.txt"), reusedNames);
            assertEquals(DlcFileMatch.fullHash(left.root.resolve("data/a")), DlcFileMatch.fullHash(right.root.resolve("data/a")));
            assertEquals("old content", Files.readString(right.snapshots.get(right.root.resolve("data/a")).resolve("changed.txt")));
        }
    }
    private final class DirectoryAdapter implements SyncModel.Adapter {
        private final Path root;
        private final Map<Path, Path> snapshots = new LinkedHashMap<>();
        private DirectoryAdapter(Path root, Path data) throws IOException {
            this.root = root; Files.createDirectories(root.resolve("remote")); Files.writeString(root.resolve("remote/a.dc"), "same config"); copy(data, root.resolve("data/a"));
        }
        public Path workDirectory() { return root.resolve("work"); }
        public List<SyncModel.Item> manifest() throws IOException {
            Path config = root.resolve("remote/a.dc"), data = root.resolve("data/a");
            return List.of(new SyncModel.Item("remote/a.dc", "a", new SyncModel.File("remote/a.dc", Files.size(config), DlcFileMatch.hash(config)),
                List.of(new SyncModel.File("data/a", SyncArchive.size(data), DlcFileMatch.hash(data))), false));
        }
        public Path resolve(String id) { return root.resolve(id); }
        public void validateKey(String key) { if (!key.equals("remote/a.dc")) throw new IllegalArgumentException(); }
        public SyncModel.Journal journal(Path directory) {
            return new SyncModel.Journal() {
                private final Map<Path, Path> saved = new LinkedHashMap<>();
                public void capture(Path path) throws IOException {
                    if (saved.containsKey(path)) return;
                    Path backup = directory.resolve("backups/" + saved.size());
                    if (Files.isDirectory(path)) copy(path, backup); else { Files.createDirectories(backup.getParent()); Files.copy(path, backup); }
                    saved.put(path, backup); snapshots.put(path, backup);
                }
                public void rollback() throws IOException {
                    for (var entry : saved.entrySet()) {
                        DlcResourceHistory.delete(entry.getKey());
                        if (Files.isDirectory(entry.getValue())) copy(entry.getValue(), entry.getKey()); else Files.copy(entry.getValue(), entry.getKey());
                    }
                }
                public void commit() {}
                public void close() {}
            };
        }
        public void prepare(String key, boolean force, java.util.function.BooleanSupplier canceled, SyncModel.Progress progress) {}
        public boolean apply(String key, Boolean enabled, SyncModel.Journal journal, java.util.function.BooleanSupplier canceled) { return false; }
        public void reload() {}
        public void recover() {}
        public void recoverOperation(String operation) {}
    }
    @Test void rollbackAlsoRemovesParentsCreatedForANewResourceDirectory() throws Exception {
        prepare(); target = temp.resolve("new/nested/resource"); SyncArchive.pack(source, archive, () -> false);
        com.cozooo.dlc_manager.platform.PlatformServices.setProvider(new com.cozooo.dlc_manager.test.TestPlatformProvider(temp, temp.resolve("config")));
        com.cozooo.dlc_manager.core.DlcConfig.setDlcRoot("DLC");
        Path directory = temp.resolve("DLC/tmp/sync/" + UUID.randomUUID() + "/item/journal");
        try (var backup = com.cozooo.dlc_manager.client.update.DlcUpdateJournal.openSync(temp, directory)) {
            SyncModel.Journal journal = new SyncModel.Journal() {
                public void capture(Path path) throws IOException { backup.capture(path); }
                public void rollback() throws IOException { backup.rollback(); }
                public void commit() {}
                public void close() {}
            };
            SyncArchive.install(archive, target, SyncArchive.size(source), DlcFileMatch.fullHash(source), journal, () -> false);
            assertTrue(Files.isDirectory(target)); journal.rollback();
            assertFalse(Files.exists(temp.resolve("new")), "Rollback must remove all newly created parent directories");
        } finally {
            com.cozooo.dlc_manager.platform.PlatformServices.setProvider(null);
            com.cozooo.dlc_manager.core.DlcConfig.setDlcRoot("DLC");
        }
    }
    @Test void backupFailureLeavesTheOriginalDirectoryUntouched() throws Exception {
        prepare(); String original = DlcFileMatch.fullHash(target);
        assertThrows(IOException.class, () -> SyncArchive.install(archive, target, SyncArchive.size(source), DlcFileMatch.fullHash(source), journal(true), () -> false, reused));
        assertEquals(original, DlcFileMatch.fullHash(target));
    }
    @Test void changedReusableFileOrEscapingPathCannotReplaceTheOriginalDirectory() throws Exception {
        prepare(); Files.writeString(target.resolve("same.txt"), "changed after comparison");
        String original = DlcFileMatch.fullHash(target);
        assertThrows(IOException.class, () -> SyncArchive.install(archive, target, SyncArchive.size(source), DlcFileMatch.fullHash(source), journal(false), () -> false, reused));
        assertEquals(original, DlcFileMatch.fullHash(target));
        assertThrows(IOException.class, () -> SyncArchive.install(archive, target, 0, "invalid", journal(false), () -> false, List.of(new SyncModel.File("../outside.txt", 0, "invalid"))));
        assertEquals(original, DlcFileMatch.fullHash(target));
    }
}
