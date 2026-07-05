package com.cozooo.dlc_manager.client.remote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cozooo.dlc_manager.core.DlcType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcRemoteLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadReadsRemoteDcAndLinksIdentifierDependencies() throws Exception {
        Path remote = tempDir.resolve("remote");
        Files.createDirectories(remote);
        Files.writeString(remote.resolve("main.dc"), """
            modrinth_projectid = "mr-project"
            modrinth_versionid = "mr-version"
            directUrl = ["https://example.com/a.jar", "https://example.com/b.jar"]
            priority = ["modrinth", "directurl"]
            identifier = "main"
            type = "mod"
            appliedTarget = "mods/main.jar"
            file_name = "main.jar"
            isHidden = false
            AllowEnableByAll = false
            dependencies = ["dep"]
            conflicts = ["conflict-a", "conflict-b"]
            alarm = true

            [DisplayName]
            en_us = "Main"

            [tip]
            en_us = "Tip"
            """);
        Files.writeString(remote.resolve("dep.dc"), """
            identifier = "dep"
            directUrl = "https://example.com/dep.jar"
            file_name = "dep.jar"
            """);
        Files.createDirectories(remote.resolve("redirect"));
        Files.writeString(remote.resolve("redirect").resolve("ignored.dc"), """
            identifier = "ignored"
            """);

        List<DlcRemoteEntry> entries = DlcRemoteLoader.load(tempDir);

        assertEquals(2, entries.size());
        DlcRemoteEntry main = entries.stream().filter(entry -> "main".equals(entry.getDlcIdentifier())).findFirst().orElseThrow();
        assertEquals(List.of("https://example.com/a.jar", "https://example.com/b.jar"), main.getDirectUrls());
        assertEquals(List.of("modrinth", "directurl", "curseforge"), main.getDownloadPriorityList());
        assertEquals("mr-project", main.getModrinthProjectId());
        assertEquals("mr-version", main.getModrinthVersionId());
        assertEquals("mods/main.jar", main.getAppliedTargetRaw());
        assertEquals("main.jar", main.getFileNameHint());
        assertFalse(main.isAllowEnableByAll());
        assertTrue(main.isAlarm());
        assertEquals(List.of("dep"), main.getDependencyNames());
        assertEquals(List.of("conflict-a", "conflict-b"), main.getConflictNames());
        assertEquals("Main", main.getDisplayNames().get("en_us"));
        assertEquals("Tip", main.getTips().get("en_us"));
        assertEquals(1, main.getRemoteDependencies().size());
        assertEquals("dep", main.getRemoteDependencies().get(0).getDlcIdentifier());
    }

    @Test
    void loadSingleResolvesLegacyInfoRemotePathToRemoteRoot() throws Exception {
        Path remote = tempDir.resolve("remote");
        Files.createDirectories(remote);
        Files.writeString(remote.resolve("single.dc"), """
            identifier = "single"
            file_name = "single.jar"
            """);

        DlcRemoteEntry entry = DlcRemoteLoader.loadSingle(tempDir.resolve("info").resolve("remote").resolve("single.dc"), tempDir);

        assertNotNull(entry);
        assertEquals("single", entry.getDlcIdentifier());
        assertEquals("single.jar", entry.getFileNameHint());
    }

    @Test
    void loadMovesLegacyInfoRemoteDirectoryIntoRemoteRoot() throws Exception {
        Path legacy = tempDir.resolve("info").resolve("remote");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("legacy.dc"), """
            identifier = "legacy"
            """);

        List<DlcRemoteEntry> entries = DlcRemoteLoader.load(tempDir);

        assertEquals(1, entries.size());
        assertEquals("legacy", entries.get(0).getDlcIdentifier());
        assertTrue(Files.exists(tempDir.resolve("remote").resolve("legacy.dc")));
    }

    @Test
    void dlcPackRemoteIsHiddenAndTargetsPackDirectory() throws Exception {
        Path remote = tempDir.resolve("remote");
        Files.createDirectories(remote);
        Files.writeString(remote.resolve("pack.dc"), """
            [download]
            directUrl = "https://example.com/pack.zip"

            [basic]
            type = "dlcpack"
            file_name = "pack.zip"
            isHidden = false
            """);

        DlcRemoteEntry entry = DlcRemoteLoader.loadSingle(remote.resolve("pack.dc"), tempDir);

        assertNotNull(entry);
        assertEquals(DlcType.DLC_PACK, entry.getType());
        assertTrue(entry.isHidden());
        assertEquals(Path.of("pack", "pack.zip"), tempDir.relativize(entry.getTargetPath()));
    }
}
