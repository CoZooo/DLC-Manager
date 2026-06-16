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

    private record Entry(String name, String content) {
    }
}
