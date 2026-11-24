package com.cozooo.dlc_manager.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DlcCollectionSourceTest {
    @TempDir
    Path tempDir;

    @Test
    void stripsOnlyAnOuterDirectoryContainingMultipleGameRoots() throws Exception {
        Path dlcRoot = tempDir.resolve("DLC");
        Path source = dlcRoot.resolve("collections/bundle");
        Files.createDirectories(source.resolve("config"));
        Files.createDirectories(source.resolve("kubejs/server_scripts"));
        Files.writeString(source.resolve("config/example.toml"), "config");
        Files.writeString(source.resolve("kubejs/server_scripts/example.js"), "script");

        var entries = DlcCollectionSource.entries(source, dlcRoot, tempDir.resolve("game"), false);

        assertEquals(Set.of("config/example.toml", "kubejs/server_scripts/example.js"), relativePaths(entries));
    }

    @Test
    void keepsAStandaloneConfigDirectoryAsTheGameRelativeConfigDirectory() throws Exception {
        Path dlcRoot = tempDir.resolve("DLC");
        Path source = dlcRoot.resolve("collections/config-only");
        Files.createDirectories(source.resolve("config"));
        Files.writeString(source.resolve("config/example.toml"), "config");

        var entries = DlcCollectionSource.entries(source, dlcRoot, tempDir.resolve("game"), false);

        assertEquals(Set.of("config/example.toml"), relativePaths(entries));
    }

    @Test
    void appliesTheSameOuterDirectoryRuleToArchives() throws Exception {
        Path dlcRoot = tempDir.resolve("DLC");
        Path archive = dlcRoot.resolve("collections/bundle.zip");
        Files.createDirectories(archive.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            write(output, "Bundle/config/example.toml", "config");
            write(output, "Bundle/mods/example.jar", "mod");
        }

        var entries = DlcCollectionSource.entries(archive, dlcRoot, tempDir.resolve("game"), false);

        assertEquals(Set.of("config/example.toml", "mods/example.jar"), relativePaths(entries));
    }

    private static Set<String> relativePaths(java.util.List<com.cozooo.dlc_manager.client.pack.DlcPackInstallPlan.FileEntry> entries) {
        Set<String> paths = new HashSet<>();
        for (var entry : entries) paths.add(entry.relativePath);
        return paths;
    }

    private static void write(ZipOutputStream output, String name, String content) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
