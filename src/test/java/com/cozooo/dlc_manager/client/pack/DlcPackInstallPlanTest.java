package com.cozooo.dlc_manager.client.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPackInstallPlanTest {
    private static final List<String> GAME_ROOT_FORBIDDEN_PATHS = List.of(
        "config/dlc_manager/config.dc"
    );
    private static final List<String> DLC_ROOT_FORBIDDEN_PATHS = List.of(
        "tmp/pack/a",
        "info/version.dc",
        "info/notice.dc",
        "required/SKIP",
        "update/file.zip",
        "ShownOnFirstRun",
        "SKIP"
    );

    @TempDir
    Path tempDir;

    @Test
    void buildScansInstallFilesSkipsMetadataAndReportsForbiddenPaths() throws Exception {
        Path root = tempDir.resolve("pack");
        writePack(root.resolve("metadata").resolve("pack.dc"));
        Files.createDirectories(root.resolve("mods"));
        Files.writeString(root.resolve("mods").resolve("a.jar"), "jar");
        Files.createDirectories(root.resolve("config").resolve("dlc_manager"));
        Files.writeString(root.resolve("config").resolve("dlc_manager").resolve("config.dc"), "blocked");
        Files.createDirectories(root.resolve("DLC").resolve("update"));
        Files.writeString(root.resolve("DLC").resolve("update").resolve("u.zip"), "blocked");

        DlcPackPackage.Result parsed = DlcPackPackage.inspect(root);
        DlcPackInstallPlan plan = DlcPackInstallPlan.build(parsed.pack, root, root.resolve("DLC"));

        assertTrue(plan.getValid());
        assertEquals(Set.of("config/dlc_manager/config.dc", "DLC/update/u.zip"), new HashSet<>(plan.forbiddenPaths));
        assertEquals(Set.of("mods/a.jar"), new HashSet<>(plan.files.stream().map(entry -> entry.relativePath).toList()));
    }

    @Test
    void forbiddenPathMatcherCoversConfiguredInternalPaths() {
        Path instanceRoot = Path.of("/instance");
        Path dlcRoot = instanceRoot.resolve("DLC");
        for (String relative : GAME_ROOT_FORBIDDEN_PATHS) {
            assertTrue(DlcPackInstallPlan.isForbiddenPath(relative), relative);
        }
        for (String relative : DLC_ROOT_FORBIDDEN_PATHS) {
            assertTrue(DlcPackInstallPlan.isForbiddenPath("DLC/" + relative, instanceRoot, dlcRoot), relative);
        }
        assertFalse(DlcPackInstallPlan.isForbiddenPath("mods/a.jar"));
    }

    @Test
    void mergeKeepsBundledFilesBeforeManifestDownloads() throws Exception {
        Path root = tempDir.resolve("pack");
        writePack(root.resolve("metadata").resolve("pack.dc"));
        Files.createDirectories(root.resolve("mods"));
        Files.writeString(root.resolve("mods").resolve("a.jar"), "bundled");
        DlcPackPackage.Result parsed = DlcPackPackage.inspect(root);
        DlcPackInstallPlan base = DlcPackInstallPlan.build(parsed.pack);

        DlcPackInstallPlan merged = DlcPackInstallPlan.merge(
            base,
            List.of(
                new DlcPackInstallPlan.FileEntry(tempDir.resolve("downloaded-a.jar"), "mods/a.jar"),
                new DlcPackInstallPlan.FileEntry(tempDir.resolve("downloaded-b.jar"), "mods/b.jar")
            )
        );

        assertEquals(Set.of("mods/a.jar", "mods/b.jar"), new HashSet<>(merged.files.stream().map(entry -> entry.relativePath).toList()));
        assertEquals(root.resolve("mods").resolve("a.jar").toAbsolutePath().normalize(), merged.files.stream()
            .filter(entry -> entry.relativePath.equals("mods/a.jar"))
            .findFirst()
            .orElseThrow()
            .source
            .toAbsolutePath()
            .normalize());
    }

    private static void writePack(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
            [basic]
            identifier = "Example"
            version = "1.0.0"
            """);
    }
}
