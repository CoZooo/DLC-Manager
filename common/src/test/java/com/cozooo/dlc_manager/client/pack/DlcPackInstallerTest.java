package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPackInstallerTest {
    @TempDir
    Path tempDir;

    @org.junit.jupiter.api.AfterEach
    void clearRuntimeState() {
        DlcRuntimeState.updateItemIndex(java.util.List.of());
        DlcPresetRuntimeState.clearAll();
        PlatformServices.setProvider(null);
    }

    @Test
    void installsFilesArchivesMetadataAndRestoresDisplacedFilesOnUninstall() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packageRoot = tempDir.resolve("package");
        writePack(packageRoot.resolve("metadata").resolve("pack.dc"), "Example");
        Files.writeString(packageRoot.resolve("metadata").resolve("icon.png"), "icon");
        Files.createDirectories(packageRoot.resolve("mods"));
        Files.writeString(packageRoot.resolve("mods").resolve("addon.jar"), "new jar");
        Files.createDirectories(packageRoot.resolve("config"));
        Files.writeString(packageRoot.resolve("config").resolve("old.toml"), "new config");
        Files.createDirectories(instance.resolve("config"));
        Files.writeString(instance.resolve("config").resolve("old.toml"), "old config");

        DlcPackPackage pack = DlcPackPackage.inspect(packageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        DlcPackInstaller.InstallReport report = installer.install(pack);

        assertEquals("Example", report.identifier);
        assertEquals(2, report.filesWritten);
        assertEquals(1, report.displacedFiles);
        assertEquals("new jar", Files.readString(instance.resolve("mods").resolve("addon.jar")));
        assertEquals("new config", Files.readString(instance.resolve("config").resolve("old.toml")));
        assertEquals("old config", Files.readString(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("config").resolve("old.toml")));
        assertTrue(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("metadata").resolve("pack.dc")));
        assertTrue(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("metadata").resolve("icon.png")));
        assertTrue(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("meta.dc")));
        assertFalse(Files.exists(instance.resolve("metadata").resolve("pack.dc")));

        DlcPackInstaller.UninstallReport uninstall = installer.uninstall("Example");

        assertEquals(2, uninstall.filesDeleted);
        assertEquals(1, uninstall.filesRestored);
        assertFalse(Files.exists(instance.resolve("mods").resolve("addon.jar")));
        assertEquals("old config", Files.readString(instance.resolve("config").resolve("old.toml")));
        assertFalse(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Example")));
    }

    @Test
    void installReplacesExistingPackWhenRequested() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Files.createDirectories(instance.resolve("config"));
        Files.writeString(instance.resolve("config").resolve("old.toml"), "original config");
        Path oldPackageRoot = tempDir.resolve("old-package");
        writePack(oldPackageRoot.resolve("metadata").resolve("pack.dc"), "Example", "1.0.0", "");
        Files.createDirectories(oldPackageRoot.resolve("mods"));
        Files.writeString(oldPackageRoot.resolve("mods").resolve("addon.jar"), "old jar");
        Files.createDirectories(oldPackageRoot.resolve("config"));
        Files.writeString(oldPackageRoot.resolve("config").resolve("old.toml"), "old pack config");
        Path newPackageRoot = tempDir.resolve("new-package");
        writePack(newPackageRoot.resolve("metadata").resolve("pack.dc"), "Example", "2.0.0", "");
        Files.createDirectories(newPackageRoot.resolve("mods"));
        Files.writeString(newPackageRoot.resolve("mods").resolve("addon.jar"), "new jar");
        Files.createDirectories(newPackageRoot.resolve("shaderpacks"));
        Files.writeString(newPackageRoot.resolve("shaderpacks").resolve("new.txt"), "new shader");

        DlcPackPackage oldPack = DlcPackPackage.inspect(oldPackageRoot).pack;
        DlcPackPackage newPack = DlcPackPackage.inspect(newPackageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        installer.install(oldPack);

        assertThrows(IOException.class, () -> installer.install(newPack));
        DlcPackInstaller.InstallReport report = installer.install(newPack, true);

        assertEquals("Example", report.identifier);
        assertEquals("new jar", Files.readString(instance.resolve("mods").resolve("addon.jar")));
        assertEquals("new shader", Files.readString(instance.resolve("shaderpacks").resolve("new.txt")));
        assertEquals("original config", Files.readString(instance.resolve("config").resolve("old.toml")));
        DlcPackMeta meta = DlcPackMeta.load(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("meta.dc"));
        assertEquals("2.0.0", meta.version);
        assertNoStagingChildren(dlcRoot);
    }

    @Test
    void installReplacementRollsBackToOldPackWhenNewInstallFails() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Files.createDirectories(instance.resolve("config"));
        Files.writeString(instance.resolve("config").resolve("old.toml"), "original config");
        Path oldPackageRoot = tempDir.resolve("old-package");
        writePack(oldPackageRoot.resolve("metadata").resolve("pack.dc"), "Example", "1.0.0", "");
        Files.createDirectories(oldPackageRoot.resolve("mods"));
        Files.writeString(oldPackageRoot.resolve("mods").resolve("addon.jar"), "old jar");
        Files.createDirectories(oldPackageRoot.resolve("config"));
        Files.writeString(oldPackageRoot.resolve("config").resolve("old.toml"), "old pack config");
        Path badPackageRoot = tempDir.resolve("bad-package");
        writePack(badPackageRoot.resolve("metadata").resolve("pack.dc"), "Example", "2.0.0", "");
        Files.createDirectories(badPackageRoot.resolve("config").resolve("dlc_manager"));
        Files.writeString(badPackageRoot.resolve("config").resolve("dlc_manager").resolve("config.dc"), "blocked");

        DlcPackPackage oldPack = DlcPackPackage.inspect(oldPackageRoot).pack;
        DlcPackPackage badPack = DlcPackPackage.inspect(badPackageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        installer.install(oldPack);

        assertThrows(IOException.class, () -> installer.install(badPack, true));

        assertEquals("old jar", Files.readString(instance.resolve("mods").resolve("addon.jar")));
        assertEquals("old pack config", Files.readString(instance.resolve("config").resolve("old.toml")));
        assertEquals("original config", Files.readString(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("config").resolve("old.toml")));
        DlcPackMeta meta = DlcPackMeta.load(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("meta.dc"));
        assertEquals("1.0.0", meta.version);
        assertFalse(Files.exists(instance.resolve("config").resolve("dlc_manager").resolve("config.dc")));
    }

    @Test
    void installRejectsForbiddenPathsBeforeWritingFiles() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packageRoot = tempDir.resolve("package");
        writePack(packageRoot.resolve("metadata").resolve("pack.dc"), "Example");
        Files.createDirectories(packageRoot.resolve("config").resolve("dlc_manager"));
        Files.writeString(packageRoot.resolve("config").resolve("dlc_manager").resolve("config.dc"), "blocked");

        DlcPackPackage pack = DlcPackPackage.inspect(packageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);

        assertThrows(IOException.class, () -> installer.install(pack));
        assertFalse(Files.exists(instance.resolve("config").resolve("dlc_manager").resolve("config.dc")));
        assertFalse(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("meta.dc")));
    }

    @Test
    void installRejectsMissingPathDependenciesBeforeWritingFiles() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packageRoot = tempDir.resolve("package");
        writePack(packageRoot.resolve("metadata").resolve("pack.dc"), "Example", """
            [dependencies]
            path = "config/required.toml"
            """);
        Files.createDirectories(packageRoot.resolve("mods"));
        Files.writeString(packageRoot.resolve("mods").resolve("addon.jar"), "new jar");

        DlcPackPackage pack = DlcPackPackage.inspect(packageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);

        IOException error = assertThrows(IOException.class, () -> installer.install(pack));
        assertTrue(error.getMessage().contains("config/required.toml"));
        assertFalse(Files.exists(instance.resolve("mods").resolve("addon.jar")));
        assertFalse(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("meta.dc")));
    }

    @Test
    void installMovesConfiguredConflictDirectoryAndUninstallRestoresIt() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packageRoot = tempDir.resolve("package");
        writePack(packageRoot.resolve("metadata").resolve("pack.dc"), "Example", """
            [conflicts]
            path = "shaderpacks/old"
            """);
        Files.createDirectories(packageRoot.resolve("mods"));
        Files.writeString(packageRoot.resolve("mods").resolve("addon.jar"), "new jar");
        Files.createDirectories(instance.resolve("shaderpacks").resolve("old"));
        Files.writeString(instance.resolve("shaderpacks").resolve("old").resolve("shader.txt"), "old shader");

        DlcPackPackage pack = DlcPackPackage.inspect(packageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        DlcPackInstaller.InstallReport report = installer.install(pack);

        assertEquals(1, report.filesWritten);
        assertEquals(1, report.displacedFiles);
        assertFalse(Files.exists(instance.resolve("shaderpacks").resolve("old")));
        assertEquals(
            "old shader",
            Files.readString(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("shaderpacks").resolve("old").resolve("shader.txt"))
        );

        DlcPackInstaller.UninstallReport uninstall = installer.uninstall("Example");

        assertEquals(1, uninstall.filesRestored);
        assertEquals("old shader", Files.readString(instance.resolve("shaderpacks").resolve("old").resolve("shader.txt")));
    }

    @Test
    void uninstallRestoresInstanceDlcpackMetadataPaths() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packageRoot = tempDir.resolve("package");
        writePack(packageRoot.resolve("metadata").resolve("pack.dc"), "Example", """
            [conflicts]
            path = "dlcpack_metadata/old.txt"
            """);
        Files.createDirectories(packageRoot.resolve("mods"));
        Files.writeString(packageRoot.resolve("mods").resolve("addon.jar"), "new jar");
        Files.createDirectories(instance.resolve("dlcpack_metadata"));
        Files.writeString(instance.resolve("dlcpack_metadata").resolve("old.txt"), "old metadata named file");

        DlcPackPackage pack = DlcPackPackage.inspect(packageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        installer.install(pack);

        assertFalse(Files.exists(instance.resolve("dlcpack_metadata").resolve("old.txt")));
        assertEquals(
            "old metadata named file",
            Files.readString(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("dlcpack_metadata").resolve("old.txt"))
        );

        DlcPackInstaller.UninstallReport uninstall = installer.uninstall("Example");

        assertEquals(1, uninstall.filesRestored);
        assertEquals("old metadata named file", Files.readString(instance.resolve("dlcpack_metadata").resolve("old.txt")));
    }

    @Test
    void installRejectsDisabledDlcDependencyWithoutConfirmation() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packageRoot = tempDir.resolve("package");
        writePack(packageRoot.resolve("metadata").resolve("pack.dc"), "Example", """
            [dependencies]
            dlc = "RequiredDlc"
            """);
        Files.createDirectories(packageRoot.resolve("mods"));
        Files.writeString(packageRoot.resolve("mods").resolve("addon.jar"), "new jar");
        DlcItem required = new DlcItem("required", "RequiredDlc", "Required DLC", DlcType.MOD, null, "", false, null, null, null, null, true, false);
        DlcRuntimeState.updateItemIndex(java.util.List.of(required));

        DlcPackPackage pack = DlcPackPackage.inspect(packageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);

        IOException error = assertThrows(IOException.class, () -> installer.install(pack));
        assertTrue(error.getMessage().contains("RequiredDlc"));
        assertFalse(required.isEnabled());
        assertFalse(Files.exists(instance.resolve("mods").resolve("addon.jar")));
    }

    @Test
    void installAppliesConfirmedDlcDependencyAndConflictChanges() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packageRoot = tempDir.resolve("package");
        writePack(packageRoot.resolve("metadata").resolve("pack.dc"), "Example", """
            [dependencies]
            dlc = "RequiredDlc"
            
            [conflicts]
            dlc = "ConflictingDlc"
            """);
        Files.createDirectories(packageRoot.resolve("mods"));
        Files.writeString(packageRoot.resolve("mods").resolve("addon.jar"), "new jar");
        DlcItem required = new DlcItem("required", "RequiredDlc", "Required DLC", DlcType.MOD, null, "", false, null, null, null, null, true, false);
        DlcItem conflicting = new DlcItem("conflicting", "ConflictingDlc", "Conflicting DLC", DlcType.MOD, null, "", true, null, null, null, null, true, false);
        DlcRuntimeState.updateItemIndex(java.util.List.of(required, conflicting));

        DlcPackPackage pack = DlcPackPackage.inspect(packageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        DlcPackInstaller.InstallReport report = installer.install(pack, DlcPackInstaller.InstallOptions.applyConfirmedRuntimeChanges());

        assertEquals(1, report.enabledDlc);
        assertEquals(1, report.disabledDlc);
        assertTrue(required.isEnabled());
        assertFalse(conflicting.isEnabled());
        assertEquals("new jar", Files.readString(instance.resolve("mods").resolve("addon.jar")));
    }

    @Test
    void installTreatsConflictIdentifierAsRuntimeOnly() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packageRoot = tempDir.resolve("package");
        writePack(packageRoot.resolve("metadata").resolve("pack.dc"), "Example", """
            [conflicts]
            dlc = "ConflictingDlc"
            """);
        Files.createDirectories(packageRoot.resolve("mods"));
        Files.writeString(packageRoot.resolve("mods").resolve("addon.jar"), "new jar");
        DlcItem conflicting = new DlcItem("conflicting", "ConflictingDlc", "Conflicting DLC", DlcType.MOD, null, "", true, null, null, null, null, true, false);
        DlcRuntimeState.updateItemIndex(java.util.List.of(conflicting));

        DlcPackPackage pack = DlcPackPackage.inspect(packageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        DlcPackInstaller.InstallReport report = installer.install(pack, DlcPackInstaller.InstallOptions.applyConfirmedRuntimeChanges());

        assertEquals(1, report.disabledDlc);
        assertFalse(conflicting.isEnabled());
        assertEquals("new jar", Files.readString(instance.resolve("mods").resolve("addon.jar")));
        assertFalse(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("ConflictingDlc")));
    }

    @Test
    void installTreatsConflictPresetIdentifierAsRuntimeOnly() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Files.createDirectories(dlcRoot.resolve("preset"));
        Files.writeString(dlcRoot.resolve("preset").resolve("PresetA.dc"), """
            [basic]
            identifier = "PresetA"
            version = "1.0.0"
            """);
        Path packageRoot = tempDir.resolve("package");
        writePack(packageRoot.resolve("metadata").resolve("pack.dc"), "Example", """
            [conflicts]
            preset = "PresetA"
            """);
        Files.createDirectories(packageRoot.resolve("mods"));
        Files.writeString(packageRoot.resolve("mods").resolve("addon.jar"), "new jar");
        PlatformServices.setProvider(new TestPlatformProvider(instance, tempDir.resolve("config")));
        DlcPresetRuntimeState.clearAll();
        DlcPresetRuntimeState.reload();
        DlcPresetRuntimeState.setPresetEnabled("PresetA", true);

        DlcPackPackage pack = DlcPackPackage.inspect(packageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        DlcPackInstaller.InstallReport report = installer.install(pack, DlcPackInstaller.InstallOptions.applyConfirmedRuntimeChanges());

        assertEquals(1, report.disabledPresets);
        assertFalse(DlcPresetRuntimeState.isPresetEnabled("PresetA"));
        assertEquals("new jar", Files.readString(instance.resolve("mods").resolve("addon.jar")));
        assertFalse(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("PresetA")));
    }

    @Test
    void installRejectsBaseVersionMismatchBeforeWritingFiles() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        writeCurrentVersion(dlcRoot, "2.0.0", "1.0.0");
        Path packageRoot = tempDir.resolve("package");
        writePack(packageRoot.resolve("metadata").resolve("pack.dc"), "Example", """
            [modpack]
            base = "1.0.x"
            """);
        Files.createDirectories(packageRoot.resolve("mods"));
        Files.writeString(packageRoot.resolve("mods").resolve("addon.jar"), "new jar");

        DlcPackPackage pack = DlcPackPackage.inspect(packageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);

        IOException error = assertThrows(IOException.class, () -> installer.install(pack));
        assertTrue(error.getMessage().contains("incompatible"));
        assertFalse(Files.exists(instance.resolve("mods").resolve("addon.jar")));
        assertFalse(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("meta.dc")));
    }

    @Test
    void installRejectsDlcVersionMismatchBeforeWritingFiles() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        writeCurrentVersion(dlcRoot, "1.0.0", "2.0.0");
        Path packageRoot = tempDir.resolve("package");
        writePack(packageRoot.resolve("metadata").resolve("pack.dc"), "Example", """
            [modpack]
            dlc = "1.0.x"
            """);
        Files.createDirectories(packageRoot.resolve("mods"));
        Files.writeString(packageRoot.resolve("mods").resolve("addon.jar"), "new jar");

        DlcPackPackage pack = DlcPackPackage.inspect(packageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);

        IOException error = assertThrows(IOException.class, () -> installer.install(pack));
        assertTrue(error.getMessage().contains("incompatible"));
        assertFalse(Files.exists(instance.resolve("mods").resolve("addon.jar")));
        assertFalse(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Example").resolve("meta.dc")));
    }

    @Test
    void installCancellationRollsBackAndCleansStaging() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packageRoot = tempDir.resolve("package");
        writePack(packageRoot.resolve("metadata").resolve("pack.dc"), "Example");
        Files.createDirectories(packageRoot.resolve("mods"));
        Files.writeString(packageRoot.resolve("mods").resolve("addon.jar"), "new jar");

        DlcPackPackage pack = DlcPackPackage.inspect(packageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        DlcPackInstaller.InstallOptions options = DlcPackInstaller.InstallOptions.strict()
            .withCancelChecker(new DlcDownloadService.CancelChecker() {
                private int checks;

                @Override
                public boolean isCanceled() {
                    return ++checks >= 2;
                }
            });

        assertThrows(DlcDownloadService.DownloadCanceledException.class, () -> installer.install(pack, options));
        assertFalse(Files.exists(instance.resolve("mods").resolve("addon.jar")));
        assertFalse(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Example")));
        assertNoStagingChildren(dlcRoot);
    }

    private static void writePack(Path path, String identifier) throws Exception {
        writePack(path, identifier, "");
    }

    private static void writePack(Path path, String identifier, String extra) throws Exception {
        writePack(path, identifier, "1.0.0", extra);
    }

    private static void writeCurrentVersion(Path dlcRoot, String base, String dlc) throws Exception {
        Path version = dlcRoot.resolve("info").resolve("version.dc");
        Files.createDirectories(version.getParent());
        Files.writeString(version, """
            base = "%s"
            dlc = "%s"
            """.formatted(base, dlc));
    }

    private static void writePack(Path path, String identifier, String version, String extra) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
            [basic]
            identifier = "%s"
            version = "%s"
            
            %s
            """.formatted(identifier, version, extra));
    }

    private static void assertNoStagingChildren(Path dlcRoot) throws Exception {
        Path stagingRoot = dlcRoot.resolve("tmp").resolve("_staging");
        if (!Files.isDirectory(stagingRoot)) {
            return;
        }
        try (var stream = Files.list(stagingRoot)) {
            assertFalse(stream.findAny().isPresent());
        }
    }
}
