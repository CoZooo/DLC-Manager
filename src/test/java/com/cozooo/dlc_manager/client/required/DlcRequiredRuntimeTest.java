package com.cozooo.dlc_manager.client.required;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.server.DlcServerDownloadService;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DlcRequiredRuntimeTest {
    @TempDir Path tempDir;
    private Path game;
    private Path root;

    @BeforeEach void setup() throws Exception {
        game = tempDir.resolve("game");
        root = game.resolve("DLC");
        Files.createDirectories(root.resolve("required"));
        PlatformServices.setProvider(new TestPlatformProvider(game, tempDir.resolve("config")));
        DlcConfig.setDlcRoot(root.toString());
    }

    @AfterEach void reset() {
        PlatformServices.setProvider(null);
        DlcConfig.setDlcRoot("DLC");
    }

    private DlcRequiredEntry resource() throws Exception {
        Path config = root.resolve("required/main.dc");
        Files.writeString(config, """
            identifier = "main"
            file_name = "main.jar"
            appliedTarget = "mods/main.jar"
            UserRemoved = true
            """);
        return DlcRequiredLoader.load(root).get(0);
    }

    @Test void legacySkipAndForceMarkersAndRemovalStateDoNotBypassInstallation() throws Exception {
        resource();
        assertTrue(com.cozooo.dlc_manager.core.DlcDcFormatUtil.read(root.resolve("required/main.dc")).get("UserRemoved").getAsBoolean());
        Files.writeString(root.resolve("required/SKIP"), "");
        Files.writeString(root.resolve("required/FORCE"), "forcerestart");
        assertTrue(DlcRequiredRuntime.hasMissingDownloadsForPrompt());
        assertEquals(List.of("main"), DlcRequiredRuntime.collectMissingDownloadsForPrompt().stream().map(DlcRequiredEntry::getIdentifier).toList());
        assertEquals(1, DlcServerDownloadService.collectMissingRequired(root).size());
    }

    @Test void cachedResourceMustStillBeAppliedAndDeletedTargetsAreCheckedAgain() throws Exception {
        resource();
        Files.writeString(root.resolve("required/main.jar"), "resource");
        assertEquals(1, DlcRequiredRuntime.collectMissingDownloadsForPrompt().size());
        var result = DlcServerDownloadService.downloadAndApplyRequired(root);
        assertNotNull(result);
        assertTrue(result.success);
        assertEquals("resource", Files.readString(game.resolve("mods/main.jar")));
        assertTrue(DlcRequiredRuntime.collectMissingDownloadsForPrompt().isEmpty());
        Files.delete(game.resolve("mods/main.jar"));
        assertEquals(1, DlcRequiredRuntime.collectMissingDownloadsForPrompt().size());
    }

    @Test void startupCheckIncludesAllConfiguredRoots() throws Exception {
        resource();
        Path second = game.resolve("other-DLC");
        Files.createDirectories(second.resolve("required"));
        Files.writeString(second.resolve("required/second.dc"), """
            identifier = "second"
            file_name = "second.jar"
            appliedTarget = "mods/second.jar"
            """);
        DlcConfig.setDlcRoot(root + "," + second);
        assertEquals(java.util.Set.of("main", "second"), new java.util.HashSet<>(
            DlcRequiredRuntime.collectMissingDownloadsForPrompt().stream().map(DlcRequiredEntry::getIdentifier).toList()));
    }

    @Test void installationFailureIsReportedAndStillBlocksContinuing() throws Exception {
        var entry = resource();
        Files.writeString(root.resolve("required/main.jar"), "resource");
        Files.writeString(game.resolve("mods"), "not a directory");
        assertThrows(IOException.class, () -> DlcRequiredService.installOne(entry));
        assertTrue(DlcRequiredRuntime.hasMissingDownloadsForPrompt());
        var result = DlcServerDownloadService.downloadAndApplyRequired(root);
        assertNotNull(result);
        assertFalse(result.success);
    }

    @Test void removedVisibilityFieldsCannotHideRequiredResourcesFromServer() throws Exception {
        Path config = root.resolve("required/main.dc");
        String contents = """
            identifier = "main"
            file_name = "main.jar"
            isHidden = true
            isInList = false
            RedirectUrl = "https://example.com"
            [DisplayName]
            en_us = "Ignored name"
            [Tip]
            en_us = "Ignored tip"
            """;
        Files.writeString(config, contents);

        var items = DlcServerDownloadService.collectRequiredItems(root, "zh_cn");
        assertEquals(1, items.size());
        assertEquals("main", items.get(0).name);
        assertFalse(items.get(0).hidden);
        assertTrue(items.get(0).displayNames.isEmpty());
        assertTrue(items.get(0).tips.isEmpty());
        assertEquals(contents, Files.readString(config));
    }

    @Test void writingDownloadedFileNameDoesNotGeneratePresentationFields() throws Exception {
        Path config = root.resolve("required/main.dc");
        Files.writeString(config, """
            identifier = "main"
            appliedTarget = "mods/main.jar"
            custom = "preserved"
            [dependencies.child]
            identifier = "child"
            file_name = "child.jar"
            """);
        DlcRequiredEntry entry = DlcRequiredLoader.loadSingle(config, root);
        DlcRequiredRuntime.updateFileNameKey(entry, "downloaded.jar");

        var saved = com.cozooo.dlc_manager.core.DlcDcFormatUtil.read(config);
        assertNotNull(saved);
        String written = Files.readString(config).toLowerCase(java.util.Locale.ROOT);
        assertTrue(List.of("isinlist", "ishidden", "displayname", "tip", "redirecturl").stream().noneMatch(written::contains));
        assertEquals("downloaded.jar", saved.get("file_name").getAsString());
        assertEquals("mods/main.jar", saved.get("appliedTarget").getAsString());
        assertEquals("preserved", saved.get("custom").getAsString());
        assertEquals("child.jar", saved.getAsJsonObject("dependencies").getAsJsonObject("child").get("file_name").getAsString());
    }
}
