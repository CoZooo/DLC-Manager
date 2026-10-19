package com.cozooo.dlc_manager.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcFileMatchTest {
    @TempDir Path directory;
    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test void preservesFullSha256ValuesForMatchingAndStoredHashes() throws Exception {
        for (int size : new int[]{0, 17, 131072, 131073, 300000}) {
            byte[] bytes = new byte[size];
            for (int index = 0; index < size; index++) bytes[index] = (byte)index;
            Path file = Files.write(directory.resolve(size + ".jar"), bytes);
            assertEquals(sha256(bytes), DlcFileMatch.hash(file));
            assertEquals(sha256(bytes), DlcFileMatch.hash(file));
            assertEquals(sha256(bytes), DlcFileMatch.fullHash(file));
            assertEquals(sha256(bytes), DlcFileMatch.hash(new ByteArrayInputStream(bytes)));
        }
    }

    @Test void lightweightFingerprintUsesSizeAndFirstAndLast64KiBOnlyForLargeFiles() throws Exception {
        byte[] small = new byte[131072];
        Path file = Files.write(directory.resolve("main.jar"), small);
        assertEquals(sha256(small), DlcFileMatch.lightHash(file));
        byte[] large = new byte[200000]; large[0] = 1; large[large.length - 1] = 2;
        Files.write(file, large);
        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        expected.update(ByteBuffer.allocate(8).putLong(large.length).array());
        expected.update(large, 0, 65536); expected.update(large, large.length - 65536, 65536);
        assertEquals("light-" + HexFormat.of().formatHex(expected.digest()), DlcFileMatch.lightHash(file));
    }

    @Test void matchingRehashesMiddleChangesWhenModificationTimeChanges() throws Exception {
        byte[] bytes = new byte[300000];
        Path source = Files.write(directory.resolve("main.jar"), bytes);
        Path installed = Files.createDirectories(directory.resolve("installed"));
        Path target = Files.write(installed.resolve("renamed.jar"), bytes);
        assertTrue(DlcFileMatch.matches(source, target, "hash"));
        String light = DlcFileMatch.lightHash(target);
        FileTime before = Files.getLastModifiedTime(target);
        bytes[150000] = 1; Files.write(target, bytes); Files.setLastModifiedTime(target, FileTime.fromMillis(before.toMillis() + 2000));
        assertEquals(light, DlcFileMatch.lightHash(target));
        assertFalse(DlcFileMatch.matches(source, target, "hash"));
        assertEquals(sha256(bytes), DlcFileMatch.hash(target));
        assertNull(DlcFileMatch.findByHash(DlcFileMatch.hash(source), installed));
        assertNull(DlcFileMatch.find(source, installed, "hash"));
    }

    @Test void fingerprintInvalidatesCacheWhenSizeAndTimeAreUnchanged() throws Exception {
        byte[] bytes = new byte[300000];
        Path file = Files.write(directory.resolve("main.jar"), bytes);
        String before = DlcFileMatch.hash(file);
        FileTime modified = Files.getLastModifiedTime(file);
        bytes[0] = 1; Files.write(file, bytes); Files.setLastModifiedTime(file, modified);
        assertNotEquals(before, DlcFileMatch.hash(file));
        assertEquals(sha256(bytes), DlcFileMatch.hash(file));
    }

    @Test void finalIntegrityCheckAlwaysReadsFullContentEvenWhenFingerprintAndTimeMatch() throws Exception {
        byte[] bytes = new byte[300000];
        Path file = Files.write(directory.resolve("main.jar"), bytes);
        String before = DlcFileMatch.hash(file), light = DlcFileMatch.lightHash(file);
        FileTime modified = Files.getLastModifiedTime(file);
        bytes[150000] = 1; Files.write(file, bytes); Files.setLastModifiedTime(file, modified);
        assertEquals(light, DlcFileMatch.lightHash(file));
        assertNotEquals(before, DlcFileMatch.fullHash(file));
        assertEquals(sha256(bytes), DlcFileMatch.fullHash(file));
    }

    @Test void scanAndDirectoryCachesObserveNestedFileAndStructureChanges() throws Exception {
        Path root = Files.createDirectories(directory.resolve("tree/nested"));
        Path child = Files.writeString(root.resolve("main.txt"), "before");
        Path tree = root.getParent();
        FileTime modified = Files.getLastModifiedTime(tree);
        DlcFileMatch.beginScan();
        try {
            String before = DlcFileMatch.hash(tree);
            Files.writeString(child, "after!");
            assertEquals(modified, Files.getLastModifiedTime(tree));
            assertNotEquals(before, DlcFileMatch.hash(tree));
            String changed = DlcFileMatch.hash(tree);
            Files.createDirectory(root.resolve("empty"));
            assertNotEquals(changed, DlcFileMatch.hash(tree));
            assertEquals(DlcFileMatch.fullHash(tree), DlcFileMatch.hash(tree));
        } finally { DlcFileMatch.endScan(); }
    }

    @Test void cachedEntriesCannotHideSymbolicLinksOrInterruptions() throws Exception {
        Path file = Files.writeString(directory.resolve("main.txt"), "resource");
        DlcFileMatch.hash(file);
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> DlcFileMatch.hash(file));
            assertThrows(IOException.class, () -> DlcFileMatch.fullHash(file));
        } finally { Thread.interrupted(); }
        Path other = Files.writeString(directory.resolve("other.txt"), "resource");
        Files.delete(file); Files.createSymbolicLink(file, other);
        assertThrows(IOException.class, () -> DlcFileMatch.hash(file));
        assertThrows(IOException.class, () -> DlcFileMatch.fullHash(file));
    }
}
