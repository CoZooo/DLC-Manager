package com.cozooo.dlc_manager.client.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcZipUtilTest {
    @TempDir
    Path tempDir;

    @Test
    void createUniqueDirUsesSanitizedPrefixAndCreatesDifferentDirectories() throws Exception {
        Path base = tempDir.resolve("staging");

        Path first = DlcZipUtil.createUniqueDir(base, "A:B");
        Path second = DlcZipUtil.createUniqueDir(base, "A:B");

        assertTrue(first.startsWith(base.toAbsolutePath().normalize()));
        assertTrue(second.startsWith(base.toAbsolutePath().normalize()));
        assertNotEquals(first, second);
        assertTrue(first.getFileName().toString().startsWith("A_B-"));
        assertTrue(second.getFileName().toString().startsWith("A_B-"));
    }

    @Test
    void extractZipSkipsEscapingAndIgnoredSystemEntries() throws Exception {
        Path zip = tempDir.resolve("pack.zip");
        writeZip(
            zip,
            new Entry("mods/a.jar", "jar"),
            new Entry("../evil.txt", "evil"),
            new Entry("__MACOSX/ignored", "ignored"),
            new Entry("._ignored", "ignored"),
            new Entry(".DS_Store", "ignored"),
            new Entry("pack/.DS_Store", "ignored")
        );
        Path target = tempDir.resolve("out");

        DlcZipUtil.extractZip(zip, target);

        assertTrue(Files.exists(target.resolve("mods").resolve("a.jar")));
        assertTrue(!Files.exists(tempDir.resolve("evil.txt")));
        assertTrue(!Files.exists(target.resolve("__MACOSX").resolve("ignored")));
        assertTrue(!Files.exists(target.resolve("._ignored")));
        assertTrue(!Files.exists(target.resolve(".DS_Store")));
        assertTrue(!Files.exists(target.resolve("pack").resolve(".DS_Store")));
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
