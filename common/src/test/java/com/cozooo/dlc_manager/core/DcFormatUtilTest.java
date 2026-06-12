package com.cozooo.dlc_manager.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DcFormatUtilTest {
    @Test
    void recognizesAndConvertsDcPaths() {
        assertTrue(DcFormatUtil.isDcName("example.dc"));
        assertTrue(DcFormatUtil.isDcName("example.DC"));
        assertFalse(DcFormatUtil.isDcName("example.json"));
        assertFalse(DcFormatUtil.isDcName(null));

        assertEquals("example", DcFormatUtil.stripConfigExtension("example.dc"));
        assertEquals("example", DcFormatUtil.stripConfigExtension("example"));
        assertEquals("", DcFormatUtil.stripConfigExtension(null));
        assertEquals(Path.of("folder", "example.json.dc"), DcFormatUtil.toDcPath(Path.of("folder", "example.json")));
        assertEquals(Path.of("folder", "version.dc"), DcFormatUtil.resolveConfigPath(Path.of("folder"), "version"));
        assertNull(DcFormatUtil.resolveConfigPath(null, "version"));
    }

    @Test
    void readGenericTextParsesScalarsNestedSectionsAndDottedSections() {
        JsonObject object = DcFormatUtil.readGenericText("""
            name = "Example"
            enabled = true
            count = 3

            [server]
            host = "127.0.0.1"

            [client.ui]
            scale = 2
            """);

        assertNotNull(object);
        assertEquals("Example", object.get("name").getAsString());
        assertTrue(object.get("enabled").getAsBoolean());
        assertEquals(3, object.get("count").getAsInt());
        assertEquals("127.0.0.1", object.getAsJsonObject("server").get("host").getAsString());
        assertEquals(2, object.getAsJsonObject("client").getAsJsonObject("ui").get("scale").getAsInt());
    }

    @Test
    void writeGenericFormatsVerticalPresetArrays() {
        JsonObject object = new JsonObject();
        JsonArray enable = new JsonArray();
        enable.add("mods/a.jar");
        enable.add("config/a.toml");
        object.add("enable", enable);
        JsonArray conflicts = new JsonArray();
        conflicts.add("mods/b.jar");
        object.add("conflicts", conflicts);

        String written = DcFormatUtil.writeGeneric(object);

        assertTrue(written.contains("enable = ["));
        assertTrue(written.contains("\"mods/a.jar\""));
        assertTrue(written.contains("conflicts = ["));
    }

    @Test
    void readGenericTextSupportsMultilineStringsAndComments() {
        JsonObject object = DcFormatUtil.readGenericText("""
            title = "Visible" # ignored comment
            body = \"\"\"
            line1
            line2\"\"\"
            """);

        assertNotNull(object);
        assertEquals("Visible", object.get("title").getAsString());
        assertEquals("\nline1\nline2", object.get("body").getAsString());
    }
}
