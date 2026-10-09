package com.cozooo.dclanguage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DcCompatibilityTest {
    @ParameterizedTest(name = "{0} retains all values and original formatting")
    @ValueSource(strings = {"remote", "relations", "history", "state", "update", "notice"})
    void readsExistingSyntaxAndRoundTripsIt(String name) throws Exception {
        String text = fixture(name + ".dc");
        JsonObject expected = JsonParser.parseString(fixture(name + ".json")).getAsJsonObject();
        DcDocument document = DcDocuments.readText(text);

        assertEquals(expected, document.toJson());
        assertEquals(text, DcDocuments.writeText(document));
        assertEquals(expected, DcDocuments.readText(
            DcDocuments.writeText(document, DcWriteOptions.NORMALIZE)).toJson());
    }

    @Test
    void changesOneKnownFieldWithoutRemovingUnknownValuesOrFormatting() throws Exception {
        String text = fixture("remote.dc");
        DcDocument document = DcDocuments.readText(text);
        JsonObject vendor = document.category("basic").getAsJsonObject("vendor").deepCopy();

        document.set("basic", "mode", "name");
        String written = DcDocuments.writeText(document);

        assertEquals(text.replace("mode = \"hash\"", "mode = \"name\""), written);
        DcDocument reread = DcDocuments.readText(written);
        assertEquals(vendor, reread.category("basic").getAsJsonObject("vendor"));
        assertTrue(reread.get("basic", "optional").isJsonNull());
        assertEquals("主要内容", reread.getString("DisplayName", "zh_cn"));
    }

    @Test
    void mutableHistoryRootPreservesResourceIdentityAndUnchangedTargets() throws Exception {
        String text = fixture("history.dc");
        DcDocument document = DcDocuments.readText(text);
        JsonObject snapshot = document.toJson();
        JsonObject backup = document.root().getAsJsonObject("backups")
            .getAsJsonObject("original.1791500000000");

        backup.addProperty("old", true);
        backup.addProperty("path", "tmp/required/required.example.1791500000000.old");
        String written = DcDocuments.writeText(document);
        DcDocument reread = DcDocuments.readText(written);

        assertEquals(text.replace("old = false", "old = true")
            .replace("path = \"tmp/required/required.example.1791500000000\"",
                "path = \"tmp/required/required.example.1791500000000.old\""), written);
        assertEquals(snapshot.get("resources"), reread.root().get("resources"));
        assertFalse(snapshot.getAsJsonObject("backups")
            .getAsJsonObject("original.1791500000000").get("old").getAsBoolean());
        assertTrue(reread.root().getAsJsonObject("backups")
            .getAsJsonObject("original.1791500000000").get("old").getAsBoolean());
    }

    @Test
    void editingNestedDependencyDoesNotMoveSiblingRelations() throws Exception {
        String text = fixture("relations.dc");
        DcDocument document = DcDocuments.readText(text);

        document.category("dependencies", "extra", "dependencies", "library.with.dots", "tip")
            .addProperty("en_us", "Updated nested dependency");
        String written = DcDocuments.writeText(document);

        assertEquals(text.replace("Nested dependency", "Updated nested dependency"), written);
        DcDocument reread = DcDocuments.readText(written);
        assertEquals(document.toJson(), reread.toJson());
        assertNotNull(reread.category("conflicts", "mods/old.jar"));
        assertNotNull(reread.category("dependencies", "mods/path-dep.jar"));
    }

    @Test
    void noticeTitleEditKeepsMultilineHtmlAndLiteralTextUntouched() throws Exception {
        String text = fixture("notice.dc");
        DcDocument document = DcDocuments.readText(text);

        document.set("Title", "en_us", "Information");
        String written = DcDocuments.writeText(document);

        assertEquals(text.replace("Release notes", "Information"), written);
        assertEquals(document.toJson(), DcDocuments.readText(written).toJson());
    }

    private static String fixture(String name) throws IOException {
        try (InputStream input = DcCompatibilityTest.class.getResourceAsStream("/compatibility/" + name)) {
            if (input == null) throw new IOException("Missing compatibility fixture: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
