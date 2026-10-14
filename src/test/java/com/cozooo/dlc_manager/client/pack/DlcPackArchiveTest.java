package com.cozooo.dlc_manager.client.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DlcPackArchiveTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsStandardPackZipToIdentifierStagingAndCanInspectIt() throws Exception {
        Path zip = tempDir.resolve("pack.zip");
        writeZip(
            zip,
            new Entry("shell/metadata/pack.dc", """
                [basic]
                identifier = "Example-Pack"
                version = "1.0.0"
                """),
            new Entry("shell/mods/a.jar", "jar")
        );
        Path dlcRoot = tempDir.resolve("DLC");

        DlcPackArchive.ExtractResult extracted = DlcPackArchive.extractToStaging(zip, dlcRoot);
        DlcPackPackage.Result inspected = DlcPackPackage.inspect(extracted.stagingDir);

        assertTrue(extracted.stagingDir.getFileName().toString().startsWith("Example-Pack-"));
        assertTrue(inspected.valid);
        assertNotNull(inspected.pack);
        assertEquals("Example-Pack", inspected.pack.metadata.identifier);
        assertTrue(Files.exists(inspected.pack.installRoot.resolve("mods").resolve("a.jar")));

        DlcPackArchive.deleteRecursive(extracted.stagingDir);
        assertTrue(!Files.exists(extracted.stagingDir));
    }

    private static void writeZip(Path zip, Entry... entries) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Entry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.name);
                out.putNextEntry(zipEntry);
                out.write(entry.content.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    @Test
    void previewPreservesCurseForgeAndWrappedMetadataSelection() throws Exception {
        for (String shell : new String[] {"", "shell/"}) {
            for (boolean overridesMetadata : new boolean[] {false, true}) {
                Path zip = tempDir.resolve("cf-" + shell.length() + "-" + overridesMetadata + ".zip");
                String metadataDir = shell + (overridesMetadata ? "overrides/metadata/" : "dlcpack_metadata/");
                writeZip(zip,
                    new Entry(shell + "manifest.json", "{\"files\":[{\"projectID\":123,\"fileID\":456}]}"),
                    new Entry(shell + "overrides/", ""),
                    new Entry(metadataDir + "pack.dc", "[basic]\nidentifier = \"cf\"\nversion = \"1.0.0\"\n"),
                    new Entry(metadataDir + "icon.png", "icon"),
                    new Entry(shell + "overrides/mods/a.jar", "payload"));
                Path dlc = tempDir.resolve("DLC");
                var full = DlcPackPackage.inspect(DlcPackArchive.extractToStaging(zip, dlc).stagingDir);
                var extractedPreview = DlcPackArchive.extractPreviewToStaging(zip, dlc);
                var preview = DlcPackPackage.inspectPreview(extractedPreview);
                assertTrue(full.valid);
                assertTrue(preview.valid);
                assertEquals(full.pack.format, preview.pack.format);
                assertEquals(full.pack.metadata.identifier, preview.pack.metadata.identifier);
                assertTrue(preview.pack.manifestFiles.isEmpty());
                assertFalse(Files.exists(preview.pack.manifestFile));
                assertEquals("icon", Files.readString(preview.pack.iconFile));
                assertFalse(Files.exists(preview.pack.installRoot.resolve("mods/a.jar")));
            }
        }
    }

    @Test
    void previewDoesNotInflatePackContents() throws Exception {
        Path zip = tempDir.resolve("large.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("shell/metadata/pack.dc"));
            out.write("[basic]\nidentifier = \"large\"\nversion = \"1.0.0\"\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("shell/mods/large.jar"));
            byte[] block = new byte[1024 * 1024];
            for (int i = 0; i < 64; i++) out.write(block);
            out.closeEntry();
        }
        long started = System.nanoTime();
        Path preview = DlcPackArchive.extractPreviewToStaging(zip, tempDir.resolve("DLC")).stagingDir;
        long bytes;
        try (var files = Files.walk(preview)) {
            bytes = files.filter(Files::isRegularFile).mapToLong(path -> path.toFile().length()).sum();
        }
        System.out.printf("Pack preview: %,d bytes written in %d ms%n", bytes, (System.nanoTime() - started) / 1_000_000);
        assertTrue(DlcPackPackage.inspect(preview).valid);
        assertFalse(Files.exists(preview.resolve("shell/mods/large.jar")), "Opening the Pack page must not extract its payload");
        assertTrue(bytes < 1024 * 1024, "Preview work must depend on metadata size, not Pack size");
    }

    @Test
    void previewUsesManifestPresenceWithoutReadingItsBody() throws Exception {
        Path zip = tempDir.resolve("large-manifest.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("shell/manifest.json"));
            byte[] block = new byte[1024 * 1024];
            for (int i = 0; i < 64; i++) out.write(block);
            out.closeEntry();
            for (String location : new String[] {"shell/metadata/pack.dc", "shell/overrides/metadata/pack.dc"}) {
                out.putNextEntry(new ZipEntry(location));
                String identifier = location.contains("overrides") ? "preferred" : "outer";
                out.write(("[basic]\nidentifier = \"" + identifier + "\"\nversion = \"1.0.0\"\n").getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        var extracted = DlcPackArchive.extractPreviewToStaging(zip, tempDir.resolve("DLC"));
        var preview = DlcPackPackage.inspectPreview(extracted);
        assertTrue(preview.valid);
        assertEquals(DlcPackPackage.Format.CURSEFORGE, preview.pack.format);
        assertEquals("preferred", preview.pack.metadata.identifier);
        assertFalse(Files.exists(preview.pack.manifestFile));
        assertTrue(preview.pack.manifestFiles.isEmpty());
    }

    private record Entry(String name, String content) {
    }
}
