package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPackUpdateCoordinatorTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void isolatePlatformFiles() {
        Path instance = tempDir.resolve("instance");
        PlatformServices.setProvider(new TestPlatformProvider(instance, instance.resolve("config")));
    }

    @AfterEach
    void resetPlatformFiles() {
        PlatformServices.setProvider(null);
    }

    @Test
    void successfulUpdateReinstallsCompatiblePackFromSnapshot() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        Files.createDirectories(instance.resolve("config"));
        Files.writeString(instance.resolve("config").resolve("old.toml"), "before pack");
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        installer.install(pack("Compatible", "1.0.x", "", """
            [conflicts]
            path = "shaderpacks/old"
            """));
        Files.createDirectories(instance.resolve("shaderpacks").resolve("old"));
        Files.writeString(instance.resolve("shaderpacks").resolve("old").resolve("shader.txt"), "before update shader");

        DlcPackUpdateCoordinator coordinator = new DlcPackUpdateCoordinator(instance, dlcRoot);
        DlcPackUpdateCoordinator.UpdateSession session = coordinator.prepareForUpdate("1.0.5", "1.0.0", false);

        assertTrue(session.hasPacks());
        assertEquals("before pack", Files.readString(instance.resolve("config").resolve("old.toml")));
        assertFalse(Files.exists(instance.resolve("mods").resolve("Compatible.jar")));
        Files.writeString(instance.resolve("config").resolve("old.toml"), "after update");
        Files.writeString(instance.resolve("shaderpacks").resolve("old").resolve("shader.txt"), "after update shader");

        session.finishAfterSuccessfulUpdate();

        assertEquals("pack config", Files.readString(instance.resolve("config").resolve("old.toml")));
        assertEquals("pack jar", Files.readString(instance.resolve("mods").resolve("Compatible.jar")));
        assertEquals("after update", Files.readString(dlcRoot.resolve("tmp").resolve("pack").resolve("Compatible").resolve("config").resolve("old.toml")));
        assertEquals("after update shader", Files.readString(dlcRoot.resolve("tmp").resolve("pack").resolve("Compatible").resolve("shaderpacks").resolve("old").resolve("shader.txt")));
        assertNoStagingChildren(dlcRoot);
    }

    @Test
    void incompatiblePackRequiresExplicitUninstallPermission() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        installer.install(pack("Blocked", "1.0.x", ""));
        DlcPackUpdateCoordinator coordinator = new DlcPackUpdateCoordinator(instance, dlcRoot);

        assertThrows(Exception.class, () -> coordinator.prepareForUpdate("2.0.0", "1.0.0", false));

        DlcPackUpdateCoordinator.UpdateSession session = coordinator.prepareForUpdate("2.0.0", "1.0.0", true);
        assertTrue(session.hasIncompatiblePacks());
        session.finishAfterSuccessfulUpdate();

        assertFalse(Files.exists(instance.resolve("mods").resolve("Blocked.jar")));
        assertFalse(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Blocked")));
    }

    @Test
    void failedUpdateRestoresIncompatiblePackFromSnapshot() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        installer.install(pack("Blocked", "1.0.x", ""));
        DlcPackUpdateCoordinator coordinator = new DlcPackUpdateCoordinator(instance, dlcRoot);
        DlcPackUpdateCoordinator.UpdateSession session = coordinator.prepareForUpdate("2.0.0", "1.0.0", true);

        session.restoreAfterFailedUpdate();

        assertEquals("pack jar", Files.readString(instance.resolve("mods").resolve("Blocked.jar")));
        assertTrue(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Blocked").resolve("meta.dc")));
        assertNoStagingChildren(dlcRoot);
    }

    @Test
    void recoveryRestoresPackSnapshotWhenUpdateCrashesAfterPrepare() throws Exception {
        Path instance = tempDir.resolve("instance");
        Path dlcRoot = instance.resolve("DLC");
        DlcPackInstaller installer = new DlcPackInstaller(instance, dlcRoot);
        installer.install(pack("Compatible", "1.0.x", ""));
        DlcPackUpdateCoordinator coordinator = new DlcPackUpdateCoordinator(instance, dlcRoot);

        coordinator.prepareForUpdate("1.0.5", "1.0.0", false);

        assertFalse(Files.exists(instance.resolve("mods").resolve("Compatible.jar")));
        DlcPackRecovery.RecoveryReport report = DlcPackRecovery.recover(instance, dlcRoot);

        assertEquals(1, report.transactions);
        assertEquals(1, report.rolledBack);
        assertEquals("pack jar", Files.readString(instance.resolve("mods").resolve("Compatible.jar")));
        assertTrue(Files.exists(dlcRoot.resolve("tmp").resolve("pack").resolve("Compatible").resolve("meta.dc")));
        assertNoStagingChildren(dlcRoot);
    }

    private DlcPackPackage pack(String identifier, String base, String dlc) throws Exception {
        return pack(identifier, base, dlc, "");
    }

    private DlcPackPackage pack(String identifier, String base, String dlc, String extraMetadata) throws Exception {
        Path packageRoot = tempDir.resolve("package-" + identifier);
        Path metadata = packageRoot.resolve("metadata");
        Files.createDirectories(metadata);
        Files.writeString(metadata.resolve("pack.dc"), """
            [basic]
            identifier = "%s"
            version = "1.0.0"

            [modpack]
            base = "%s"
            dlc = "%s"

            %s
            """.formatted(identifier, base, dlc, extraMetadata));
        Files.createDirectories(packageRoot.resolve("mods"));
        Files.writeString(packageRoot.resolve("mods").resolve(identifier + ".jar"), "pack jar");
        Files.createDirectories(packageRoot.resolve("config"));
        Files.writeString(packageRoot.resolve("config").resolve("old.toml"), "pack config");
        return DlcPackPackage.inspect(packageRoot).pack;
    }

    private static void assertNoStagingChildren(Path dlcRoot) throws Exception {
        Path staging = dlcRoot.resolve("tmp").resolve("_staging");
        if (!Files.isDirectory(staging)) {
            return;
        }
        try (var stream = Files.list(staging)) {
            assertFalse(stream.findAny().isPresent());
        }
    }
}
