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
