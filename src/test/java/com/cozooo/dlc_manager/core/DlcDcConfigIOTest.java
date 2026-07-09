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

class DlcDcConfigIOTest {
    @TempDir
    Path tempDir;

    @Test
    void writeAppendsDcExtensionAndRemovesOriginal() throws Exception {
        Path original = tempDir.resolve("entry.json");
        Files.writeString(original, "{}");
        JsonObject object = new JsonObject();
        object.addProperty("name", "Entry");

        assertTrue(DlcDcConfigIO.write(original, object, DlcDcConfigIO.Layout.GENERIC));

        Path target = tempDir.resolve("entry.json.dc");
        assertTrue(Files.exists(target));
        assertFalse(Files.exists(original));
        JsonObject read = DlcDcConfigIO.read(target, DlcDcConfigIO.Layout.GENERIC);
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

        assertTrue(DlcDcConfigIO.write(target, object, DlcDcConfigIO.Layout.DLC_STRUCTURED));

        JsonObject read = DlcDcConfigIO.read(target, DlcDcConfigIO.Layout.DLC_STRUCTURED);
        assertNotNull(read);
        assertEquals("Entry", read.get("name").getAsString());
        assertFalse(read.has("priority"));
        assertFalse(read.has("directUrl"));
    }

    @Test
    void readRejectsNonDcFiles() throws Exception {
        Path json = tempDir.resolve("entry.json");
        Files.writeString(json, "name = \"Entry\"");

        assertFalse(DlcDcConfigIO.isDc(json));
        assertEquals(null, DlcDcConfigIO.read(json, DlcDcConfigIO.Layout.GENERIC));
    }
}
