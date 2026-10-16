package com.cozooo.dlc_manager.bootstrap;

import com.cozooo.dlc_bootstrap.launch.DlcDistributionAssembler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcDistributionAssemblerTest {
    @TempDir Path directory;

    @Test void invalidModCannotReplacePreviouslyBuiltDistribution() throws Exception {
        Path service = Path.of(System.getProperty("forgeBootstrapJar"));
        Path invalidMod = directory.resolve("invalid.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(invalidMod))) {
            zip.putNextEntry(new ZipEntry("unrelated.txt"));
            zip.write(42);
            zip.closeEntry();
        }
        Path output = Files.writeString(directory.resolve("DLC-Manager.jar"), "previous distribution");
        assertThrows(IOException.class, () -> DlcDistributionAssembler.pack(service, invalidMod, output));
        assertEquals("previous distribution", Files.readString(output));
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(file -> file.toString().endsWith(".tmp")));
        }
    }
}
