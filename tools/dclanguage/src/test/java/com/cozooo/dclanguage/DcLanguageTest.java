package com.cozooo.dclanguage;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DcLanguageTest {
    @Test void registersCategoriesAndFieldsWithSmallCalls() throws Exception {
        DcLanguage language = DcLanguages.create();
        DcCategory basic = language.registerMainCategory("basic", true);
        language.addField(basic, DcField.string("identifier"), true);
        language.addField(basic, DcField.bool("enabled"), false);
        DcDocument document = DcDocuments.readText("""
            # keep this
            [basic]
            identifier = "main"
            enabled = true
            unknown = "kept"
            """);
        assertTrue(language.validate(document).valid());
        assertEquals("main", document.get("basic", "identifier").getAsString());
        assertEquals("kept", document.get("basic", "unknown").getAsString());
        assertTrue(DcDocuments.writeText(document).contains("# keep this"));
    }

    @Test void reportsMissingRequiredValuesAndWrongTypes() throws Exception {
        DcLanguage language = new DcLanguage();
        DcCategory basic = language.registerMainCategory("basic", true);
        language.addField(basic, DcField.string("identifier"), true);
        language.addField(basic, DcField.bool("enabled"), true);
        DcValidationResult result = language.validate(DcDocuments.readText("""
            [basic]
            enabled = "yes"
            """));
        assertFalse(result.valid());
        assertEquals(2, result.issues().size());
    }

    @Test void readAndWriteDoNotNeedLanguageArguments() throws Exception {
        Path file = Files.createTempFile("dc-language", ".dc");
        try {
            Files.writeString(file, """
                [basic]
                identifier = "main"
                """);
            DcDocument document = DcDocuments.read(file);
            document.set("basic", "identifier", "changed");
            DcDocuments.write(file, document);
            assertEquals("changed", DcDocuments.read(file).get("basic", "identifier").getAsString());
        } finally { Files.deleteIfExists(file); }
    }
}
