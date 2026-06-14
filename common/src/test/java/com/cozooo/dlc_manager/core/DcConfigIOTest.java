package com.cozooo.dlc_manager.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DcConfigIOTest {
    @TempDir
    Path tempDir;

    @Test
    void writeAppendsDcExtensionAndRemovesOriginal() throws Exception {
        Path original = tempDir.resolve("entry.json");
        Files.writeString(original, "{}");
        JsonObject object = new JsonObject();
        object.addProperty("name", "Entry");

        assertTrue(DcConfigIO.write(original, object, DcConfigIO.Layout.GENERIC));

        Path target = tempDir.resolve("entry.json.dc");
        assertTrue(Files.exists(target));
        assertFalse(Files.exists(original));
        JsonObject read = DcConfigIO.read(target, DcConfigIO.Layout.GENERIC);
        assertNotNull(read);
        assertEquals("Entry", read.get("name").getAsString());
    }

    @Test
    void structuredInfoWriteStripsDownloadKeys() {
        Path target = tempDir.resolve("info").resolve("entry.dc");
        JsonObject object = new JsonObject();
        object.addProperty("name", "Entry");
        object.addProperty("priority", "modrinth");
        object.addProperty("directUrl", "https://example.com/file.jar");

        assertTrue(DcConfigIO.write(target, object, DcConfigIO.Layout.DLC_STRUCTURED));

        JsonObject read = DcConfigIO.read(target, DcConfigIO.Layout.DLC_STRUCTURED);
        assertNotNull(read);
        assertEquals("Entry", read.get("name").getAsString());
        assertFalse(read.has("priority"));
        assertFalse(read.has("directUrl"));
    }

    @Test
    void readRejectsNonDcFiles() throws Exception {
        Path json = tempDir.resolve("entry.json");
        Files.writeString(json, "name = \"Entry\"");

        assertFalse(DcConfigIO.isDc(json));
        assertEquals(null, DcConfigIO.read(json, DcConfigIO.Layout.GENERIC));
    }
}
