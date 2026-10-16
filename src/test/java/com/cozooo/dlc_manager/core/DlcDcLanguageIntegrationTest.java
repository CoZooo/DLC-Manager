package com.cozooo.dlc_manager.core;

import com.cozooo.dclanguage.DcDocuments;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DlcDcLanguageIntegrationTest {
    @TempDir
    Path directory;

    @Test
    void genericEditsPreserveCommentsUnknownValuesAndLineEndings() throws Exception {
        Path path = directory.resolve("history.dc");
        String source = "\uFEFF# history\r\nformat = 1\r\n\r\n[resources.\"id.with.dots\"]\r\n"
            + "source = 'DLC/downloaded/required/original.jar' # source\r\n"
            + "extension = {\"flags\": [true, null], \"revision\": 123456789012345678901234567890}\r\n";
        Files.writeString(path, source);

        JsonObject value = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
        assertNotNull(value);
        value.getAsJsonObject("resources").getAsJsonObject("id.with.dots")
            .addProperty("source", "DLC/downloaded/required/changed.jar");
        assertTrue(DlcDcConfigIO.write(path, value, DlcDcConfigIO.Layout.GENERIC));
        assertEquals(source.replace("original.jar", "changed.jar"), Files.readString(path));
        assertEquals(value, DcDocuments.read(path).toJson());
    }

    @Test
    void structuredNoOpRetainsSectionsWithoutAddingSyntheticFields() throws Exception {
        Path path = directory.resolve("required.dc");
        String source = """
            # user configuration
            [BaSiC]
            Identifier = 'required'
            file_name = "required.jar"
            custom = {"features": [1, true, null]}

            [download]
            directUrl = 'https://example.invalid/file.jar' # keep
            custom_provider = "private"

            [future.integration]
            enabled = true
            """;
        Files.writeString(path, source);
        JsonObject value = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.DLC_STRUCTURED);
        assertNotNull(value);
        assertEquals("private", value.get("custom_provider").getAsString());
        assertTrue(value.getAsJsonObject("future").getAsJsonObject("integration").get("enabled").getAsBoolean());
        assertTrue(DlcDcConfigIO.write(path, value, DlcDcConfigIO.Layout.DLC_STRUCTURED));
        assertEquals(source, Files.readString(path));

        value.addProperty("file_name", "updated.jar");
        assertTrue(DlcDcConfigIO.write(path, value, DlcDcConfigIO.Layout.DLC_STRUCTURED));
        assertEquals(source.replace("required.jar", "updated.jar"), Files.readString(path));
    }

    @Test
    void unchangedLegacyRelationsRemainInTheirOriginalSections() throws Exception {
        Path path = directory.resolve("legacy.dc");
        String source = """
            [basic]
            identifier = "legacy"
            dependencies = ["first", "second"] # preserved legacy form
            conflicts = "other"
            """;
        Files.writeString(path, source);
        JsonObject value = DlcDcFormatUtil.read(path);
        assertNotNull(value);
        value.addProperty("identifier", "changed");
        assertTrue(DlcDcFormatUtil.write(path, value));
        assertEquals(source.replace("\"legacy\"", "\"changed\""), Files.readString(path));
    }

    @Test
    void nestedDependencyEditsKeepQuotedIdentifiersAndUnrelatedBranches() throws Exception {
        Path path = directory.resolve("remote.dc");
        String source = """
            [basic]
            identifier = "root"

            [dependencies]
            "plain.dependency" # keep reference

            [dependencies."branch.with.dots".basic]
            identifier = "branch.with.dots"
            file_name = "before.jar" # branch target

            [dependencies."branch.with.dots".dependencies."nested/path".basic]
            identifier = "nested/path"

            [dependencies."branch.with.dots".download]
            directUrl = "https://example.invalid/branch.jar"

            [conflicts]
            "incompatible.mod"
            """;
        Files.writeString(path, source);
        JsonObject value = DlcDcFormatUtil.read(path);
        assertNotNull(value);
        JsonObject branch = value.getAsJsonObject("dependencies").getAsJsonObject("branch.with.dots");
        assertEquals("nested/path", branch.getAsJsonObject("dependencies").getAsJsonObject("nested/path").get("identifier").getAsString());
        branch.addProperty("file_name", "after.jar");
        assertTrue(DlcDcFormatUtil.write(path, value));
        assertEquals(source.replace("before.jar", "after.jar"), Files.readString(path));
        assertEquals(value, DlcDcFormatUtil.read(path));
    }

    @Test
    void newStructuredDocumentsRoundTripInlineObjectsAndQuotedDependencyNames() throws Exception {
        JsonObject object = new JsonObject();
        object.addProperty("identifier", "main");
        object.addProperty("directUrl", "https://example.invalid/main.jar");
        JsonObject custom = new JsonObject();
        custom.addProperty("setting", 3);
        object.add("custom", custom);
        JsonObject dependencies = new JsonObject();
        dependencies.addProperty("plain/name", "plain/name");
        JsonObject branch = new JsonObject();
        branch.addProperty("identifier", "branch.with.dots");
        branch.addProperty("file_name", "branch.jar");
        dependencies.add("branch.with.dots", branch);
        object.add("dependencies", dependencies);
        Path path = directory.resolve("created.dc");
        assertTrue(DlcDcFormatUtil.write(path, object));
        JsonObject stored = DcDocuments.read(path).toJson();
        assertEquals("main", stored.getAsJsonObject("basic").get("identifier").getAsString());
        assertEquals("branch.jar", stored.getAsJsonObject("dependencies").getAsJsonObject("branch.with.dots")
            .getAsJsonObject("basic").get("file_name").getAsString());
        assertEquals(custom, DlcDcFormatUtil.read(path).getAsJsonObject("custom"));
    }

    @Test
    void genericDeletionRemovesHistoryEntriesWithoutRestoringStaleValues() throws Exception {
        Path path = directory.resolve("state.dc");
        Files.writeString(path, """
            # records
            [items.first]
            enabled = true
            [items.second]
            enabled = false
            """);
        JsonObject value = DlcDcFormatUtil.readGeneric(path);
        value.getAsJsonObject("items").remove("first");
        assertTrue(DlcDcFormatUtil.writeGeneric(path, value));
        assertFalse(DlcDcFormatUtil.readGeneric(path).getAsJsonObject("items").has("first"));
        assertTrue(Files.readString(path).contains("# records"));
    }

    @Test
    void noticeRemainsGenericWhenRequestedThroughStructuredLayout() throws Exception {
        Path path = directory.resolve("notice.dc");
        String source = """
            [title]
            en_us = "Information"
            [subhead]
            en_us = "Details"
            [extension]
            enabled = true
            """;
        Files.writeString(path, source);
        JsonObject value = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.DLC_STRUCTURED);
        value.getAsJsonObject("subhead").addProperty("en_us", "Changed");
        assertTrue(DlcDcConfigIO.write(path, value, DlcDcConfigIO.Layout.DLC_STRUCTURED));
        assertEquals(source.replace("Details", "Changed"), Files.readString(path));
    }

    @Test
    void malformedDocumentsAreRejectedAndNeverOverwritten() throws Exception {
        for (DlcDcConfigIO.Layout layout : DlcDcConfigIO.Layout.values()) {
            Path path = directory.resolve(layout.name() + ".dc");
            String source = "[basic]\nidentifier = \"unterminated\n";
            Files.writeString(path, source);
            assertNull(DlcDcConfigIO.read(path, layout));
            JsonObject replacement = new JsonObject();
            replacement.addProperty("identifier", "replacement");
            assertFalse(DlcDcConfigIO.write(path, replacement, layout));
            assertEquals(source, Files.readString(path));
        }
    }

    @Test
    void structuredWritesDoNotLoseLargeIntegerChanges() throws Exception {
        Path path = directory.resolve("precise.dc");
        String source = "[basic]\nidentifier = \"precise\"\nfuture_count = 123456789012345678901234567889 # keep\n";
        Files.writeString(path, source);
        JsonObject value = DlcDcFormatUtil.read(path);
        value.addProperty("future_count", new java.math.BigInteger("123456789012345678901234567890"));
        assertTrue(DlcDcFormatUtil.write(path, value));
        assertEquals(source.replace("123456789012345678901234567889", "123456789012345678901234567890"), Files.readString(path));
    }

    @Test
    void legacyUnquotedDependencyIdentifiersRetainDotsAndPathSeparators() throws Exception {
        Path path = directory.resolve("legacy-identifiers.dc");
        String source = """
            [basic]
            identifier = "main"
            [dependencies.mods/library.jar] # first branch
              [basic]
              identifier = "library"
              file_name = "old.jar" # selected file
            [dependencies.mods/library.zip]
              [basic]
              identifier = "archive"
            """;
        Files.writeString(path, source);
        JsonObject value = DlcDcFormatUtil.read(path);
        assertNotNull(value);
        JsonObject dependencies = value.getAsJsonObject("dependencies");
        assertEquals("library", dependencies.getAsJsonObject("mods/library.jar").get("identifier").getAsString());
        assertEquals("archive", dependencies.getAsJsonObject("mods/library.zip").get("identifier").getAsString());
        dependencies.getAsJsonObject("mods/library.jar").addProperty("file_name", "new.jar");
        assertTrue(DlcDcFormatUtil.write(path, value));
        String written = Files.readString(path);
        assertTrue(written.contains("[dependencies.\"mods/library.jar\"] # first branch"));
        assertTrue(written.contains("file_name = \"new.jar\" # selected file"));
        assertEquals(value, DlcDcFormatUtil.read(path));
    }

    @Test
    void legacyNestedBranchesMayReuseChildIdentifiers() throws Exception {
        Path path = directory.resolve("legacy-nested.dc");
        Files.writeString(path, """
            [basic]
            identifier = "main"
            [dependencies.first.parent]
              [basic]
              identifier = "first"
              [dependencies.shared.child]
                [basic]
                identifier = "first-child"
            [dependencies.second.parent]
              [basic]
              identifier = "second"
              [dependencies.shared.child]
                [basic]
                identifier = "second-child"
            """);
        JsonObject value = DlcDcFormatUtil.read(path);
        assertNotNull(value);
        JsonObject first = value.getAsJsonObject("dependencies").getAsJsonObject("first.parent");
        JsonObject second = value.getAsJsonObject("dependencies").getAsJsonObject("second.parent");
        assertEquals("first-child", first.getAsJsonObject("dependencies").getAsJsonObject("shared.child").get("identifier").getAsString());
        assertEquals("second-child", second.getAsJsonObject("dependencies").getAsJsonObject("shared.child").get("identifier").getAsString());
        first.getAsJsonObject("dependencies").getAsJsonObject("shared.child").addProperty("file_name", "child.jar");
        assertTrue(DlcDcFormatUtil.write(path, value));
        assertEquals(value, DlcDcFormatUtil.read(path));
        assertNotNull(DcDocuments.read(path));
    }

    @Test
    void legacyHeaderCompatibilitySkipsMultilineTextAndPreservesUnknownBranchMaps() throws Exception {
        Path path = directory.resolve("legacy-text.dc");
        String source = """
            [basic]
            identifier = "main"
            description = \"\"\"
            [dependencies.not.a.branch]
              [basic]
              identifier = "text only"
            \"\"\"
            [dependencies.branch]
            [dependencies.branch.future]
            setting = "unknown"
            """;
        Files.writeString(path, source);
        JsonObject value = DlcDcFormatUtil.read(path);
        assertNotNull(value);
        assertEquals("unknown", value.getAsJsonObject("dependencies").getAsJsonObject("branch").getAsJsonObject("future").get("setting").getAsString());
        assertTrue(value.get("description").getAsString().contains("[dependencies.not.a.branch]"));
        assertTrue(DlcDcFormatUtil.write(path, value));
        assertEquals(source, Files.readString(path));
    }

    @Test
    void legacyColonBranchesRetainNestedDependencyContext() throws Exception {
        Path path = directory.resolve("legacy-colon.dc");
        Files.writeString(path, """
            [basic]
            identifier = "main"
            [dependencies]
              "first.parent":
                [basic]
                identifier = "first"
                [dependencies.shared.child]
                  [basic]
                  identifier = "nested"
            """);
        JsonObject value = DlcDcFormatUtil.read(path);
        assertNotNull(value);
        assertEquals("nested", value.getAsJsonObject("dependencies").getAsJsonObject("first.parent")
            .getAsJsonObject("dependencies").getAsJsonObject("shared.child").get("identifier").getAsString());
        assertTrue(DlcDcFormatUtil.write(path, value));
        assertEquals(value, DlcDcFormatUtil.read(path));
    }

    @Test
    void duplicateDefinitionsFailInsteadOfSilentlyChoosingOne() throws Exception {
        Path path = directory.resolve("duplicate.dc");
        Files.writeString(path, "[basic]\nidentifier = \"first\"\nidentifier = \"second\"\n");
        assertNull(DlcDcFormatUtil.read(path));
        assertNull(DlcDcFormatUtil.readGeneric(path));
    }
}
