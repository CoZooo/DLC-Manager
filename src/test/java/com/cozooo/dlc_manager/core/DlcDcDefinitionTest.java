package com.cozooo.dlc_manager.core;

import com.cozooo.dclanguage.DcDocuments;
import com.cozooo.dclanguage.DcLanguageService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DlcDcDefinitionTest {
    @Test
    void updateDeleteHashUsesApplicationDynamicPathDefinition() throws Exception {
        var catalog = DlcDcDefinition.catalog();
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

    @Test void requiredZipOptionUsesApplicationBooleanDefaultAndEditorRules() {
        var catalog = DlcDcDefinition.catalog();
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

    @Test void registersApplicationFieldsThroughTheDcApi() throws Exception {
        var language = DlcDcDefinition.register("required");
        var basic = language.categories().get("basic");
        assertNotNull(basic);
        assertEquals("boolean", basic.fields().get("unzip").field().type().name());
        assertTrue(language.validate(DcDocuments.readText("[basic]\nunzip=true\n")).valid());
        assertFalse(language.validate(DcDocuments.readText("[basic]\nunzip='true'\n")).valid());
        assertThrows(IllegalArgumentException.class, () -> DlcDcDefinition.register("not-registered"));
    }
}
