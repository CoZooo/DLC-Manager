package com.cozooo.dlc_manager.client.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPackPackageTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsStandardPackageAtRootMetadata() throws Exception {
        Path root = tempDir.resolve("standard");
        writePack(root.resolve("metadata").resolve("pack.dc"), "root_pack");
        Files.createDirectories(root.resolve("mods"));
        Files.writeString(root.resolve("mods").resolve("a.jar"), "jar");

        DlcPackPackage.Result result = DlcPackPackage.inspect(root);

        assertTrue(result.valid);
        assertNotNull(result.pack);
        assertEquals(DlcPackPackage.Format.STANDARD, result.pack.format);
        assertEquals(root.toAbsolutePath().normalize(), result.pack.contentRoot);
        assertEquals(root.toAbsolutePath().normalize(), result.pack.installRoot);
        assertEquals("root_pack", result.pack.metadata.identifier);
        assertTrue(result.pack.packFile.endsWith("metadata/pack.dc"));
    }

    @Test
    void detectsStandardPackageInsideOneShellDirectoryAndIgnoresOuterFiles() throws Exception {
        Path root = tempDir.resolve("wrapped");
        Path shell = root.resolve("any-name");
        Files.createDirectories(root);
        Files.writeString(root.resolve("ignored.txt"), "ignored");
        writePack(shell.resolve("dlcpack_metadata").resolve("pack.dc"), "shell_pack");

        DlcPackPackage.Result result = DlcPackPackage.inspect(root);

        assertTrue(result.valid);
        assertNotNull(result.pack);
        assertEquals(shell.toAbsolutePath().normalize(), result.pack.contentRoot);
        assertEquals(shell.toAbsolutePath().normalize(), result.pack.installRoot);
        assertEquals("shell_pack", result.pack.metadata.identifier);
        assertTrue(result.pack.packFile.endsWith("dlcpack_metadata/pack.dc"));
    }

    @Test
    void detectsCurseForgePackageAndPrefersOverridesMetadata() throws Exception {
        Path root = tempDir.resolve("cf");
        Files.createDirectories(root.resolve("overrides"));
        Files.writeString(root.resolve("manifest.json"), """
            {
              "files": [
                { "projectID": 123, "fileID": 456, "required": false }
              ]
            }
            """);
        writePack(root.resolve("metadata").resolve("pack.dc"), "outer_pack");
        writePack(root.resolve("overrides").resolve("metadata").resolve("pack.dc"), "overrides_pack");

        DlcPackPackage.Result result = DlcPackPackage.inspect(root);

        assertTrue(result.valid);
        assertNotNull(result.pack);
        assertEquals(DlcPackPackage.Format.CURSEFORGE, result.pack.format);
        assertEquals(root.toAbsolutePath().normalize(), result.pack.contentRoot);
        assertEquals(root.resolve("overrides").toAbsolutePath().normalize(), result.pack.installRoot);
        assertEquals("overrides_pack", result.pack.metadata.identifier);
        assertEquals(1, result.pack.manifestFiles.size());
        assertEquals("123", result.pack.manifestFiles.get(0).projectId);
        assertEquals("456", result.pack.manifestFiles.get(0).fileId);
        assertTrue(result.pack.packFile.endsWith("overrides/metadata/pack.dc"));
    }

    @Test
    void curseForgeFallsBackToOuterDlcpackMetadataWhenOverridesMetadataMissing() throws Exception {
        Path root = tempDir.resolve("cf-fallback");
        Files.createDirectories(root.resolve("overrides"));
        Files.writeString(root.resolve("manifest.json"), "{}");
        writePack(root.resolve("dlcpack_metadata").resolve("pack.dc"), "fallback_pack");

        DlcPackPackage.Result result = DlcPackPackage.inspect(root);

        assertTrue(result.valid);
        assertNotNull(result.pack);
        assertEquals(DlcPackPackage.Format.CURSEFORGE, result.pack.format);
        assertEquals("fallback_pack", result.pack.metadata.identifier);
        assertTrue(result.pack.packFile.endsWith("dlcpack_metadata/pack.dc"));
    }

    private static void writePack(Path path, String identifier) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
            [basic]
            identifier = "%s"
            version = "1.0.0"
            """.formatted(identifier));
    }
}
