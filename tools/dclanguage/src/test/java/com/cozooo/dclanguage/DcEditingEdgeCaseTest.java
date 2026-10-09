package com.cozooo.dclanguage;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DcEditingEdgeCaseTest {
    @Test
    void editingInlineObjectExtendedByASectionDoesNotDuplicateFields() throws Exception {
        DcDocument document = DcDocuments.readText("""
            config = {"inline": 1} # inline fields
            [config]
            extended = 2 # section fields
            """);

        document.category("config").addProperty("inline", 3);
        String written = document.toText();

        assertTrue(written.contains("# inline fields"));
        assertTrue(written.contains("# section fields"));
        assertRoundTrip(document, written);
    }

    @Test
    void editingBareEntryExtendedByASectionDoesNotDuplicateFields() throws Exception {
        DcDocument document = DcDocuments.readText("""
            [dependencies]
            "library" # entry
            [dependencies.library]
            version = "1.0" # version
            """);

        document.category("dependencies", "library").addProperty("version", "2.0");
        String written = document.toText();

        assertTrue(written.contains("# entry"));
        assertTrue(written.contains("# version"));
        assertRoundTrip(document, written);
    }

    @Test
    void deletingFirstBomSectionAndAddingRootFieldKeepsValidRootScope() throws Exception {
        DcDocument document = DcDocuments.readText("\uFEFF[old]\nvalue = 1");

        document.root().remove("old");
        document.root().addProperty("replacement", 2);
        String written = document.toText();

        assertTrue(written.startsWith("\uFEFF"));
        assertRoundTrip(document, written);
    }

    @Test
    void editingFirstBomArrayRetainsBomBeforeExtractedComments() throws Exception {
        DcDocument document = DcDocuments.readText("\uFEFFvalues = [\n  1, # keep\n  2\n]");

        document.root().add("values", JsonParser.parseString("[3, 4]"));
        String written = document.toText();

        assertTrue(written.startsWith("\uFEFF"));
        assertTrue(written.contains("# keep"));
        assertRoundTrip(document, written);
    }

    @Test
    void bareEntriesWithTrailingCommasRemainLossless() throws Exception {
        String text = "[entries]\n\"mods/old.jar\", # path\n\"config/a.toml\",\n";
        DcDocument document = DcDocuments.readText(text);
        assertEquals(text, document.toText());
        assertTrue(document.category("entries").has("mods/old.jar"));
        document.category("entries").remove("config/a.toml");
        assertRoundTrip(document, document.toText());
    }

    private static void assertRoundTrip(DcDocument document, String written) throws Exception {
        assertEquals(document.toJson(), DcDocuments.readText(written).toJson());
    }
}
