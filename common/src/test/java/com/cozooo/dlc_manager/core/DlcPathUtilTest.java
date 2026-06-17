package com.cozooo.dlc_manager.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPathUtilTest {
    @TempDir
    Path tempDir;

    @Test
    void normalizesRelativePatterns() {
        assertEquals("folder/*/file", DlcPathUtil.normalizePattern("\\folder\\*\\file/"));
        assertNull(DlcPathUtil.normalizePattern("../folder/*"));
        assertNull(DlcPathUtil.normalizePattern("folder//file"));
        assertNull(DlcPathUtil.normalizePattern("folder/../file"));
    }

    @Test
    void wildcardMatchesOnePathSegmentOnly() {
        assertTrue(DlcPathUtil.matches("folder/*", "folder/a"));
        assertTrue(DlcPathUtil.matches("folder/*/file", "folder/a/file"));
        assertFalse(DlcPathUtil.matches("folder/*", "folder/a/file"));
        assertFalse(DlcPathUtil.matches("folder/*/file", "folder/a/b/file"));
        assertFalse(DlcPathUtil.matches("folder/*/file", "folder/a/other"));
    }

    @Test
    void exactPatternStillMatchesExactly() {
        assertFalse(DlcPathUtil.hasWildcard("folder/file"));
        assertFalse(DlcPathUtil.hasWildcard("folder/file*.jar"));
        assertTrue(DlcPathUtil.hasWildcard("folder/*"));
        assertTrue(DlcPathUtil.matches("folder/file", "folder/file"));
        assertFalse(DlcPathUtil.matches("folder/file", "folder/other"));
    }

    @Test
    void findsFileSystemMatchesBySingleSegments() throws Exception {
        Files.createDirectories(tempDir.resolve("folder/a"));
        Files.createDirectories(tempDir.resolve("folder/b"));
        Files.createDirectories(tempDir.resolve("folder/a/nested"));
        Files.writeString(tempDir.resolve("folder/a/file"), "a");
        Files.writeString(tempDir.resolve("folder/b/file"), "b");
        Files.writeString(tempDir.resolve("folder/a/nested/file"), "nested");

        List<String> direct = DlcPathUtil.findMatches(tempDir, "folder/*").stream()
            .map(path -> tempDir.relativize(path).toString().replace('\\', '/'))
            .toList();
        List<String> nested = DlcPathUtil.findMatches(tempDir, "folder/*/file").stream()
            .map(path -> tempDir.relativize(path).toString().replace('\\', '/'))
            .toList();

        assertEquals(List.of("folder/a", "folder/b"), direct);
        assertEquals(List.of("folder/a/file", "folder/b/file"), nested);
    }
}
