package com.cozooo.dlc_manager.client.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcUpdatePackageTest {
    @TempDir
    Path tempDir;

    @Test
    void fromZipReadsJsonManifestAtRoot() throws Exception {
        Path zip = tempDir.resolve("Client_1.0.0_1.0.0-1.0.1_1.0.1.zip");
        writeZip(zip, new Entry("update.json", """
            {
              "version": {
                "base": "1.0.0",
                "dlc_old": "1.0.0",
                "dlc_new": "1.0.1"
              }
            }
            """));

        DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(zip);

        assertNotNull(pkg);
        assertEquals(zip, pkg.getArchivePath());
        assertEquals("Client_1.0.0_1.0.0-1.0.1_1.0.1", pkg.getName());
        assertEquals("1.0.1", pkg.getManifest().getVersion().getDlcNew());
    }

    @Test
    void fromZipFindsNestedDcManifestAndExtractsContentRoot() throws Exception {
        Path zip = tempDir.resolve("nested.zip");
        writeZip(
            zip,
            new Entry("pack/update.dc", """
                [version]
                base = "1.0.0"
                dlc_old = "1.0.0"
                dlc_new = "1.0.1"
                """),
            new Entry("pack/mods/new.jar", "jar"),
            new Entry("../evil.txt", "evil"),
            new Entry("__MACOSX/ignored", "ignored"),
            new Entry("pack/.DS_Store", "ignored")
        );

        DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(zip);
        Path staging = pkg.extractTo(tempDir.resolve("DLC"));

        assertNotNull(staging);
        assertEquals("1.0.1", pkg.getManifest().getVersion().getDlcNew());
        assertTrue(staging.endsWith("pack"));
        assertTrue(Files.exists(staging.resolve("mods").resolve("new.jar")));
        assertTrue(!Files.exists(staging.resolve("..").resolve("evil.txt").normalize()));

        pkg.cleanupStaging();
        assertNull(pkg.getStagingRoot());
        assertTrue(!Files.exists(staging.getParent()));
        assertTrue(!Files.exists(staging));
    }

    @Test
    void fromZipReturnsNullWhenManifestMissing() throws Exception {
        Path zip = tempDir.resolve("missing.zip");
        writeZip(zip, new Entry("mods/file.jar", "jar"));

        assertNull(DlcUpdatePackage.fromZip(zip));
        assertNull(DlcUpdatePackage.fromZip(null));
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
