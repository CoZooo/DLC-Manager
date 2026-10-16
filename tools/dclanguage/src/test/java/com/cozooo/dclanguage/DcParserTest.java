package com.cozooo.dclanguage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DcParserTest {
    @Test void supportsIndentedAndAbsoluteDottedCategories() throws Exception {
        JsonObject root = parse("""
            [local]
              [child]
              value = 1
              [global.branch]
              value = 2
              [sibling]
              value = 3
            """);
        assertEquals(1, root.getAsJsonObject("local").getAsJsonObject("child").get("value").getAsInt());
        assertEquals(2, root.getAsJsonObject("global").getAsJsonObject("branch").get("value").getAsInt());
        assertEquals(3, root.getAsJsonObject("sibling").get("value").getAsInt());
        assertEquals(1, root.getAsJsonObject("local").size());
    }

    @Test void quotedNamesKeepDotsAndCommentMarkersLiteral() throws Exception {
        JsonObject root = parse("""
            ["namespace.with.dots". child]
            "key = # // space" = "https://example.invalid/#part" // comment
            ['single.quoted']
            'C:\\mods\\name.jar' = true
            """);
        assertEquals("https://example.invalid/#part", root.getAsJsonObject("namespace.with.dots")
            .getAsJsonObject("child").get("key = # // space").getAsString());
        assertTrue(root.getAsJsonObject("single.quoted").get("C:\\mods\\name.jar").getAsBoolean());
    }

    @Test void quotedNamesSupportEscapedQuotesAndUnicode() throws Exception {
        JsonObject root = parse("[\"a\\\"b\"]\n\"\\u540D\\u5B57\" = \"ok\"\n");
        assertEquals("ok", root.getAsJsonObject("a\"b").get("名字").getAsString());
    }

    @Test void bareEntriesAndColonBlocksWorkInAnyCategory() throws Exception {
        JsonObject root = parse("""
            [relations]
              "first module.jar"
              detailed:
                [basic]
                identifier = "module"
              "last module.jar"
            [other]
            "flat entry"
            """);
        JsonObject relations = root.getAsJsonObject("relations");
        assertEquals(3, relations.size());
        assertTrue(relations.getAsJsonObject("first module.jar").isEmpty());
        assertTrue(relations.getAsJsonObject("last module.jar").isEmpty());
        assertEquals("module", relations.getAsJsonObject("detailed").getAsJsonObject("basic").get("identifier").getAsString());
        assertTrue(root.getAsJsonObject("other").getAsJsonObject("flat entry").isEmpty());
    }

    @Test void colonBlocksReturnToTheirParentWhenAssignmentsDedent() throws Exception {
        JsonObject root = parse("""
            [preset]
            changelog:
              zh_cn = "变更"
              nested:
                item = true
              en_us = "Changes"
            hidden = false
            next:
              enabled = true
            last = 4
            """);
        JsonObject preset = root.getAsJsonObject("preset");
        assertEquals(4, preset.size());
        assertFalse(preset.get("hidden").getAsBoolean());
        assertEquals(4, preset.get("last").getAsInt());
        assertEquals("Changes", preset.getAsJsonObject("changelog").get("en_us").getAsString());
        assertTrue(preset.getAsJsonObject("changelog").getAsJsonObject("nested").get("item").getAsBoolean());
        assertTrue(preset.getAsJsonObject("next").get("enabled").getAsBoolean());
    }

    @Test void rootColonBlocksAndEmptyValuesRemainDistinct() throws Exception {
        JsonObject root = parse("settings:\n  label =\n  option = # empty\nenabled = true\nempty:\n");
        assertEquals("", root.getAsJsonObject("settings").get("label").getAsString());
        assertEquals("", root.getAsJsonObject("settings").get("option").getAsString());
        assertTrue(root.get("enabled").getAsBoolean());
        assertTrue(root.getAsJsonObject("empty").isEmpty());
    }

    @Test void valuesPreserveTypesAndLargeNumbers() throws Exception {
        JsonObject root = parse("""
            text = unquoted text
            yes = TRUE
            no = False
            nothing = null
            big = 9007199254740993123456789
            fraction = -1.234567890123456789e-10
            small = +.5
            version = 1.20.1
            """);
        assertEquals("unquoted text", root.get("text").getAsString());
        assertTrue(root.get("yes").getAsBoolean());
        assertFalse(root.get("no").getAsBoolean());
        assertTrue(root.get("nothing").isJsonNull());
        assertEquals(new BigDecimal("9007199254740993123456789"), root.get("big").getAsBigDecimal());
        assertEquals(new BigDecimal("-1.234567890123456789e-10"), root.get("fraction").getAsBigDecimal());
        assertEquals(new BigDecimal(".5"), root.get("small").getAsBigDecimal());
        assertEquals("1.20.1", root.get("version").getAsString());
    }

    @Test void arraysAndInlineObjectsCanMixSeparatorsAndNestedContainers() throws Exception {
        JsonObject root = parse("""
            values = [
              "first" # line comment
              'second', // another comment
              [1, 2,],
              {enabled: true, 'name' = "Object",
               more: {value: null}}
            ]
            empty_array = [# empty
            ]
            empty_object = {// empty
            }
            """);
        JsonArray values = root.getAsJsonArray("values");
        assertEquals(4, values.size());
        assertEquals("second", values.get(1).getAsString());
        assertEquals(2, values.get(2).getAsJsonArray().get(1).getAsInt());
        assertTrue(values.get(3).getAsJsonObject().get("enabled").getAsBoolean());
        assertTrue(values.get(3).getAsJsonObject().getAsJsonObject("more").get("value").isJsonNull());
        assertTrue(root.getAsJsonArray("empty_array").isEmpty());
        assertTrue(root.getAsJsonObject("empty_object").isEmpty());
    }

    @Test void multilineStringsKeepLiteralCommentsQuotesAndEscapes() throws Exception {
        String text = "body = \"\"\"\n# not a comment\n// not a comment\n\\n and \"quoted\" inside\"\"\" # tail\n"
            + "other = '''line one\r\nline two'''\r\n";
        JsonObject root = parse(text);
        assertEquals("\n# not a comment\n// not a comment\n\\n and \"quoted\" inside", root.get("body").getAsString());
        assertEquals("line one\r\nline two", root.get("other").getAsString());
    }

    @Test void normalStringsDecodeEscapesAndSingleQuotesStayLiteral() throws Exception {
        JsonObject root = parse("double = \"a\\nb\\t\\u4F60\\u597D\\\\\\\"\\q\"\nsingle = 'a\\nb\\t'\n");
        assertEquals("a\nb\t你好\\\"\\q", root.get("double").getAsString());
        assertEquals("a\\nb\\t", root.get("single").getAsString());
    }

    @Test void repeatedSectionsCanAddFieldsWithoutOverwritingEarlierFields() throws Exception {
        DcParser.Parsed parsed = DcParser.parse("[basic]\none = 1\n[other]\nvalue = 3\n[basic]\ntwo = 2\n");
        assertEquals(2, parsed.root().getAsJsonObject("basic").size());
        assertEquals(2, parsed.bindings().stream().filter(b -> b.kind() == DcParser.Kind.SECTION && b.path().equals(List.of("basic"))).count());
    }

    @Test void bindingOffsetsPreserveBomCrLfAndMultilineValues() throws Exception {
        String text = "\uFEFF# heading\r\n[basic] // section\r\n  body = \"\"\"a\r\nb\"\"\" # tail\r\n";
        DcParser.Parsed parsed = DcParser.parse(text);
        DcParser.Binding body = parsed.bindings().get(1);
        assertEquals("\r\n", parsed.newline());
        assertEquals(List.of("basic", "body"), body.path());
        assertEquals("\"\"\"a\r\nb\"\"\"", text.substring(body.valueStart(), body.valueEnd()));
        assertTrue(text.substring(body.commentStart(), body.end()).startsWith("# tail"));
        assertEquals("  ", body.indent());
        assertEquals(text.length(), body.end());
        assertEquals(text.length(), parsed.sections().get(List.of("basic")).insertAt);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[", "[]", "[[array]]", "[a..b]", "[\"unterminated]", "[a] garbage",
        "= 1", "\"\" = 1", "\"bad name = 1", "value = \"unterminated",
        "value = \"ok\" trailing", "value = [1,}", "value = [1,", "value = [1,,2]",
        "value = [\"one\" \"two\"]", "value = {a: 1]", "value = {a:}",
        "value = {a: 1, a: 2}", "value = {a,b: 1}", "value = }", "value = true]",
        "value = {a: [1, 2]", "value = '''unterminated", "value = 1e99999999999"
    })
    void malformedDocumentsProduceParseExceptions(String text) {
        DcParseException error = assertThrows(DcParseException.class, () -> parse(text));
        assertTrue(error.line() >= 1);
        assertTrue(error.column() >= 1);
        assertTrue(error.getMessage().contains("line "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "name = 1\nname = 2", "[one]\nx = 1\n[one]\nx = 2", "[list]\nentry\nentry",
        "name = 1\n[name]\n", "[basic]\nname = 1\n[other]\n[basic.name.child]\n"
    })
    void duplicateValuesAndCategoryCollisionsAreRejected(String text) {
        assertThrows(DcParseException.class, () -> parse(text));
    }

    @Test void quotedNameErrorsReferToTheOriginalDocument() {
        DcParseException error = assertThrows(DcParseException.class,
            () -> parse("# heading\n[basic]\n\"bad\\u00Q0\" = true\n"));
        assertEquals(3, error.line());
        assertEquals(7, error.column());
    }

    @Test void lineAndColumnHandleCrLfBareCrAndBom() {
        DcParseException crlf = assertThrows(DcParseException.class,
            () -> parse("\uFEFF# heading\r\n[basic]\r\nname = \"ok\" more\r\n"));
        assertEquals(3, crlf.line());
        assertEquals(13, crlf.column());
        DcParseException cr = assertThrows(DcParseException.class,
            () -> parse("a = 1\rnext=[1,}\r"));
        assertEquals(2, cr.line());
        assertEquals(9, cr.column());
        DcParseException bom = assertThrows(DcParseException.class, () -> parse("\uFEFFa = }"));
        assertEquals(1, bom.line());
        assertEquals(5, bom.column());
    }

    @Test void excessiveContainerDepthFailsWithoutOverflow() {
        String text = "value = " + "[".repeat(300) + "0" + "]".repeat(300);
        DcParseException error = assertThrows(DcParseException.class, () -> parse(text));
        assertTrue(error.getMessage().contains("nesting"));
    }

    private static JsonObject parse(String text) throws DcParseException {
        return DcParser.parse(text).root();
    }
    @org.junit.jupiter.api.Test
    void deeplyNestedCategoryNamesFailBeforeBuildingAnUnboundedTree() {
        String source = "[" + String.join(".", java.util.Collections.nCopies(300, "category")) + "]\nvalue=1\n";
        org.junit.jupiter.api.Assertions.assertThrows(DcParseException.class, () -> DcDocuments.readText(source));
    }
}
