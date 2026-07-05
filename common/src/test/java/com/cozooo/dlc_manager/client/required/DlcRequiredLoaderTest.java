package com.cozooo.dlc_manager.client.required;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcRequiredLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadReadsRequiredDcAndSkipsNotice() throws Exception {
        Path required = tempDir.resolve("required");
        Files.createDirectories(required);
        Files.writeString(required.resolve("main.dc"), """
            curseforge_projectid = "cf-project"
            curseforge_fileid = "cf-file"
            directUrl = [
              "https://example.com/a.jar",
              "https://example.com/b.jar"
            ]
            priority = ["directurl", "curseforge"]
            identifier = "main-id"
            file_name = "main.jar"
            appliedTarget = "mods/main.jar"
            RedirectUrl = "https://example.com"
            isHidden = true
            UserRemoved = true
            dependencies = ["dep-a", "dep-b"]

            [DisplayName]
            en_us = "Main"

            [tip]
            en_us = "Tip"
            """);
        Files.writeString(required.resolve("notice.dc"), """
            title = "Ignored"
            """);

        List<DlcRequiredEntry> entries = DlcRequiredLoader.load(tempDir);

        assertEquals(1, entries.size());
        DlcRequiredEntry entry = entries.get(0);
        assertEquals("main-id", entry.getIdentifier());
        assertEquals("main.jar", entry.getRequiredFileName());
        assertEquals(List.of("https://example.com/a.jar", "https://example.com/b.jar"), entry.getDirectUrls());
        assertEquals(List.of("directurl", "curseforge", "modrinth"), entry.getDownloadPriorityList());
        assertEquals("cf-project", entry.getCurseforgeProjectId());
        assertEquals("cf-file", entry.getCurseforgeFileId());
        assertEquals("mods/main.jar", entry.getAppliedTargetRaw());
        assertEquals("https://example.com", entry.getRedirectUrl());
        assertTrue(entry.isHidden());
        assertTrue(entry.isUserRemoved());
        assertEquals("Main", entry.getDisplayNames().get("en_us"));
        assertEquals("Tip", entry.getTips().get("en_us"));
        assertEquals(List.of("dep-a", "dep-b"), entry.getDependencies().stream().map(DlcRequiredDependency::getIdentifier).toList());
    }

    @Test
    void fromJsonObjectParsesNestedDependencyEntry() {
        JsonObject object = new JsonObject();
        object.addProperty("identifier", "owner");
        JsonArray dependencies = new JsonArray();
        JsonObject child = new JsonObject();
        child.addProperty("identifier", "child");
        child.addProperty("file_name", "child.jar");
        dependencies.add(child);
        object.add("dependencies", dependencies);

        DlcRequiredEntry entry = DlcRequiredLoader.fromJsonObject(object, tempDir.resolve("required"), tempDir.resolve("required").resolve("owner.dc"), null);

        assertEquals(1, entry.getDependencies().size());
        DlcRequiredDependency dependency = entry.getDependencies().get(0);
        assertEquals("child", dependency.getIdentifier());
        assertNotNull(dependency.getEntry());
        assertEquals("child.jar", dependency.getEntry().getRequiredFileName());
    }

    @Test
    void loadReadsRelationTableDependenciesAndBranches() throws Exception {
        Path required = tempDir.resolve("required");
        Files.createDirectories(required);
        Files.writeString(required.resolve("main.dc"), """
            identifier = "main"

            [dependencies]
            "mods/path.jar"
            "identifier_dep"

            [dependencies.remote_dep]
              [basic]
              identifier = "remote_dep"
              file_name = "remote.jar"
            """);

        List<DlcRequiredEntry> entries = DlcRequiredLoader.load(tempDir);

        assertEquals(1, entries.size());
        assertEquals(
            List.of("mods/path.jar", "identifier_dep", "remote_dep"),
            entries.get(0).getDependencies().stream().map(DlcRequiredDependency::getIdentifier).toList()
        );
        assertNotNull(entries.get(0).getDependencies().get(2).getEntry());
        assertEquals("remote.jar", entries.get(0).getDependencies().get(2).getEntry().getRequiredFileName());
    }

    @Test
    void loadSingleResolvesMissingExtensionToDcFile() throws Exception {
        Path required = tempDir.resolve("required");
        Files.createDirectories(required);
        Files.writeString(required.resolve("single.dc"), """
            identifier = "single"
            """);

        DlcRequiredEntry entry = DlcRequiredLoader.loadSingle(required.resolve("single"), tempDir);

        assertNotNull(entry);
        assertEquals("single", entry.getIdentifier());
        assertNull(DlcRequiredLoader.loadSingle(required.resolve("missing"), tempDir));
    }

    @Test
    void loadDoesNotMigrateLegacyKeys() throws Exception {
        Path required = tempDir.resolve("required");
        Files.createDirectories(required);
        Path config = required.resolve("legacy.dc");
        Files.writeString(config, """
            dlc_identifier = "legacy-id"
            dlc_name = "Legacy"
            """);

        List<DlcRequiredEntry> entries = DlcRequiredLoader.load(tempDir);

        assertEquals(1, entries.size());
        assertEquals("legacy", entries.get(0).getIdentifier());
        assertTrue(Files.readString(config).contains("dlc_identifier"));
        assertTrue(Files.readString(config).contains("dlc_name"));
    }
}
