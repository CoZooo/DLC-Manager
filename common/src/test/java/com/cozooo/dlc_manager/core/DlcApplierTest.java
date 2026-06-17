package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcApplierTest {
    @TempDir
    Path tempDir;

    private Path gameDir;
    private Path dlcRoot;

    @BeforeEach
    void setUp() {
        gameDir = tempDir.resolve("game");
        dlcRoot = gameDir.resolve("DLC");
        PlatformServices.setProvider(new TestPlatformProvider(gameDir, tempDir.resolve("config")));
        DlcConfig.setDlcRoot("DLC");
        DlcConfig.setResourcepackRoot("resourcepacks");
        DlcConfig.setDatapackRoot("datapacks");
        DlcConfig.setTaczRoot("tacz");
        DlcStateConfig.load();
    }

    @AfterEach
    void tearDown() {
        PlatformServices.setProvider(null);
        DlcRuntimeState.updateItemIndex(null);
    }

    @Test
    void applyCopiesAndRemovesModFilesAndPersistsRelativeState() throws Exception {
        Files.createDirectories(dlcRoot);
        Path source = dlcRoot.resolve("mod-a.jar");
        Files.writeString(source, "mod");
        DlcItem item = item("mod-a", DlcType.MOD, source, null, false);

        item.setEnabled(true);
        DlcApplier.ApplyResult enabled = DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        Path target = gameDir.resolve("mods").resolve("mod-a.jar");
        assertTrue(Files.exists(target));
        assertTrue(enabled.requiresRestart());
        DlcStateConfig.DlcState state = DlcStateConfig.getState(item.getId());
        assertTrue(state.enabled);
        assertEquals(List.of("mods/mod-a.jar"), state.appliedTargets);

        item.setEnabled(false);
        DlcApplier.ApplyResult disabled = DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        assertFalse(Files.exists(target));
        assertTrue(disabled.requiresRestart());
        assertFalse(DlcStateConfig.getState(item.getId()).enabled);
    }

    @Test
    void applyUnknownDirectoryTargetCopiesAndRemovesMultipleSources() throws Exception {
        Files.createDirectories(dlcRoot.resolve("files"));
        Path sourceA = dlcRoot.resolve("files").resolve("a.txt");
        Path sourceB = dlcRoot.resolve("files").resolve("b.txt");
        Files.writeString(sourceA, "a");
        Files.writeString(sourceB, "b");
        DlcItem item = new DlcItem(
            "unknown:test",
            "test",
            "test",
            DlcType.UNKNOWN,
            sourceA,
            List.of(sourceA, sourceB),
            "config/test/",
            false,
            null,
            null,
            List.of(),
            List.of(),
            true,
            false
        );

        item.setEnabled(true);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        assertEquals("a", Files.readString(gameDir.resolve("config/test/a.txt")));
        assertEquals("b", Files.readString(gameDir.resolve("config/test/b.txt")));

        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        assertFalse(Files.exists(gameDir.resolve("config/test/a.txt")));
        assertFalse(Files.exists(gameDir.resolve("config/test/b.txt")));
    }

    @Test
    void configTypeDlcBacksUpExistingTargetAndRestoresItOnDisable() throws Exception {
        Files.createDirectories(dlcRoot);
        Path source = dlcRoot.resolve("override.toml");
        Files.writeString(source, "value = \"override\"");
        Path target = DlcPaths.resolveAppliedTarget("config/app.toml");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "value = \"original\"");
        DlcItem item = item("config-override", DlcType.UNKNOWN, source, "config/app.toml", true);

        item.setEnabled(true);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        assertEquals("value = \"override\"", Files.readString(target));
        assertTrue(Files.exists(DlcConfigDlcSupport.backupPathForItemTarget(item.getId(), target)));

        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        assertEquals("value = \"original\"", Files.readString(target));
        assertFalse(Files.exists(DlcConfigDlcSupport.backupPathForItemTarget(item.getId(), target)));
    }

    @Test
    void configTypeDlcDeletesTargetOnDisableWhenOriginalWasMissing() throws Exception {
        Files.createDirectories(dlcRoot);
        Path source = dlcRoot.resolve("new.toml");
        Files.writeString(source, "value = true");
        Path target = DlcPaths.resolveAppliedTarget("config/generated").resolve(source.getFileName().toString());
        DlcItem item = item("config-new", DlcType.UNKNOWN, source, "config/generated", true);

        item.setEnabled(true);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        assertTrue(Files.exists(target));
        Path backupRoot = DlcConfigDlcSupport.backupRootForItem(item.getId());
        assertTrue(hasMissingMarker(backupRoot), () -> "missing marker files=" + listFiles(backupRoot));
        DlcStateConfig.DlcState enabledState = DlcStateConfig.getState(item.getId());
        assertTrue(enabledState.enabled);

        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        DlcStateConfig.DlcState disabledState = DlcStateConfig.getState(item.getId());
        assertFalse(Files.exists(target), () -> "enabledState=" + enabledState.appliedTargets
            + " disabledState=" + (disabledState == null ? null : disabledState.appliedTargets)
            + " files=" + listFiles(backupRoot));
        assertFalse(hasMissingMarker(backupRoot), () -> "enabledState=" + enabledState.appliedTargets
            + " disabledState=" + (disabledState == null ? null : disabledState.appliedTargets)
            + " files=" + listFiles(backupRoot));
    }

    private static DlcItem item(String identifier, DlcType type, Path source, String target, boolean configType) {
        return new DlcItem(
            type.name().toLowerCase() + ":" + identifier,
            identifier,
            source.getFileName().toString(),
            type,
            source,
            target,
            false,
            null,
            null,
            List.of(),
            List.of(),
            true,
            false,
            configType
        );
    }

    private static List<Path> listFiles(Path root) {
        if (root == null || !Files.exists(root)) {
            return List.of();
        }
        try (var stream = Files.walk(root)) {
            return stream.toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static boolean hasMissingMarker(Path root) {
        return listFiles(root).stream()
            .anyMatch(path -> path.getFileName() != null && path.getFileName().toString().endsWith(".missing"));
    }
}
