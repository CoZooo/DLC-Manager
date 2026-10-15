package com.cozooo.dlc_manager.client.required;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.cozooo.dlc_manager.core.DlcResourceHistory;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcRequiredInstallerTest {
    @TempDir Path game;
    private Path root;

    @BeforeEach void setup() throws Exception {
        root = game.resolve("DLC");
        Files.createDirectories(root.resolve("required"));
        PlatformServices.setProvider(new TestPlatformProvider(game, game.resolve("config")));
        DlcConfig.setDlcRoot("DLC");
    }

    @AfterEach void reset() {
        PlatformServices.setProvider(null);
        DlcConfig.setDlcRoot("DLC");
    }

    private DlcRequiredEntry resource(String name, String target, String option) throws Exception {
        Path config = root.resolve("required/main.dc");
        Files.writeString(config, "identifier='main'\nfile_name='" + name + "'\nappliedTarget='" + target + "'\n" + option);
        return DlcRequiredLoader.loadSingle(config, root);
    }

    private void zip(String name, Map<String, String> entries) throws Exception {
        try (var out = new ZipOutputStream(Files.newOutputStream(root.resolve("required").resolve(name)))) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    @Test void absentOrFalseUnzipCopiesZipWithoutExtraction() throws Exception {
        zip("bundle.zip", Map.of("nested/file.cfg", "content"));
        for (String option : List.of("", "unzip=false\n")) {
            var entry = resource("bundle.zip", "packs/", option);
            assertFalse(entry.isUnzip());
            DlcRequiredService.installOne(entry);
            assertArrayEquals(Files.readAllBytes(root.resolve("required/bundle.zip")), Files.readAllBytes(game.resolve("packs/bundle.zip")));
            assertFalse(Files.exists(game.resolve("packs/nested/file.cfg")));
            assertFalse(DlcRequiredRuntime.entryNeedsApply(entry));
        }
    }

    @Test void directoryTargetsAndExplicitNamesUseTheSameRulesForEveryFileType() throws Exception {
        Files.writeString(root.resolve("required/source.txt"), "text");
        var entry = resource("source.txt", "config,renamed/file.cfg,dotted.zip/", "unzip=true\n");
        assertFalse(entry.shouldUnzip("source.txt"));
        DlcRequiredService.installOne(entry);
        for (String target : List.of("config/source.txt", "renamed/file.cfg", "dotted.zip/source.txt")) assertEquals("text", Files.readString(game.resolve(target)));
        assertFalse(DlcRequiredRuntime.entryNeedsApply(entry));
    }

    @Test void explicitJarNameReplacesAnExistingDirectoryAndUnzipDoesNotAffectJars() throws Exception {
        Files.writeString(root.resolve("required/main.jar"), "jar");
        Files.createDirectories(game.resolve("mods/main.jar"));
        Files.writeString(game.resolve("mods/main.jar/old"), "old directory");
        var entry = resource("main.jar", "mods/main.jar", "unzip=true\n");
        DlcRequiredService.installOne(entry);
        assertEquals("jar", Files.readString(game.resolve("mods/main.jar")));
        assertFalse(DlcRequiredRuntime.entryNeedsApply(entry));
        assertTrue(DlcResourceHistory.restore(root, "required", entry.getHistoryKey(), true));
        assertEquals("old directory", Files.readString(game.resolve("mods/main.jar/old")));
    }

    @Test void unzipMergesMembersAndRecoveryRestoresOnlyOwnedFiles() throws Exception {
        zip("bundle.ZIP", Map.of("nested/file.cfg", "new", "added.txt", "added", "empty/", ""));
        Files.createDirectories(game.resolve("config/nested"));
        Files.writeString(game.resolve("config/nested/file.cfg"), "original");
        Files.writeString(game.resolve("config/unrelated.txt"), "keep");
        var entry = resource("bundle.ZIP", "config/", "unzip=true\n");
        assertTrue(DlcRequiredService.installOne(entry));
        assertEquals("new", Files.readString(game.resolve("config/nested/file.cfg")));
        assertEquals("added", Files.readString(game.resolve("config/added.txt")));
        assertTrue(Files.isDirectory(game.resolve("config/empty")));
        assertFalse(Files.exists(game.resolve("config/bundle.ZIP")));
        assertFalse(DlcRequiredRuntime.entryNeedsApply(entry));
        assertFalse(DlcRequiredService.installOne(entry));
        Files.writeString(game.resolve("config/nested/file.cfg"), "changed");
        assertTrue(DlcRequiredRuntime.entryNeedsApply(entry));
        DlcRequiredService.installOne(entry);
        assertTrue(DlcResourceHistory.restore(root, "required", entry.getHistoryKey(), true));
        assertEquals("original", Files.readString(game.resolve("config/nested/file.cfg")));
        assertEquals("keep", Files.readString(game.resolve("config/unrelated.txt")));
        assertFalse(Files.exists(game.resolve("config/added.txt")));
        var history = DlcDcFormatUtil.readGeneric(root.resolve("tmp/required/history.dc"));
        var item = history.getAsJsonObject("resources").getAsJsonObject(entry.getHistoryKey());
        assertEquals("DLC/required/bundle.ZIP", item.get("source").getAsString());
    }

    @Test void unzipControlsExtractionRegardlessOfTargetSuffixAndSupportsMultipleDirectories() throws Exception {
        zip("bundle.zip", Map.of("main.jar", "jar"));
        var entry = resource("bundle.zip", "first.zip,second/", "unzip=true\n");
        DlcRequiredService.installOne(entry);
        assertEquals("jar", Files.readString(game.resolve("first.zip/main.jar")));
        assertEquals("jar", Files.readString(game.resolve("second/main.jar")));
        Files.delete(game.resolve("second/main.jar"));
        assertTrue(DlcRequiredRuntime.entryNeedsApply(entry));
    }

    @Test void invalidArchivePathsAreRejectedBeforeAnyTargetChanges() throws Exception {
        zip("bundle.zip", Map.of("safe.txt", "new", "../escape.txt", "unsafe"));
        Files.createDirectories(game.resolve("config"));
        Files.writeString(game.resolve("config/safe.txt"), "original");
        var entry = resource("bundle.zip", "config/", "unzip=true\n");
        assertThrows(IOException.class, () -> DlcRequiredService.installOne(entry));
        assertEquals("original", Files.readString(game.resolve("config/safe.txt")));
        assertFalse(Files.exists(game.resolve("escape.txt")));
    }

    @Test void existingSymlinkCannotRedirectAnExtractedMember() throws Exception {
        zip("bundle.zip", Map.of("nested/file.txt", "new"));
        Files.createDirectories(game.resolve("config"));
        Files.createDirectories(game.resolve("outside"));
        Files.createSymbolicLink(game.resolve("config/nested"), game.resolve("outside"));
        var entry = resource("bundle.zip", "config/", "unzip=true\n");
        assertThrows(IOException.class, () -> DlcRequiredService.installOne(entry));
        assertFalse(Files.exists(game.resolve("outside/file.txt")));
    }

    @Test void nestedDependencyParsesUnzipAndFileNameUpdatesPreserveItAndComments() throws Exception {
        Path config = root.resolve("required/main.dc");
        Files.writeString(config, """
            [basic]
            identifier='main'
            file_name='main.zip'
            appliedTarget='config/'
            # keep unzip setting
            unzip=true
            future='keep'
            [dependencies.child.basic]
            identifier='child'
            file_name='child.zip'
            appliedTarget='mods/'
            unzip=true
            """);
        var entry = DlcRequiredLoader.loadSingle(config, root);
        assertTrue(entry.isUnzip());
        assertTrue(entry.getDependencies().get(0).getEntry().isUnzip());
        DlcRequiredRuntime.updateFileNameKey(entry, "renamed.zip");
        var reloaded = DlcRequiredLoader.loadSingle(config, root);
        assertTrue(reloaded.isUnzip());
        assertTrue(reloaded.getDependencies().get(0).getEntry().isUnzip());
        assertTrue(Files.readString(config).contains("# keep unzip setting"));
        assertEquals("keep", DlcDcFormatUtil.read(config).get("future").getAsString());
    }

    @Test void modeNameStillChecksExtractedMembersWithoutRequiringTheirContentHash() throws Exception {
        zip("bundle.zip", Map.of("file.txt", "expected"));
        var entry = resource("bundle.zip", "config/", "unzip=true\nmode='name'\n");
        DlcRequiredService.installOne(entry);
        Files.writeString(game.resolve("config/file.txt"), "modified");
        assertFalse(DlcRequiredRuntime.entryNeedsApply(entry));
        Files.delete(game.resolve("config/file.txt"));
        assertTrue(DlcRequiredRuntime.entryNeedsApply(entry));
    }
    @Test void failedPendingUpdateCanUndoExtractedFilesAndTheirHistory() throws Exception {
        zip("bundle.zip", Map.of("existing.txt", "new", "added.txt", "added"));
        Files.createDirectories(game.resolve("config"));
        Files.writeString(game.resolve("config/existing.txt"), "original");
        var entry = resource("bundle.zip", "config/", "unzip=true\n");
        try (var journal = com.cozooo.dlc_manager.client.update.DlcUpdateJournal.open(game, root.resolve("tmp/update/jobs/test"))) {
            DlcRequiredService.installOne(entry);
            assertEquals("new", Files.readString(game.resolve("config/existing.txt")));
            journal.rollback();
        }
        assertEquals("original", Files.readString(game.resolve("config/existing.txt")));
        assertFalse(Files.exists(game.resolve("config/added.txt")));
        assertFalse(Files.exists(root.resolve("tmp/required/history.dc")));
    }

    @Test void serverUninstallRestoresReplacedMembersWithoutDeletingOtherFiles() throws Exception {
        zip("bundle.zip", Map.of("existing.txt", "new", "added.txt", "added"));
        Files.createDirectories(game.resolve("config"));
        Files.writeString(game.resolve("config/existing.txt"), "original");
        Files.writeString(game.resolve("config/unrelated.txt"), "keep");
        var entry = resource("bundle.zip", "config/", "unzip=true\n");
        DlcRequiredService.installOne(entry);
        assertEquals(1, com.cozooo.dlc_manager.server.DlcServerDownloadService.uninstallRequired(root, List.of("main")));
        assertEquals("original", Files.readString(game.resolve("config/existing.txt")));
        assertEquals("keep", Files.readString(game.resolve("config/unrelated.txt")));
        assertFalse(Files.exists(game.resolve("config/added.txt")));
    }

    @Test void evenEmptyZipCannotReplaceAnExistingFileWhenItsTargetIsADirectory() throws Exception {
        zip("bundle.zip", Map.of());
        Files.writeString(game.resolve("config"), "keep");
        var entry = resource("bundle.zip", "config/", "unzip=true\n");
        assertThrows(IOException.class, () -> DlcRequiredService.installOne(entry));
        assertEquals("keep", Files.readString(game.resolve("config")));
    }

    @Test void currentDirectoryTargetKeepsSourceFileName() throws Exception {
        Files.writeString(root.resolve("required/main.jar"), "jar");
        var entry = resource("main.jar", ".", "");
        DlcRequiredService.installOne(entry);
        assertEquals("jar", Files.readString(game.resolve("main.jar")));
        assertFalse(DlcRequiredRuntime.entryNeedsApply(entry));
    }

    @Test void aFileTargetCannotReplaceADirectoryContainingItsOwnDownload() throws Exception {
        Files.createDirectories(game.resolve("container.zip/required"));
        Path source = game.resolve("container.zip/required/main.zip");
        Files.writeString(source, "keep");
        Path config = game.resolve("container.zip/required/main.dc");
        Files.writeString(config, "identifier='main'\nfile_name='main.zip'\nappliedTarget='container.zip'\n");
        var entry = DlcRequiredLoader.loadSingle(config, game.resolve("container.zip"));
        assertThrows(IOException.class, () -> DlcRequiredService.installOne(entry));
        assertEquals("keep", Files.readString(source));
    }

}
