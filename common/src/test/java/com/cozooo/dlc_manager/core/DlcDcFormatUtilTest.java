package com.cozooo.dlc_manager.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcDcFormatUtilTest {
    @Test
    void recognizesAndConvertsDcPaths() {
        assertTrue(DlcDcFormatUtil.isDcName("example.dc"));
        assertTrue(DlcDcFormatUtil.isDcName("example.DC"));
        assertFalse(DlcDcFormatUtil.isDcName("example.json"));
        assertFalse(DlcDcFormatUtil.isDcName(null));

        assertEquals("example", DlcDcFormatUtil.stripConfigExtension("example.dc"));
        assertEquals("example", DlcDcFormatUtil.stripConfigExtension("example"));
        assertEquals("", DlcDcFormatUtil.stripConfigExtension(null));
        assertEquals(Path.of("folder", "example.json.dc"), DlcDcFormatUtil.toDcPath(Path.of("folder", "example.json")));
        assertEquals(Path.of("folder", "version.dc"), DlcDcFormatUtil.resolveConfigPath(Path.of("folder"), "version"));
        assertNull(DlcDcFormatUtil.resolveConfigPath(null, "version"));
    }

    @Test
    void readGenericTextParsesScalarsNestedSectionsAndDottedSections() {
        JsonObject object = DlcDcFormatUtil.readGenericText("""
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

        String written = DlcDcFormatUtil.writeGeneric(object);

        assertTrue(written.contains("enable = ["));
        assertTrue(written.contains("\"mods/a.jar\""));
        assertTrue(written.contains("conflicts = ["));
    }

    @Test
    void readGenericTextSupportsMultilineStringsAndComments() {
        JsonObject object = DlcDcFormatUtil.readGenericText("""
            title = "Visible" # ignored comment
            body = \"\"\"
            line1
            line2\"\"\"
            """);

        assertNotNull(object);
        assertEquals("Visible", object.get("title").getAsString());
        assertEquals("\nline1\nline2", object.get("body").getAsString());
    }

    @Test
    void structuredRelationsMoveOutOfBasicAndSupportBranches() throws Exception {
        String source = """
            [download]
            directUrl = "https://example.com/mod.jar"

            [basic]
            identifier = "MainMod"
            file_name = "main.jar"
            dependencies = ["legacy_dep"]
            conflicts = "legacy_conflict"

            [dependencies]
            "identifier_dep"
            "mods/path-dep.jar"

            [dependencies.mod1]
              [download]
              directUrl = "https://example.com/mod1.jar"

              [basic]
              identifier = "mod1"
              file_name = "mod1.jar"

            [conflicts]
            "identifier_conflict"
            "mods/path-conflict.jar"
            """;
        Path path = Files.createTempFile("dc-format-util-test", ".dc");
        Files.writeString(path, source);

        JsonObject structured = DlcDcFormatUtil.read(path);

        assertNotNull(structured);
        JsonObject dependencies = structured.getAsJsonObject("dependencies");
        assertNotNull(dependencies);
        assertEquals("legacy_dep", dependencies.get("legacy_dep").getAsString());
        assertEquals("identifier_dep", dependencies.get("identifier_dep").getAsString());
        assertEquals("mods/path-dep.jar", dependencies.get("mods/path-dep.jar").getAsString());
        assertEquals("mod1", dependencies.getAsJsonObject("mod1").get("identifier").getAsString());
        JsonObject conflicts = structured.getAsJsonObject("conflicts");
        assertEquals("legacy_conflict", conflicts.get("legacy_conflict").getAsString());
        assertEquals("identifier_conflict", conflicts.get("identifier_conflict").getAsString());

        String written = DlcDcFormatUtil.write(structured);
        assertFalse(written.contains("dependencies ="));
        assertFalse(written.contains("conflicts ="));
        assertTrue(written.contains("[dependencies]"));
        assertTrue(written.contains("\"identifier_dep\""));
        assertTrue(written.contains("[dependencies.mod1]"));
        assertTrue(written.contains("[conflicts]"));
    }
}
