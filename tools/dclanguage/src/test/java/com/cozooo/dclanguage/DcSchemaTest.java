package com.cozooo.dclanguage;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DcSchemaTest {
    @TempDir Path directory;

    @Test void nestedRequiredFieldsAreCheckedOnlyWhenOptionalParentIsPresent() throws Exception {
        DcLanguage language = DcLanguages.create();
        DcCategory basic = language.registerMainCategory("basic", true);
        language.addField(basic, DcField.string("identifier"), true);
        DcCategory optional = language.registerMainCategory("optional", false);
        DcCategory nested = language.registerCategory(optional, "nested", true);
        DcField<Boolean> enabled = language.addField(nested, DcField.bool("enabled"), true);
        DcDocument document = DcDocuments.readText("[basic]\nidentifier = \"main\"\n");
        assertTrue(language.validate(document).valid());
        document.set(List.of("optional"), "other", new JsonPrimitive(1));
        assertEquals("optional.nested", language.validate(document).issues().get(0).path());
        document.set(nested, enabled, true);
        assertTrue(language.validate(document).valid());
        assertTrue(document.get(nested, enabled));
    }

    @Test void typedCaseInsensitiveAccessPreservesOriginalSpelling() throws Exception {
        DcLanguage language = DcLanguages.create();
        DcCategory basic = language.registerMainCategory("basic", true);
        DcField<String> field = language.addField(basic, DcField.string("identifier"), true);
        DcDocument document = DcDocuments.readText("[BASIC]\nIdentifier = \"main\"\n");
        assertTrue(language.validate(document).valid());
        assertEquals("main", document.get(basic, field));
        document.set(basic, field, "changed");
        assertEquals(1, document.root().size());
        assertEquals(1, document.category(basic).size());
        assertEquals("changed", document.root().getAsJsonObject("BASIC").get("Identifier").getAsString());
        document.remove(basic, field);
        assertEquals("basic.identifier", language.validate(document).issues().get(0).path());
    }

    @Test void typedCaseSensitiveAccessFollowsSchemaPolicy() throws Exception {
        DcLanguage language = DcLanguages.create().caseSensitive(true);
        DcCategory basic = language.registerMainCategory("basic", true);
        DcField<String> field = language.addField(basic, DcField.string("Identifier"), true);
        DcDocument document = DcDocuments.readText("[basic]\nidentifier = \"lower\"\n");
        assertFalse(language.validate(document).valid());
        assertNull(document.get(basic, field));
        document.set(basic, field, "upper");
        assertEquals("upper", document.get(basic, field));
        assertEquals("lower", document.root().getAsJsonObject("basic").get("identifier").getAsString());
        assertTrue(language.validate(document).valid());
        assertThrows(IllegalArgumentException.class, () -> document.get("basic", "identifier"));
        document.caseSensitive(true);
        assertEquals("lower", document.getString("basic", "identifier"));
        document.remove(basic, field);
        assertEquals(1, document.category(basic).size());
    }

    @Test void ignoresUnknownFieldsAndCategoriesWithoutRemovingThem() throws Exception {
        DcLanguage language = DcLanguages.create();
        DcCategory basic = language.registerMainCategory("basic", true);
        language.addField(basic, DcField.string("identifier"), true);
        DcDocument document = DcDocuments.readText("[basic]\nidentifier = \"main\"\nUnknown = true\nunknown = false\n[extra]\nvalue = [1, null, { x: true }]\n");
        String before = document.toText();
        assertTrue(language.validate(document).valid());
        assertEquals(before, document.toText());
    }

    @Test void reportsAmbiguousDeclaredKeysInsteadOfChoosingOne() throws Exception {
        DcLanguage language = DcLanguages.create();
        DcCategory basic = language.registerMainCategory("basic", true);
        DcField<String> field = language.addField(basic, DcField.string("identifier"), true);
        DcDocument document = DcDocuments.readText("[basic]\nidentifier = \"a\"\nIdentifier = \"b\"\n");
        assertFalse(language.validate(document).valid());
        assertTrue(language.validate(document).issues().get(0).message().contains("Ambiguous"));
        assertThrows(IllegalArgumentException.class, () -> document.get(basic, field));
        assertThrows(IllegalArgumentException.class, () -> document.set(basic, field, "c"));
    }

    @Test void unicodeCaseAmbiguityUsesTheSameRulesForValidationAndAccess() {
        DcLanguage language = DcLanguages.create();
        DcField<String> field = language.addField(language.rootCategory(), DcField.string("id"), true);
        JsonObject root = new JsonObject();
        root.addProperty("id", "first");
        root.addProperty("ıd", "second");
        DcDocument document = DcDocuments.fromJson(root);
        assertFalse(language.validate(document).valid());
        assertThrows(IllegalArgumentException.class, () -> document.get(language.rootCategory(), field));
    }

    @Test void rejectsForeignCategoriesAndDuplicateRegistrations() {
        DcLanguage language = DcLanguages.create();
        DcCategory basic = language.registerMainCategory("basic", true);
        assertThrows(IllegalArgumentException.class, () -> language.registerMainCategory("BASIC", false));
        language.addField(basic, DcField.string("mode"), false);
        assertThrows(IllegalArgumentException.class, () -> language.registerCategory(basic, "MODE", false));
        assertThrows(IllegalArgumentException.class, () -> language.addField(DcLanguages.create().rootCategory(), DcField.string("test"), false));
        assertThrows(IllegalStateException.class, () -> language.caseSensitive(true));
        assertThrows(UnsupportedOperationException.class, () -> basic.fields().clear());
    }

    @Test void defaultsAreTypedAndNeverInsertedIntoDocument() {
        DcLanguage language = DcLanguages.create();
        DcCategory basic = language.registerMainCategory("basic", true);
        DcField<List<String>> field = language.addField(basic, DcField.list("tags", DcTypes.STRING).defaultValue(List.of("default")), true);
        DcDocument document = DcDocuments.create();
        assertEquals(List.of("default"), document.get(basic, field));
        assertTrue(document.root().isEmpty());
        assertFalse(language.validate(document).valid());
        assertThrows(IllegalArgumentException.class, () -> DcField.string("name").defaultValue("").check(v -> !v.isEmpty(), "Must not be empty"));
    }

    @Test void customValidatorsKeepTheFirstFailingMessage() {
        DcLanguage language = DcLanguages.create();
        DcCategory basic = language.registerMainCategory("basic", true);
        DcField<String> field = language.addField(basic, DcField.string("name")
                .check(v -> !v.isEmpty(), "Must not be empty")
                .check(v -> v.length() <= 3, "Must be short"), true);
        DcDocument document = DcDocuments.create().set(basic, field, "");
        assertEquals("Must not be empty", language.validate(document).issues().get(0).message());
        document.set(basic, field, "long");
        assertEquals("Must be short", language.validate(document).issues().get(0).message());
        document.set(basic, field, "ok");
        assertTrue(language.validate(document).valid());
    }

    @Test void enumListNullableAndDecimalValuesRoundTrip() throws Exception {
        DcLanguage language = DcLanguages.create();
        DcCategory basic = language.registerMainCategory("basic", true);
        DcField<String> mode = language.addField(basic, DcField.enumeration("mode", "hash", "name"), true);
        DcField<List<String>> tags = language.addField(basic, DcField.list("tags", DcTypes.nullable(DcTypes.STRING)), true);
        DcField<String> optional = language.addField(basic, new DcField<>("optional", DcTypes.nullable(DcTypes.STRING)), true);
        DcField<BigDecimal> number = language.addField(basic, DcField.number("number"), true);
        DcDocument document = DcDocuments.create().set(basic, mode, "hash")
                .set(basic, tags, java.util.Arrays.asList("tag", null))
                .set(basic, optional, null)
                .set(basic, number, new BigDecimal("12345678901234567890.123456789"));
        assertTrue(language.validate(document).valid());
        DcDocument restored = DcDocuments.readText(document.toText());
        assertEquals(document.toJson(), restored.toJson());
        assertNull(restored.get(basic, optional));
        assertEquals(2, restored.get(basic, tags).size());
        assertThrows(IllegalArgumentException.class, () -> document.set(basic, mode, "other"));
        assertFalse(DcTypes.NUMBER.accepts(new JsonPrimitive(Double.NaN)));
        assertEquals(JsonNull.INSTANCE, DcTypes.NULL.encode(null));
    }

    @Test void jsonConversionDoesNotExposeCallerOwnedValues() {
        JsonObject input = new JsonObject();
        input.addProperty("a", 1);
        DcDocument document = DcDocuments.fromJson(input);
        input.addProperty("a", 2);
        JsonObject exported = document.toJson();
        exported.addProperty("a", 3);
        assertEquals(1, document.root().get("a").getAsInt());
        DcCategory category = DcLanguages.create().registerMainCategory("basic", true);
        DcField<JsonObject> field = DcField.object("object");
        document.set(category, field, input);
        input.addProperty("a", 4);
        document.get(category, field).addProperty("a", 5);
        assertEquals(2, document.get(category, field).get("a").getAsInt());
    }

    @Test void writesPreserveExistingPosixPermissionsAndLeaveNoStagingFiles() throws Exception {
        assumeTrue(Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null);
        Path file = directory.resolve("sample.dc");
        Files.writeString(file, "[basic]\nvalue = 1\n");
        var permissions = PosixFilePermissions.fromString("rw-r-----");
        Files.setPosixFilePermissions(file, permissions);
        DcDocuments.write(file, DcDocuments.read(file).set("basic", "value", 2));
        assertEquals(permissions, Files.getPosixFilePermissions(file));
        assertEquals(2, DcDocuments.read(file).get("basic", "value").getAsInt());
        try (var files = Files.list(directory)) { assertEquals(List.of(file), files.toList()); }
    }

    @Test void writingSymbolicLinkUpdatesItsTargetAndKeepsLink() throws Exception {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        Path target = directory.resolve("target.dc");
        Path link = directory.resolve("link.dc");
        Files.writeString(target, "[basic]\nvalue = 1\n");
        Files.createSymbolicLink(link, target.getFileName());
        DcDocuments.write(link, DcDocuments.read(link).set("basic", "value", 2));
        assertTrue(Files.isSymbolicLink(link));
        assertEquals(2, DcDocuments.read(target).get("basic", "value").getAsInt());
    }

    @Test void writeFailureKeepsExistingDestinationAndCleansStagingFile() throws Exception {
        Path target = Files.createDirectory(directory.resolve("not-a-file.dc"));
        Path child = Files.writeString(target.resolve("existing"), "keep");
        assertThrows(java.io.IOException.class, () -> DcDocuments.write(target, DcDocuments.create()));
        assertEquals("keep", Files.readString(child));
        try (var files = Files.list(directory)) { assertEquals(List.of(target), files.toList()); }
    }
}
