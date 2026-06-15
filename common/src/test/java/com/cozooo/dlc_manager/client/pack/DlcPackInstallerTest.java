package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void installRejectsDuplicateUntilReplacementTransactionIsImplemented() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Path packageRoot = tempDir.resolve("package");
        writePack(packageRoot.resolve("metadata").resolve("pack.dc"), "Example");
        Files.createDirectories(packageRoot.resolve("mods"));
        Files.writeString(packageRoot.resolve("mods").resolve("addon.jar"), "new jar");

        DlcPackPackage pack = DlcPackPackage.inspect(packageRoot).pack;
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        installer.install(pack);

        assertThrows(IOException.class, () -> installer.install(pack));
        assertThrows(IOException.class, () -> installer.install(pack, true));
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

    private static void writePack(Path path, String identifier) throws Exception {
        writePack(path, identifier, "");
    }

    private static void writePack(Path path, String identifier, String extra) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
            [basic]
            identifier = "%s"
            version = "1.0.0"
            
            %s
            """.formatted(identifier, extra));
    }
}
