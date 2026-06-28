package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcUpdateServiceTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void resetPlatform() {
        PlatformServices.setProvider(null);
        DlcConfig.setDlcRoot("DLC");
    }

    @Test
    void compareVersionsComparesNumericPartsNumerically() {
        assertTrue(DlcUpdateService.compareVersions("1.10.0", "1.2.9") > 0);
        assertTrue(DlcUpdateService.compareVersions("1.0.0", "1.0") > 0);
        assertTrue(DlcUpdateService.compareVersions("26.1.x", "1.21.1") > 0);
    }

    @Test
    void compareVersionsOrdersKnownQualifiersBeforeReleaseAndHotfixAfterRelease() {
        assertTrue(DlcUpdateService.compareVersions("1.0.0-alpha1", "1.0.0-beta1") < 0);
        assertTrue(DlcUpdateService.compareVersions("1.0.0-beta2", "1.0.0-rc1") < 0);
        assertTrue(DlcUpdateService.compareVersions("1.0.0-rc1", "1.0.0") < 0);
        assertTrue(DlcUpdateService.compareVersions("1.0.0-hotfix1", "1.0.0") > 0);
        assertTrue(DlcUpdateService.compareVersions("1.0.0-build1", "1.0.0-hotfix1") > 0);
    }

    @Test
    void compareVersionsUsesQualifierNumber() {
        assertTrue(DlcUpdateService.compareVersions("1.0.0-beta2", "1.0.0-beta10") < 0);
        assertEquals(0, DlcUpdateService.compareVersions("1.0.0-rc1", "1.0.0-rc1"));
    }

    @Test
    void rollbackMarkerIsActiveWhenMarkerTargetDiffersFromCurrentVersion() {
        assertTrue(!DlcUpdateService.isRollbackMarkerActive("1.2.0+1.0.5", "1.2.0", "1.0.5"));
        assertTrue(!DlcUpdateService.isRollbackMarkerActive("1.0.5", "1.2.0", "1.0.5"));
        assertTrue(DlcUpdateService.isRollbackMarkerActive("1.1.0+1.0.5", "1.2.0", "1.0.5"));
        assertTrue(DlcUpdateService.isRollbackMarkerActive("1.0.4", "1.2.0", "1.0.5"));
    }

    @Test
    void fileDeleteSupportsSingleSegmentWildcard() throws Exception {
        Path gameDir = tempDir.resolve("game");
        Path dlcRoot = gameDir.resolve("DLC");
        PlatformServices.setProvider(new TestPlatformProvider(gameDir, tempDir.resolve("config")));
        DlcConfig.setDlcRoot(dlcRoot.toString());
        Files.createDirectories(gameDir.resolve("mods").resolve("a"));
        Files.createDirectories(gameDir.resolve("mods").resolve("b"));
        Files.writeString(gameDir.resolve("mods").resolve("a").resolve("target.txt"), "a");
        Files.writeString(gameDir.resolve("mods").resolve("b").resolve("target.txt"), "b");
        Files.createDirectories(dlcRoot.resolve("info"));
        Files.writeString(dlcRoot.resolve("info").resolve("version.dc"), """
            base = "1.0.0"
            dlc = "1.0.0"
            """);
        Path zip = tempDir.resolve("Client_1.0.0_1.0.0-1.0.1_1.0.1.zip");
        writeZip(zip, new Entry("update.dc", """
            [version]
            base = "1.0.0"
            dlc_old = "1.0.0"
            dlc_new = "1.0.1"

            [file]
              [del]
                "mods/*/target.txt"
            """));

        DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(zip);
        DlcUpdateService.UpdateApplyResult result = DlcUpdateService.applyUpdate(pkg, DlcUpdateVersion.load(dlcRoot), false);

        assertTrue(result.isSuccess());
        assertFalse(Files.exists(gameDir.resolve("mods").resolve("a").resolve("target.txt")));
        assertFalse(Files.exists(gameDir.resolve("mods").resolve("b").resolve("target.txt")));
        DlcUpdateMeta meta = DlcUpdateService.loadMetaForVersion("1.0.0", "1.0.1");
        assertTrue(meta != null);
        assertEquals(Set.of("mods/a/target.txt", "mods/b/target.txt"), Set.copyOf(meta.getRestorePaths()));
    }

    @Test
    void rolledBackUpdateCanBeImportedAndReapplied() throws Exception {
        Path gameDir = tempDir.resolve("game");
        Path dlcRoot = gameDir.resolve("DLC");
        PlatformServices.setProvider(new TestPlatformProvider(gameDir, tempDir.resolve("config")));
        DlcConfig.setDlcRoot(dlcRoot.toString());
        Files.createDirectories(gameDir.resolve("mods"));
        Files.writeString(gameDir.resolve("mods").resolve("example.jar"), "1.0.7");
        Files.createDirectories(dlcRoot.resolve("info"));
        Files.writeString(dlcRoot.resolve("info").resolve("version.dc"), """
            base = "3.13.8"
            dlc = "1.0.7"
            """);
        Path zip = tempDir.resolve("Universal_3.13.8_1.0.7-3.13.8_1.0.8.zip");
        writeZip(zip,
            new Entry("update.dc", """
                [version]
                platform = "universal"
                base = "3.13.8"
                dlc = "1.0.7"
                dlc_new = "1.0.8"

                [file]
                  [add]
                    "mods/example.jar":
                      appliedTarget = "mods/example.jar"
                      override = true
                """),
            new Entry("mods/example.jar", "1.0.8"));

        DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(zip);
        DlcUpdateService.UpdateApplyResult applyResult = DlcUpdateService.applyUpdate(pkg, DlcUpdateVersion.load(dlcRoot), false);
        assertTrue(applyResult.isSuccess());
        assertEquals("1.0.8", DlcUpdateVersion.load(dlcRoot).getDlc());

        DlcUpdateService.UpdateRollbackResult rollbackResult = DlcUpdateService.rollbackTo("3.13.8+1.0.7");
        assertTrue(rollbackResult.isSuccess());
        DlcUpdateVersion rolledBack = DlcUpdateVersion.load(dlcRoot);
        assertEquals("1.0.7", rolledBack.getDlc());
        assertEquals("3.13.8+1.0.8", rolledBack.getRollbackFrom());
        assertEquals("1.0.7", Files.readString(gameDir.resolve("mods").resolve("example.jar")));

        assertTrue(DlcUpdateService.importUpdatePackage(zip));
        DlcUpdateService.UpdateCheckResult check = DlcUpdateService.checkForUpdates(true);
        assertTrue(check.hasUpdates());
        DlcUpdateService.UpdateApplyResult reapplyResult = DlcUpdateService.applyUpdates(check.getUpdates(), false);
        assertTrue(reapplyResult.isSuccess());
        DlcUpdateVersion reapplied = DlcUpdateVersion.load(dlcRoot);
        assertEquals("3.13.8", reapplied.getBase());
        assertEquals("1.0.8", reapplied.getDlc());
        assertTrue(reapplied.getRollbackFrom() == null || reapplied.getRollbackFrom().isBlank());
        assertEquals("1.0.8", Files.readString(gameDir.resolve("mods").resolve("example.jar")));
    }

    @Test
    void missingTargetsIncludesModifyDeleteAndMoveSources() throws Exception {
        Path gameDir = tempDir.resolve("game");
        Path dlcRoot = gameDir.resolve("DLC");
        PlatformServices.setProvider(new TestPlatformProvider(gameDir, tempDir.resolve("config")));
        DlcConfig.setDlcRoot(dlcRoot.toString());
        Files.createDirectories(dlcRoot.resolve("info"));
        Files.writeString(dlcRoot.resolve("info").resolve("version.dc"), """
            base = "1.0.0"
            dlc = "1.0.0"
            """);
        Path zip = tempDir.resolve("Client_1.0.0_1.0.0-1.0.1_1.0.1.zip");
        writeZip(zip, new Entry("update.dc", """
            [version]
            base = "1.0.0"
            dlc_old = "1.0.0"
            dlc_new = "1.0.1"

            [file]
              [modify]
                "config/missing-modify.json":
                  find = "{}"
                  replace = "{}"
                  which = -1
              [del]
                "config/missing-delete.json"
              [move]
                "config/missing-move.json" = "config/moved.json"
            """));

        DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(zip);
        DlcUpdateService.UpdateApplyResult result = DlcUpdateService.applyUpdate(pkg, DlcUpdateVersion.load(dlcRoot), false);

        assertTrue(result.isMissing());
        assertEquals(List.of(
            "config/missing-modify.json",
            "config/missing-delete.json",
            "config/missing-move.json"
        ), result.getMissingPaths());
    }

    @Test
    void missingTargetsDeduplicateAcrossModifyDeleteAndMove() throws Exception {
        Path gameDir = tempDir.resolve("game");
        Path dlcRoot = gameDir.resolve("DLC");
        PlatformServices.setProvider(new TestPlatformProvider(gameDir, tempDir.resolve("config")));
        DlcConfig.setDlcRoot(dlcRoot.toString());
        Files.createDirectories(dlcRoot.resolve("info"));
        Files.writeString(dlcRoot.resolve("info").resolve("version.dc"), """
            base = "1.0.0"
            dlc = "1.0.0"
            """);
        Path zip = tempDir.resolve("Client_1.0.0_1.0.0-1.0.1_1.0.1.zip");
        writeZip(zip, new Entry("update.dc", """
            [version]
            base = "1.0.0"
            dlc_old = "1.0.0"
            dlc_new = "1.0.1"

            [file]
              [modify]
                "config/shared.json":
                  find = "{}"
                  replace = "{}"
                  which = -1
              [del]
                "config/shared.json"
              [move]
                "config/shared.json" = "config/moved.json"
            """));

        DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(zip);
        DlcUpdateService.UpdateApplyResult result = DlcUpdateService.applyUpdate(pkg, DlcUpdateVersion.load(dlcRoot), false);

        assertTrue(result.isMissing());
        assertEquals(List.of("config/shared.json"), result.getMissingPaths());
    }

    private static void writeZip(Path zip, Entry... entries) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Entry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.name);
                out.putNextEntry(zipEntry);
                out.write(entry.content.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    private record Entry(String name, String content) {
    }
}
