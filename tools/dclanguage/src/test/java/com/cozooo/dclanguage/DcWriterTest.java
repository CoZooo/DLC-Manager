package com.cozooo.dclanguage;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class DcWriterTest {
    @TempDir Path directory;

    @Test void editingPreservesCommentsUnknownFieldsSpacingBomAndCrLf() throws Exception {
        String text = "\uFEFF# header\r\n[basic] // category\r\n  identifier  =  'old'  # name\r\n  unknown = [1, null, {\"x\":true}]\r\n\r\n# footer\r\n";
        DcDocument document = DcDocuments.readText(text);
        document.set("basic", "identifier", "new");
        assertEquals(text.replace("'old'", "'new'"), document.toText());
        roundTrip(document);
    }

    @Test void additionsUseOriginalSectionAndKeepNestedSectionsIntact() throws Exception {
        DcDocument document = DcDocuments.readText("revision = 1\n# settings\n[basic]\nidentifier = \"main\"\n  [nested]\n  old = true\n[other]\nvalue = \"kept\"\n");
        document.set("basic", "mode", "hash");
        document.root().addProperty("format", 2);
        document.category("basic", "nested").addProperty("new", true);
        String written = document.toText();
        assertTrue(written.indexOf("mode =") < written.indexOf("[nested]"));
        assertTrue(written.indexOf("format =") < written.indexOf("[basic]"));
        assertTrue(written.contains("# settings"));
        roundTrip(document);
    }

    @Test void additionsToImplicitParentsAndNewCategoriesKeepScope() throws Exception {
        DcDocument document = DcDocuments.readText("[a.b]\nx = 1\n[c]\nq = false");
        document.category("a").addProperty("y", 2);
        document.set("new", "nested", JsonParser.parseString("{\"entry\": {\"enabled\":true}}"));
        document.root().addProperty("root_value", "root");
        roundTrip(document);
    }

    @Test void editsReopenedCategoriesWithoutDuplicatingFields() throws Exception {
        DcDocument document = DcDocuments.readText("[a]\none = 1\n[b]\nv = 2\n[a]\ntwo = 2\n");
        document.set("a", "three", 3);
        document.remove("a", "one");
        roundTrip(document);
        assertTrue(document.toText().endsWith("two = 2\nthree = 3\n"));
    }

    @Test void removingACategoryKeepsCommentsWithoutLeakingChildren() throws Exception {
        DcDocument document = DcDocuments.readText("# document\n[a] # group\nx = 1 # number\n  [nested]\n  val = [\n    \"# inside string\", # array comment\n    2\n  ] // end array\n[b]\nx = 3\n");
        document.root().remove("a");
        String written = document.toText();
        for (String comment : List.of("# document", "# group", "# number", "# array comment", "// end array")) assertTrue(written.contains(comment), comment);
        assertFalse(written.contains("# inside string"));
        roundTrip(document);
    }

    @Test void replacingASectionAndBareEntryIsSupported() throws Exception {
        DcDocument document = DcDocuments.readText("[a]\nx = 1\n[b]\n\"token\" # token comment\n");
        document.root().addProperty("a", "new");
        document.category("b").getAsJsonObject("token").addProperty("enabled", true);
        assertTrue(document.toText().contains("# token comment"));
        roundTrip(document);
    }

    @Test void compositeChangesKeepInteriorCommentsAndTrailingComments() throws Exception {
        DcDocument document = DcDocuments.readText("[a]\nitems = [\n  \"https://example.com/#fragment\", # first\n  2 // second\n] # tail\n");
        document.set("a", "items", JsonParser.parseString("[3,4]"));
        String text = document.toText();
        for (String comment : List.of("# first", "// second", "# tail")) assertTrue(text.contains(comment));
        assertFalse(text.contains("#fragment"));
        roundTrip(document);
    }

    @Test void inlineObjectChangesKeepUnknownValuesAndComments() throws Exception {
        DcDocument document = DcDocuments.readText("[a]\nconfig = {\n  title: 'old', # title\n  extra: [1, null, false]\n} # config\nother = 'unchanged'\n");
        document.category("a").getAsJsonObject("config").addProperty("title", "changed");
        String written = document.toText();
        for (String fragment : List.of("# title", "# config", "other = 'unchanged'")) assertTrue(written.contains(fragment));
        roundTrip(document);
    }

    @Test void tripleQuotedChangesKeepLiteralBackslashesAndLineEndings() throws Exception {
        String text = "[text]\nbody = \"\"\"old\r\nraw\\n\"\"\" # ending\r\n";
        DcDocument document = DcDocuments.readText(text);
        document.set("text", "body", "new\r\nraw\\n");
        assertEquals(text.replace("old", "new"), document.toText());
        document.set("text", "body", "quote\"\"\" and ''' with CR\r\n and \\");
        roundTrip(document);
    }

    @Test void normalizedQuotedPathsPreserveNestingAndEmptyObjects() throws Exception {
        JsonObject root = JsonParser.parseString("{\"root.with.dot\":{\"has\\\"quote\":{\"number\":1}},\"a\":{\"b\":{}},\"empty\":{}}").getAsJsonObject();
        DcDocument document = DcDocuments.fromJson(root);
        String text = document.toText(DcWriteOptions.NORMALIZE);
        assertTrue(text.contains("[\"root.with.dot\".\"has\\\"quote\"]"));
        assertEquals(root, DcDocuments.readText(text).toJson());
    }

    @Test void unchangedTextIsIdenticalIncludingFinalNewlinePolicy() throws Exception {
        for (String text : List.of("", "# comment", "\uFEFF", "[a]\r\nx = 'v'", "[a]\nx = 1 # comment\n", "one = null\n")) {
            assertEquals(text, DcDocuments.readText(text).toText());
        }
    }

    @Test void failedSerializationLeavesExistingFileIntact() throws Exception {
        Path path = directory.resolve("sample.dc");
        Files.writeString(path, "v = 1\n");
        DcDocument document = DcDocuments.read(path);
        document.root().addProperty("v", Double.NaN);
        assertThrows(IllegalStateException.class, () -> DcDocuments.write(path, document));
        assertEquals("v = 1\n", Files.readString(path));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void repeatedSavesCanRevertToOriginalContent() throws Exception {
        Path path = directory.resolve("sample.dc");
        String original = "[basic]\nidentifier = 'main' # keep\n";
        Files.writeString(path, original);
        DcDocument document = DcDocuments.read(path);
        for (String value : List.of("one", "two", "main")) {
            document.set("basic", "identifier", value);
            DcDocuments.write(path, document);
            assertEquals(value, DcDocuments.read(path).getString("basic", "identifier"));
        }
        assertEquals(original, Files.readString(path));
    }

    @Test void deterministicGeneratedDocumentsRoundTripAllValueTypes() throws Exception {
        Random random = new Random(73105);
        for (int i = 0; i < 100; i++) {
            JsonObject root = new JsonObject();
            for (int key = 0; key < 4; key++) root.add("key." + key, generated(random, 0));
            DcDocument document = DcDocuments.fromJson(root);
            DcDocument reread = DcDocuments.readText(document.toText());
            assertEquals(root, reread.toJson());
            reread.root().addProperty("added", "new value");
            reread.root().remove("key.1");
            reread.root().add("key.2", generated(random, 0));
            roundTrip(reread);
        }
    }

    @Test void changesToLargeAndHighPrecisionNumbersAreNotMistakenForEqualValues() throws Exception {
        for (String[] values : List.of(
            new String[]{"9007199254740992", "9007199254740993"},
            new String[]{"1.00000000000000000001", "1.00000000000000000002"},
            new String[]{"1e900", "2e900"}
        )) {
            DcDocument document = DcDocuments.readText("v = " + values[0] + " # keep\n");
            document.root().addProperty("v", new java.math.BigDecimal(values[1]));
            String written = document.toText();
            assertEquals(0, new java.math.BigDecimal(values[1]).compareTo(DcDocuments.readText(written).root().get("v").getAsBigDecimal()));
            assertTrue(written.contains("# keep"));
        }
    }

    @Test void additionsToCrFilesKeepTheirLineSeparator() throws Exception {
        DcDocument document = DcDocuments.readText("[a]\rv = 1\r");
        document.set("a", "added", 2);
        assertEquals("[a]\rv = 1\radded = 2\r", document.toText());
    }

    @Test void inlineObjectAndSectionExtensionsCanBeChangedTogether() throws Exception {
        DcDocument document = DcDocuments.readText("config = {sub: {x: 1}} # inline\n[config.sub]\ny = 2 # extension\n[config.sub.child]\nz = 3\n");
        document.category("config", "sub").addProperty("x", 4);
        document.category("config", "sub").addProperty("y", 5);
        document.category("config", "sub").addProperty("new", 6);
        roundTrip(document);
        document.category("config").addProperty("sub", "replaced");
        roundTrip(document);
        document.root().addProperty("config", "all replaced");
        roundTrip(document);
    }

    private void roundTrip(DcDocument document) throws Exception { assertEquals(document.toJson(), DcDocuments.readText(document.toText()).toJson()); }
    private JsonElement generated(Random random, int depth) {
        int type = random.nextInt(depth < 3 ? 7 : 4);
        return switch (type) {
            case 0 -> JsonNull.INSTANCE;
            case 1 -> new JsonPrimitive(random.nextBoolean());
            case 2 -> new JsonPrimitive(random.nextInt(1000));
            case 3 -> new JsonPrimitive(List.of("quote\"'\\\n\r\t", "https://url/#hash", "中文😀", "").get(random.nextInt(4)));
            case 4, 5 -> {
                JsonObject object = new JsonObject();
                int count = random.nextInt(4);
                for (int i = 0; i < count; i++) object.add("node." + i, generated(random, depth + 1));
                yield object;
            }
            default -> {
                JsonArray array = new JsonArray();
                int count = random.nextInt(4);
                for (int i = 0; i < count; i++) array.add(generated(random, depth + 1));
                yield array;
            }
        };
    }
    @Test void valueWriterUsesTheSameLosslessGrammarAsDocuments() throws Exception {
        for (String json : List.of("true", "null", "[1,2]", "{\"key\":\"value\"}", "\"line\\ntext\"")) {
            JsonElement value = JsonParser.parseString(json);
            String text = DcDocuments.writeValue(value);
            assertEquals(value, DcDocuments.readText("value = " + text).root().get("value"));
        }
    }
}
