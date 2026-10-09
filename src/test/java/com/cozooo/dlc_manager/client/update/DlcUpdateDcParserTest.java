package com.cozooo.dlc_manager.client.update;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcUpdateDcParserTest {
    @Test
    void parsesUpdateDcSectionsAndDeleteArray() {
        JsonObject object = DlcUpdateDcParser.parse("""
            [update]
            base_old = "1.0.0"
            base_new = "1.1.0"

            [del]
            "mods/old.jar"
            "config/old.toml"
            """);

        assertNotNull(object);
        assertEquals("1.0.0", object.getAsJsonObject("update").get("base_old").getAsString());
        assertEquals("1.1.0", object.getAsJsonObject("update").get("base_new").getAsString());
        assertEquals("mods/old.jar", object.getAsJsonArray("del").get(0).getAsString());
        assertEquals("config/old.toml", object.getAsJsonArray("del").get(1).getAsString());
    }

    @Test
    void parsesNestedObjectsMultilineStringsAndJsonArrays() {
        JsonObject object = DlcUpdateDcParser.parse("""
            [preset]
            enable = [
              "mods/a.jar",
              "config/a.toml"
            ]
            changelog:
              zh_cn = \"\"\"
            第一行
            第二行\"\"\"
            hidden = false
            """);

        assertNotNull(object);
        JsonObject preset = object.getAsJsonObject("preset");
        assertEquals("mods/a.jar", preset.getAsJsonArray("enable").get(0).getAsString());
        assertEquals("\n第一行\n第二行", preset.getAsJsonObject("changelog").get("zh_cn").getAsString());
        assertTrue(!preset.get("hidden").getAsBoolean());
    }
    @Test
    void nestedDeleteListsAndTrailingCommasRemainSupported() {
        JsonObject object = DlcUpdateDcParser.parse("""
            [update]
              [del]
              "mods/old.jar",
              "config/old.toml",
            [extra]
            preserved = {"vendor": 42}
            """);
        assertNotNull(object);
        assertEquals(2, object.getAsJsonObject("update").getAsJsonArray("del").size());
        assertEquals("mods/old.jar", object.getAsJsonObject("update").getAsJsonArray("del").get(0).getAsString());
        assertEquals(42, object.getAsJsonObject("extra").getAsJsonObject("preserved").get("vendor").getAsInt());
    }

    @Test
    void malformedUpdatesAreRejectedWithoutPartialParsing() {
        org.junit.jupiter.api.Assertions.assertNull(DlcUpdateDcParser.parse("[update]\nname = \"unterminated\n"));
    }

}
