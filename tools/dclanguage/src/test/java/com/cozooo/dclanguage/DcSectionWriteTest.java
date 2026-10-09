package com.cozooo.dclanguage;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DcSectionWriteTest {
    @Test
    void movesLegacyValuesIntoRequestedSectionsAndRetainsUnknownText() throws Exception {
        String source = "# before\r\nidentifier = 'main' # identifier\r\nenable = [\r\n  'first', # first\r\n  'second'\r\n]\r\n"
            + "future = {\"setting\": 2}\r\n\r\n[extension]\r\nvalue = 'unchanged' # untouched\r\n";
        var document = DcDocuments.readText(source);
        document.root().remove("identifier");
        document.root().remove("enable");
        document.set("basic", "identifier", "main");
        document.set("dependencies", "first", "first");
        document.set("dependencies", "second", "second");
        document.asSection("basic").asSection("dependencies");
        String written = document.toText();
        assertTrue(written.contains("[basic]\r\nidentifier = \"main\"\r\n"));
        assertTrue(written.contains("[dependencies]\r\n"));
        assertTrue(written.contains("# identifier\r\n"));
        assertTrue(written.contains("# first\r\n"));
        assertTrue(written.contains("future = {\"setting\": 2}\r\n"));
        assertTrue(written.contains("[extension]\r\nvalue = 'unchanged' # untouched\r\n"));
        assertEquals(document.toJson(), DcDocuments.readText(written).toJson());
    }

    @Test
    void convertsInlineObjectWithNoDataChanges() throws Exception {
        var document = DcDocuments.readText("basic = {identifier: 'main', future: {number: 2}} # keep\nother = 'value'\n");
        document.asSection("basic");
        String written = document.toText();
        assertTrue(written.contains("[basic]\n"));
        assertTrue(written.contains("[basic.future]\n"));
        assertTrue(written.contains("# keep"));
        assertTrue(written.contains("other = 'value'"));
        assertEquals(document.toJson(), DcDocuments.readText(written).toJson());
    }

    @Test
    void preservesAlreadyExistingSectionVerbatim() throws Exception {
        String source = "\uFEFF[BaSiC] // header\r\n  Identifier = 'main' # value\r\n";
        var document = DcDocuments.readText(source);
        assertSame(document, document.asSection("basic"));
        assertEquals(source, document.toText());
    }

    @Test
    void convertsOnlyNestedInlineObjectAndKeepsItsParentValues() throws Exception {
        var document = DcDocuments.readText("options = {unchanged: 1, nested: {flag: true}} # parent\nother = 'keep'\n");
        document.asSection("options", "nested");
        String written = document.toText();
        assertTrue(written.contains("[options.nested]"));
        assertTrue(written.contains("options = {\"unchanged\": 1}"));
        assertTrue(written.contains("other = 'keep'"));
        assertEquals(document.toJson(), DcDocuments.readText(written).toJson());
    }

    @Test
    void replacesLegacyArrayWithSectionAndRetainsItsComments() throws Exception {
        var document = DcDocuments.readText("conflicts = [\n  'old', # keep array note\n] # keep field note\nother = true\n");
        JsonObject relation = new JsonObject();
        relation.addProperty("old", "old");
        document.root().add("conflicts", relation);
        document.asSection("conflicts");
        String written = document.toText();
        assertFalse(written.contains("conflicts ="));
        assertTrue(written.contains("[conflicts]"));
        assertTrue(written.contains("# keep array note"));
        assertTrue(written.contains("# keep field note"));
        assertEquals(document.toJson(), DcDocuments.readText(written).toJson());
    }

    @Test
    void createsEmptySectionAndResolvesQuotedPaths() throws Exception {
        var document = DcDocuments.readText("other = true\n");
        JsonObject parent = new JsonObject();
        parent.add("name.with.dots", new JsonObject());
        document.root().add("parent", parent);
        document.asSection("parent", "name.with.dots");
        String written = document.toText();
        assertTrue(written.contains("[parent.\"name.with.dots\"]"));
        assertEquals(document.toJson(), DcDocuments.readText(written).toJson());
    }

    @Test
    void validatesPathsAndRejectsLaterTypeChanges() throws Exception {
        var document = DcDocuments.readText("field = true\n[basic]\nidentifier = 'main'\n");
        assertThrows(IllegalArgumentException.class, () -> document.asSection());
        assertThrows(IllegalArgumentException.class, () -> document.asSection("missing"));
        assertThrows(IllegalArgumentException.class, () -> document.asSection("field"));
        assertThrows(IllegalArgumentException.class, () -> document.asSection("basic", ""));
        document.asSection("basic");
        document.root().addProperty("basic", true);
        assertThrows(IllegalArgumentException.class, document::toText);
    }
}
