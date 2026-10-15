package com.cozooo.dclanguage;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DcLanguageServiceTest {
    private DcLanguageService service() {
        return new DcLanguageService(DcSchemaCatalog.fromJson(JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/service/language-definition.json"), StandardCharsets.UTF_8)).getAsJsonObject()));
    }
    @Test
    void usesGeneratedSchemaForRequiredTypesEnumsAndConstraints() {
        var service = service();
        assertTrue(service.diagnostics("file:///test.dc", "[basic]\nidentifier='x'\nmode='hash'\nunknown={x:true}\n").isEmpty());
        var issues = service.diagnostics("file:///test.dc", "[basic]\nmode='invalid'\nenabled='yes'\ncount=20\n");
        assertEquals(4, issues.size());
        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("identifier") && issue.message().contains("Required")));
        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("hash, name")));
        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("boolean")));
        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("constraints")));
    }
    @Test
    void optionalCategoryRequiresItsFieldsOnlyWhenPresent() {
        var service = service();
        assertTrue(service.diagnostics("file:///test.dc", "[basic]\nidentifier='x'\n").isEmpty());
        assertEquals(1, service.diagnostics("file:///test.dc", "[basic]\nidentifier='x'\n[optional]\n").size());
        assertTrue(service.diagnostics("file:///test.dc", "").get(0).message().contains("Required category"));
    }
    @Test
    void syntaxDiagnosticsHaveUtf16Ranges() {
        var issues = service().diagnostics("file:///test.dc", "[basic]\nidentifier = \"😀\" trailing\n");
        assertEquals("syntax", issues.get(0).code());
        assertEquals(1, issues.get(0).range().start().line());
        assertEquals(18, issues.get(0).range().start().character());
    }
    @Test
    void completesCategoriesFieldsAndEnumValuesInIncompleteBuffers() {
        var service = service();
        assertTrue(service.completions("file:///test.dc", "[bas", new DcLanguageService.Position(0, 4)).stream().anyMatch(item -> item.insertText().equals("[basic]")));
        assertTrue(service.completions("file:///test.dc", "[basic]\niden", new DcLanguageService.Position(1, 4)).stream().anyMatch(item -> item.label().equals("identifier")));
        var completions = service.completions("file:///test.dc", "[basic]\nmode = \"ha", new DcLanguageService.Position(1, 10));
        assertEquals(List.of("hash"), completions.stream().map(DcLanguageService.Completion::label).toList());
        assertEquals("\"hash\"", completions.get(0).insertText());
        assertEquals(7, completions.get(0).range().start().character());
    }
    @Test
    void doesNotCompleteInsideCommentsOrMultilineValues() {
        var service = service();
        assertTrue(service.completions("file:///test.dc", "[basic]\n# iden", new DcLanguageService.Position(1, 6)).isEmpty());
        assertTrue(service.completions("file:///test.dc", "[basic]\nidentifier = \"\"\"\niden", new DcLanguageService.Position(2, 4)).isEmpty());
    }
    @Test
    void hoverDescribesFieldAndAllowedValues() {
        var hover = service().hover("file:///test.dc", "[basic]\nidentifier='x'\nmode='hash'\n", new DcLanguageService.Position(2, 2));
        assertNotNull(hover);
        assertTrue(hover.markdown().contains("Matching mode"));
        assertTrue(hover.markdown().contains("`hash`"));
        assertEquals(2, hover.range().start().line());
    }
    @Test
    void formattingChangesWhitespaceWithoutDeletingCommentsOrValues() throws Exception {
        String source = "\uFEFF# heading\r\n[ basic ] # category\r\nidentifier= '😀' # name\r\nunknown  = {\"data\": 2}\r\nbody = \"\"\"\r\n  [ untouched ]\r\n\"\"\"\r\n";
        var edits = service().format(source, 2, true);
        assertEquals(1, edits.size());
        String written = edits.get(0).newText();
        assertTrue(written.contains("[basic] # category\r\n"));
        assertTrue(written.contains("identifier = '😀' # name\r\n"));
        assertTrue(written.contains("  [ untouched ]\r\n"));
        assertEquals(DcDocuments.readText(source).toJson(), DcDocuments.readText(written).toJson());
        assertTrue(service().format(written, 2, true).isEmpty());
    }
    @Test
    void invalidSyntaxIsNeverFormattedAway() {
        assertTrue(service().format("identifier = \"broken", 4, true).isEmpty());
    }
    @Test
    void utf16CoordinatesHandleAstralCharactersAndAllNewlineStyles() {
        String text = "a😀b\r\nsecond\rlast\n";
        assertEquals(3, DcLanguageService.offset(text, new DcLanguageService.Position(0, 3)));
        assertEquals(new DcLanguageService.Position(0, 3), DcLanguageService.position(text, 3));
        assertEquals(6, DcLanguageService.offset(text, new DcLanguageService.Position(1, 0)));
        assertEquals(new DcLanguageService.Position(2, 0), DcLanguageService.position(text, 13));
        assertEquals(4, DcLanguageService.offset(text, new DcLanguageService.Position(0, 900)));
        assertEquals(4, DcLanguageService.offset(text, new DcLanguageService.Position(0, Integer.MAX_VALUE)));
    }
    @Test
    void choosesProfilesUsingMetadataRatherThanHardCodedDirectoryNames() throws Exception {
        JsonObject definition = JsonParser.parseString("""
            {"profiles":[
              {"id":"a","filePatterns":["**/special/*.dc"],"categories":[{"path":["a"],"fields":[{"name":"flag","type":"boolean"}]}]},
              {"id":"b","filePatterns":["**/other/*.dc"],"categories":[{"path":["b"],"fields":[{"name":"count","type":"number"}]}]}
            ]}
            """).getAsJsonObject();
        var catalog = DcSchemaCatalog.fromJson(definition);
        assertEquals("a", catalog.select("file:///project/special/file.dc", null, null).id());
        assertEquals("b", catalog.select("C:\\project\\other\\file.dc", null, null).id());
        assertEquals("b", catalog.select("untitled:document", DcDocuments.readText("[b]\ncount=3").root(), null).id());
        assertThrows(IllegalArgumentException.class, () -> catalog.select("file:///file.dc", null, "missing"));
    }
    @Test
    void wildcardMetadataValidatesDynamicFieldsWithoutOverridingExplicitRules() {
        var definition = JsonParser.parseString("""
            {"profiles":[{"id":"dynamic","filePatterns":["**/*.dc"],"categories":[
              {"path":["translations"],"fields":[{"name":"enabled","type":"boolean"},{"name":"*","type":"string"}]},
              {"path":["entries","*"],"fields":[{"name":"identifier","type":"string","required":true}]}
            ]}]}
            """).getAsJsonObject();
        var service = new DcLanguageService(DcSchemaCatalog.fromJson(definition));
        var issues = service.diagnostics("file:///example.dc", "[translations]\nenabled=true\nzh_cn=123\n[entries.one]\nidentifier='ok'\n[entries.two]\nidentifier=false\n");
        assertEquals(2, issues.size());
        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("translations.zh_cn")));
        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("entries.two.identifier")));
        assertTrue(service.completions("file:///example.dc", "[entries.two]\niden", new DcLanguageService.Position(1, 4)).stream().anyMatch(item -> item.label().equals("identifier")));
    }

    @Test
    void ambiguousOrUntitledDocumentsStillOfferMetadataCompletionsWithoutGuessingValidationRules() {
        var definition = JsonParser.parseString("""
            {"profiles":[
              {"id":"first","categories":[{"path":["basic"],"fields":[{"name":"identifier","type":"string","required":true}]}]},
              {"id":"second","categories":[{"path":["basic"],"fields":[{"name":"identifier","type":"string","required":true}]}]}
            ]}
            """).getAsJsonObject();
        var service = new DcLanguageService(DcSchemaCatalog.fromJson(definition));
        assertTrue(service.diagnostics("untitled:new", "[basic]\n").isEmpty());
        var completions = service.completions("untitled:new", "[basic]\niden", new DcLanguageService.Position(1, 4));
        assertEquals(1, completions.size());
        assertEquals("identifier", completions.get(0).label());
    }

    @Test
    void completingAnExistingFieldKeepsItsValueAndInlineComment() {
        String source = "[basic]\niden = 'existing' # keep\n";
        var completion = service().completions("file:///example.dc", source, new DcLanguageService.Position(1, 4)).get(0);
        int start = DcLanguageService.offset(source, completion.range().start());
        int end = DcLanguageService.offset(source, completion.range().end());
        String completed = source.substring(0, start) + completion.insertText() + source.substring(end);
        assertEquals("[basic]\nidentifier = 'existing' # keep\n", completed);
    }

    @org.junit.jupiter.api.Test
    void completionNeverInsertsRootOrWildcardCategories() {
        var service = new DcLanguageService(DcSchemaCatalog.fromJson(com.google.gson.JsonParser.parseString("""
            {"profiles":[{"id":"example","categories":[
              {"path":[],"fields":[]},
              {"path":["entries","*"],"fields":[]},
              {"path":["basic"],"fields":[]}
            ]}]}
            """).getAsJsonObject()));
        var completions = service.completions("untitled:test", "[", new DcLanguageService.Position(0,1), "example");
        org.junit.jupiter.api.Assertions.assertEquals(1, completions.size());
        org.junit.jupiter.api.Assertions.assertEquals("[basic]", completions.get(0).insertText());
    }
    @Test
    void updateDeleteHashUsesSharedDynamicPathDefinition() throws Exception {
        var catalog = DcSchemaCatalog.loadDefault();
        var field = catalog.field(catalog.profile("update"), List.of("file", "del", "mods/a.jar"), "hash");
        assertNotNull(field);
        assertEquals("string", field.type());
        var service = new DcLanguageService(catalog);
        String text = """
            [file]
              [del]
                "mods/a.jar":
                  hash = "abc"
                "config/legacy.txt"
            """;
        assertTrue(service.diagnostics("file:///update.dc", text).isEmpty());
        assertTrue(service.diagnostics("file:///update.dc", text.replace("hash = \"abc\"", "hash = 42")).stream()
            .anyMatch(issue -> issue.message().contains("hash")));
        assertEquals(DcDocuments.readText(text).toJson(), DcDocuments.readText(DcDocuments.writeText(DcDocuments.readText(text))).toJson());
    }

    @Test void requiredZipOptionUsesGeneratedBooleanDefaultAndEditorRules() {
        var catalog = DcSchemaCatalog.loadDefault();
        var field = catalog.profile("required").categories().stream()
            .filter(category -> category.path().equals(List.of("basic"))).flatMap(category -> category.fields().stream())
            .filter(value -> value.name().equals("unzip")).findFirst().orElseThrow();
        assertEquals("boolean", field.type());
        assertFalse(field.defaultValue().getAsBoolean());
        var service = new DcLanguageService(catalog);
        String uri = "file:///instance/DLC/required/main.dc";
        assertTrue(service.diagnostics(uri, "[basic]\nunzip=false\n").isEmpty());
        assertTrue(service.diagnostics(uri, "[basic]\nunzip='true'\n").stream().anyMatch(issue -> issue.message().contains("boolean")));
        assertTrue(service.completions(uri, "[basic]\nun", new DcLanguageService.Position(1, 2)).stream().anyMatch(value -> value.label().equals("unzip")));
    }

}
