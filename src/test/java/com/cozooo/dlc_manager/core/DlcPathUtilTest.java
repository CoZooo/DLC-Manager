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
        assertTrue(DlcPathUtil.matches("mods/*.jar", "mods/a.jar"));
        assertFalse(DlcPathUtil.matches("folder/*", "folder/a/file"));
        assertFalse(DlcPathUtil.matches("folder/*/file", "folder/a/b/file"));
        assertFalse(DlcPathUtil.matches("folder/*/file", "folder/a/other"));
        assertFalse(DlcPathUtil.matches("mods/*.jar", "mods/nested/a.jar"));
        assertFalse(DlcPathUtil.matches("mods/*.jar", "mods/a.zip"));
    }

    @Test
    void exactPatternStillMatchesExactly() {
        assertFalse(DlcPathUtil.hasWildcard("folder/file"));
        assertTrue(DlcPathUtil.hasWildcard("folder/file*.jar"));
        assertTrue(DlcPathUtil.hasWildcard("folder/*"));
        assertTrue(DlcPathUtil.matches("folder/file", "folder/file"));
        assertFalse(DlcPathUtil.matches("folder/file", "folder/other"));
    }

    @Test
    void fileNameWildcardMatchesWithinSingleFileName() {
        assertTrue(DlcPathUtil.hasFileNameWildcard("DLC-Manager-*.jar"));
        assertTrue(DlcPathUtil.matchesFileName("DLC-Manager-*.jar", "DLC-Manager-Fabric-1.20.1.jar"));
        assertTrue(DlcPathUtil.matchesFileName("*.zip", "pack.zip"));
        assertTrue(DlcPathUtil.hasFileNameWildcard("mods/*.jar"));
        assertTrue(DlcPathUtil.matchesFileName("mods/*.jar", "mods/a.jar"));
        assertFalse(DlcPathUtil.matchesFileName("*.zip", "pack.jar"));
        assertFalse(DlcPathUtil.matchesFileName("mods/*.jar", "mods/nested/a.jar"));
        assertFalse(DlcPathUtil.matchesFileName("*.jar", "mods/a.jar"));
    }

    @Test
    void findsFileSystemMatchesBySingleSegments() throws Exception {
        Files.createDirectories(tempDir.resolve("folder/a"));
        Files.createDirectories(tempDir.resolve("folder/b"));
        Files.createDirectories(tempDir.resolve("folder/a/nested"));
        Files.createDirectories(tempDir.resolve("mods/nested"));
        Files.writeString(tempDir.resolve("folder/a/file"), "a");
        Files.writeString(tempDir.resolve("folder/b/file"), "b");
        Files.writeString(tempDir.resolve("folder/a/nested/file"), "nested");
        Files.writeString(tempDir.resolve("mods/a.jar"), "jar");
        Files.writeString(tempDir.resolve("mods/a.zip"), "zip");
        Files.writeString(tempDir.resolve("mods/nested/a.jar"), "nested jar");

        List<String> direct = DlcPathUtil.findMatches(tempDir, "folder/*").stream()
            .map(path -> tempDir.relativize(path).toString().replace('\\', '/'))
            .toList();
        List<String> nested = DlcPathUtil.findMatches(tempDir, "folder/*/file").stream()
            .map(path -> tempDir.relativize(path).toString().replace('\\', '/'))
            .toList();
        List<String> jars = DlcPathUtil.findMatches(tempDir, "mods/*.jar").stream()
            .map(path -> tempDir.relativize(path).toString().replace('\\', '/'))
            .toList();

        assertEquals(List.of("folder/a", "folder/b"), direct);
        assertEquals(List.of("folder/a/file", "folder/b/file"), nested);
        assertEquals(List.of("mods/a.jar"), jars);
    }
}
