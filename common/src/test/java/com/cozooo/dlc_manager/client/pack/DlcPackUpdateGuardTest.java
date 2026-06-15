package com.cozooo.dlc_manager.client.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPackUpdateGuardTest {
    @TempDir
    Path tempDir;

    @Test
    void classifiesInstalledPacksAgainstTargetBaseAndDlcVersions() throws Exception {
        Path dlcRoot = tempDir.resolve("DLC");
        writeInstalledPack(dlcRoot, "Compatible", "1.0.x", "1.0.0~1.0.5");
        writeInstalledPack(dlcRoot, "Blocked", "2.0.x", "any");

        DlcPackUpdateGuard.CheckResult result = DlcPackUpdateGuard.check(dlcRoot, "1.0.3", "1.0.4");

        assertTrue(result.hasInstalledPacks());
        assertTrue(result.hasIncompatiblePacks());
        assertEquals(2, result.packs.size());
        assertEquals(List.of("Compatible"), result.compatiblePacks.stream().map(DlcPackUpdateGuard.PackStatus::identifier).toList());
        assertEquals(List.of("Blocked"), result.incompatiblePacks.stream().map(DlcPackUpdateGuard.PackStatus::identifier).toList());
        assertTrue(result.incompatiblePacks.get(0).reasons.get(0).contains("base 1.0.3"));
    }

    @Test
    void treatsMissingArchivedPackMetadataAsIncompatible() {
        Path dlcRoot = tempDir.resolve("DLC");
        Path packDir = dlcRoot.resolve("tmp").resolve("pack").resolve("MissingMetadata");
        try {
            Files.createDirectories(packDir);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        new DlcPackMeta("MissingMetadata", "1.0.0", "STANDARD", Instant.now().toString(), List.of()).save(packDir.resolve("meta.dc"));

        DlcPackUpdateGuard.CheckResult result = DlcPackUpdateGuard.check(dlcRoot, "1.0.0", "1.0.0");

        assertEquals(1, result.incompatiblePacks.size());
        assertEquals("MissingMetadata", result.incompatiblePacks.get(0).identifier());
        assertEquals(List.of("missing metadata/pack.dc"), result.incompatiblePacks.get(0).reasons);
    }

    @Test
    void emptyPackRootProducesEmptyResult() {
        DlcPackUpdateGuard.CheckResult result = DlcPackUpdateGuard.check(tempDir.resolve("DLC"), "1.0.0", "1.0.0");

        assertFalse(result.hasInstalledPacks());
        assertFalse(result.hasIncompatiblePacks());
    }

    private static void writeInstalledPack(Path dlcRoot, String identifier, String baseRule, String dlcRule) throws Exception {
        Path packDir = dlcRoot.resolve("tmp").resolve("pack").resolve(identifier);
        Path metadataDir = packDir.resolve("metadata");
        Files.createDirectories(metadataDir);
        Files.writeString(metadataDir.resolve("pack.dc"), """
            [basic]
            identifier = "%s"
            version = "1.0.0"

            [modpack]
            base = "%s"
            dlc = "%s"
            """.formatted(identifier, baseRule, dlcRule));
        DlcPackMeta meta = new DlcPackMeta(identifier, "1.0.0", "STANDARD", Instant.now().toString(), List.of("mods/" + identifier + ".jar"));
        meta.save(packDir.resolve("meta.dc"));
    }
}
