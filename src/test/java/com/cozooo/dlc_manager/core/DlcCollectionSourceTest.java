package com.cozooo.dlc_manager.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.google.gson.JsonObject;
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

    @Test
    void stripsASingleWrappedDirectoryThatHoldsOneGameRoot() throws Exception {
        Path dlcRoot = tempDir.resolve("DLC");
        Path archive = dlcRoot.resolve("collections/renyingDLC.zip");
        Files.createDirectories(archive.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            write(output, "renyingDLC/mods/example.jar", "mod");
        }

        var entries = DlcCollectionSource.entries(archive, dlcRoot, tempDir.resolve("game"), false);

        assertEquals(Set.of("mods/example.jar"), relativePaths(entries));
    }

    @Test
    void ignoresFinderMetadataWhenStrippingTheWrappedDirectory() throws Exception {
        Path dlcRoot = tempDir.resolve("DLC");
        Path archive = dlcRoot.resolve("collections/renyingDLC.zip");
        Files.createDirectories(archive.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            write(output, "renyingDLC/mods/example.jar", "mod");
            write(output, "__MACOSX/renyingDLC/mods/._example.jar", "junk");
        }

        var entries = DlcCollectionSource.entries(archive, dlcRoot, tempDir.resolve("game"), false);

        assertEquals(Set.of("mods/example.jar"), relativePaths(entries));
    }

    @Test
    void readsStandaloneModrinthMetadataAsACollectionSource() throws Exception {
        Path dlcRoot = tempDir.resolve("DLC");
        Path metadata = dlcRoot.resolve("collections/modrinth.index.json");
        Files.createDirectories(metadata.getParent());
        Files.writeString(metadata, """
            {
              "formatVersion": 1,
              "game": "minecraft",
              "files": [
                {
                  "path": "mods/example.jar",
                  "hashes": {"sha1": "0123456789012345678901234567890123456789", "sha512": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"},
                  "env": {"client": "required", "server": "required"},
                  "downloads": ["https://example.com/example.jar"],
                  "fileSize": 1
                }
              ]
            }
            """);

        var entries = DlcCollectionSource.completionEntries(metadata, dlcRoot, "collection:test");

        assertEquals(1, entries.size());
        assertEquals("mods/example.jar", entries.get(0).getAppliedTargetRaw());
        assertEquals("example.jar", entries.get(0).getRequiredFileName());
    }

    @Test
    void acceptsStandaloneCurseForgeManifestAsACollectionSource() throws Exception {
        Path dlcRoot = tempDir.resolve("DLC");
        Path metadata = dlcRoot.resolve("collections/manifest.json");
        Files.createDirectories(metadata.getParent());
        Files.writeString(metadata, "{\"manifestType\":\"minecraftModpack\",\"manifestVersion\":1,\"minecraft\":{},\"files\":[]}");
        Files.writeString(metadata.getParent().resolve("unrelated.txt"), "must not install");

        assertEquals(metadata, DlcCollectionSource.materialize(metadata, dlcRoot));
        assertEquals(List.of(), DlcCollectionSource.completionEntries(metadata, dlcRoot, "collection:test"));
        assertEquals(List.of(), DlcCollectionSource.entries(metadata, dlcRoot, tempDir.resolve("game"), false));
    }

    @Test
    void addsInlineRemoteEntriesToCollectionCompletion() throws Exception {
        Path dlcRoot = tempDir.resolve("DLC");
        Path source = dlcRoot.resolve("collections/inline");
        Files.createDirectories(source);
        JsonObject remote = new JsonObject();
        remote.addProperty("identifier", "inline");
        remote.addProperty("source", "inline.jar");
        remote.addProperty("directUrl", "https://example.com/inline.jar");
        remote.addProperty("target", "mods/");

        var entries = DlcCollectionSource.completionEntries(source, dlcRoot, "collection:test", List.of(remote));

        assertEquals(1, entries.size());
        assertEquals("collection:test:remote:inline", entries.get(0).getIdentifier());
        assertEquals("mods/", entries.get(0).getAppliedTargetRaw());
    }

    @Test
    void appliesCachedInlineRemoteFilesToDirectoryTargets() throws Exception {
        Path dlcRoot = tempDir.resolve("DLC");
        Path source = dlcRoot.resolve("collections/inline");
        Path cached = dlcRoot.resolve("downloaded/required/inline.jar");
        Files.createDirectories(source);
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, "jar");
        JsonObject remote = new JsonObject();
        remote.addProperty("identifier", "inline");
        remote.addProperty("source", "inline.jar");
        remote.addProperty("directUrl", "https://example.com/inline.jar");
        remote.addProperty("target", "mods/");

        var entries = DlcCollectionSource.entries(source, dlcRoot, tempDir.resolve("game"), true, "collection:test", List.of(remote));

        assertEquals(Set.of("mods/inline.jar"), relativePaths(entries));
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
