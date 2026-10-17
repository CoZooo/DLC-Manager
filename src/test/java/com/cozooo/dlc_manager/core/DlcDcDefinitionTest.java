package com.cozooo.dlc_manager.core;

import com.cozooo.dclanguage.DcDocuments;
import com.cozooo.dclanguage.DcLanguageService;
import com.cozooo.dclanguage.DcSchemaCatalog;
import com.google.gson.JsonPrimitive;
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

    @Test void folderMetadataRegistersDynamicFoldersAndDottedLocaleFields() throws Exception {
        var catalog = DlcDcDefinition.catalog();
        var service = new DcLanguageService(catalog);
        String text = "[A]\npriority=-3\nDisplayName.en_US='Folder'\nDisplayName.zh_CN='文件夹'\n";
        for (String uri : List.of("file:///DLC/_metadata.dc", "file:///DLC/remote/A/_metadata.dc")) {
            assertTrue(service.diagnostics(uri, text).isEmpty(), uri);
            assertTrue(service.diagnostics(uri, text.replace("priority=-3", "priority=0.5")).stream()
                .anyMatch(issue -> issue.message().contains("priority")), uri);
            assertTrue(service.diagnostics(uri, text.replace("DisplayName.en_US='Folder'", "DisplayName.en_US=42")).stream()
                .anyMatch(issue -> issue.message().contains("DisplayName.en_US")), uri);
        }
        var document = DcDocuments.readText(text);
        assertTrue(DlcDcDefinition.register("folder_metadata", document.toJson()).validate(document).valid());
    }

    @Test void registersApplicationFieldsThroughTheDcApi() throws Exception {
        var language = DlcDcDefinition.register("required");
        var basic = language.categories().get("basic");
        assertNotNull(basic);
        var unzip = basic.fields().get("unzip").field();
        assertEquals("boolean", unzip.type().name());
        assertTrue(unzip.hasDefault());
        assertEquals(new JsonPrimitive(false), unzip.defaultValue());
        assertTrue(DcSchemaCatalog.loadDefault().profiles().isEmpty());
        assertTrue(language.validate(DcDocuments.readText("[basic]\nunzip=true\n")).valid());
        assertFalse(language.validate(DcDocuments.readText("[basic]\nunzip='true'\n")).valid());
        assertThrows(IllegalArgumentException.class, () -> DlcDcDefinition.register("not-registered"));
    }
}
