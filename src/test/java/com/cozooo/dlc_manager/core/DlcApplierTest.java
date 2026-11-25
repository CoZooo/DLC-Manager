package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
    void gunpacksDeployDetectAndDisableUsingConfiguredTaczTargets() throws Exception {
        DlcConfig.setTaczRoot("custom/gunpacks,second-gunpacks");
        Files.createDirectories(dlcRoot.resolve("sources/directory-pack"));
        Path archive = dlcRoot.resolve("sources/guns.zip");
        Path directory = dlcRoot.resolve("sources/directory-pack");
        Files.writeString(archive, "archive-content");
        Files.writeString(directory.resolve("guns.json"), "directory-content");
        DlcItem zipItem = item("gun-archive", DlcType.TACZ_PACK, archive, null, false);
        DlcItem directoryItem = item("gun-directory", DlcType.TACZ_PACK, directory, null, false);
        List<DlcItem> items = List.of(zipItem, directoryItem);
        items.forEach(item -> item.setEnabled(true));

        DlcApplier.apply(items, DlcApplyMode.RESTART_ONLY);

        assertEquals(List.of(gameDir.resolve("custom/gunpacks/guns.zip"), gameDir.resolve("second-gunpacks/guns.zip")), DlcItemFiles.installed(zipItem));
        assertEquals(List.of(gameDir.resolve("custom/gunpacks/directory-pack"), gameDir.resolve("second-gunpacks/directory-pack")), DlcItemFiles.installed(directoryItem));
        assertEquals("directory-content", Files.readString(gameDir.resolve("custom/gunpacks/directory-pack/guns.json")));
        assertEquals(List.of("custom/gunpacks/guns.zip", "second-gunpacks/guns.zip"), DlcStateConfig.getState(zipItem.getId()).appliedTargets);
        assertFalse(Files.exists(gameDir.resolve("tacz")));
        assertEquals("archive-content", Files.readString(archive));

        DlcConfig.setTaczRoot("changed-target");
        items.forEach(item -> item.setEnabled(false));
        DlcApplier.apply(items, DlcApplyMode.RESTART_ONLY);

        assertFalse(Files.exists(gameDir.resolve("custom/gunpacks/guns.zip")));
        assertFalse(Files.exists(gameDir.resolve("second-gunpacks/directory-pack")));
        assertEquals("directory-content", Files.readString(directory.resolve("guns.json")));
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
    void rollbackRemovesDirectoriesCreatedByApply() throws Exception {
        Files.createDirectories(dlcRoot);
        Path source = dlcRoot.resolve("settings.toml");
        Files.writeString(source, "value");
        DlcItem item = new DlcItem(
            "unknown:settings",
            "settings",
            "settings",
            DlcType.UNKNOWN,
            source,
            "config/example/",
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

        Path target = gameDir.resolve("config/example/settings.toml");
        assertTrue(Files.exists(target));

        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        assertFalse(Files.exists(target));
        assertFalse(Files.exists(gameDir.resolve("config/example")));
        assertFalse(Files.exists(gameDir.resolve("config")));
    }

    @Test
    void applyRecordsDirectoriesItCreates() throws Exception {
        Files.createDirectories(dlcRoot);
        Path source = dlcRoot.resolve("settings.toml");
        Files.writeString(source, "value");
        DlcItem item = new DlcItem(
            "unknown:recorded",
            "recorded",
            "recorded",
            DlcType.UNKNOWN,
            source,
            "config/recorded/inner/",
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

        JsonObject data = DlcDcConfigIO.read(dlcRoot.resolve("tmp").resolve("dlc").resolve("history.dc"), DlcDcConfigIO.Layout.GENERIC);
        JsonObject resource = data.getAsJsonObject("resources").getAsJsonObject(DlcResourceHistory.key("unknown:recorded", "dlc"));
        assertTrue(resource != null);
        JsonArray created = resource.getAsJsonArray("createdDirs");
        assertTrue(created != null);
        List<String> directories = new java.util.ArrayList<>();
        for (var value : created) directories.add(value.getAsString());
        assertEquals(List.of("config", "config/recorded", "config/recorded/inner"), directories);

        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        assertFalse(Files.exists(gameDir.resolve("config/recorded/inner")));
        assertFalse(Files.exists(gameDir.resolve("config/recorded")));
        assertFalse(Files.exists(gameDir.resolve("config")));
    }

    @Test
    void rollbackKeepsDirectoriesItDidNotCreate() throws Exception {
        Files.createDirectories(dlcRoot);
        Files.createDirectories(gameDir.resolve("config"));
        Path source = dlcRoot.resolve("entry.toml");
        Files.writeString(source, "value");
        DlcItem item = new DlcItem(
            "unknown:keep",
            "keep",
            "keep",
            DlcType.UNKNOWN,
            source,
            "config/kept/entry.toml",
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
        assertTrue(Files.exists(gameDir.resolve("config/kept/entry.toml")));

        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        assertFalse(Files.exists(gameDir.resolve("config/kept/entry.toml")));
        assertFalse(Files.exists(gameDir.resolve("config/kept")));
        assertTrue(Files.exists(gameDir.resolve("config")));
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
    void collectionCopiesRelativePathsAndRestoresOverwrittenFilesOnDisable() throws Exception {
        Path source = dlcRoot.resolve("collections/example");
        Files.createDirectories(source.resolve("config"));
        Files.createDirectories(source.resolve("kubejs/server_scripts"));
        Files.writeString(source.resolve("config/example.toml"), "collection");
        Files.writeString(source.resolve("kubejs/server_scripts/example.js"), "script");
        Path existing = gameDir.resolve("config/example.toml");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "original");
        DlcItem item = new DlcItem(
            "collection:example", "example", "example", DlcType.COLLECTION, source, List.of(), null, false,
            null, null, List.of(), List.of(), true, false, false, false
        );

        item.setEnabled(true);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        assertEquals("collection", Files.readString(existing));
        assertEquals("script", Files.readString(gameDir.resolve("kubejs/server_scripts/example.js")));

        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        assertEquals("original", Files.readString(existing));
        assertFalse(Files.exists(gameDir.resolve("kubejs/server_scripts/example.js")));
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
        assertTrue(listFiles(dlcRoot.resolve("tmp/dlc")).stream().anyMatch(path -> path.getFileName().toString().startsWith(item.getIdentifier() + ".") && !path.getFileName().toString().endsWith(".old")));

        item.setEnabled(false);
        DlcApplier.apply(List.of(item), DlcApplyMode.RESTART_ONLY);

        assertEquals("value = \"original\"", Files.readString(target));
        assertTrue(listFiles(dlcRoot.resolve("tmp/dlc")).stream().anyMatch(path -> path.getFileName().toString().endsWith(".old")));
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
        Path backupRoot = dlcRoot.resolve("tmp/dlc");
        assertTrue(Files.exists(backupRoot.resolve("history.dc")));
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

    @Test
    void dlcConflictPathsMoveAndRestoreOnlyWhenNoEnabledDlcStillDeclaresThem() throws Exception {
        Files.createDirectories(gameDir.resolve("mods"));
        Files.writeString(gameDir.resolve("mods/a.jar"), "a");
        Files.writeString(gameDir.resolve("mods/b.jar"), "b");
        DlcItem itemA = item("A", DlcType.UNKNOWN, dlcRoot.resolve("a-source.txt"), null, false, List.of("mods/a.jar", "mods/b.jar"));
        DlcItem itemB = item("B", DlcType.UNKNOWN, dlcRoot.resolve("b-source.txt"), null, false, List.of("mods/b.jar"));
        DlcRuntimeState.updateItemIndex(List.of(itemA, itemB));

        itemA.setEnabled(true);
        itemB.setEnabled(false);
        DlcApplier.apply(List.of(itemA, itemB), DlcApplyMode.RESTART_ONLY);

        assertFalse(Files.exists(gameDir.resolve("mods/a.jar")));
        assertFalse(Files.exists(gameDir.resolve("mods/b.jar")));
        assertEquals("a", Files.readString(dlcRoot.resolve("tmp/conflicts/mods/a.jar")));
        assertEquals("b", Files.readString(dlcRoot.resolve("tmp/conflicts/mods/b.jar")));

        itemB.setEnabled(true);
        assertTrue(DlcRuntimeState.collectConflicts(List.of(itemB)).getLines().isEmpty());
        DlcApplier.apply(List.of(itemA, itemB), DlcApplyMode.RESTART_ONLY);

        assertFalse(Files.exists(gameDir.resolve("mods/a.jar")));
        assertFalse(Files.exists(gameDir.resolve("mods/b.jar")));

        itemA.setEnabled(false);
        DlcApplier.apply(List.of(itemA, itemB), DlcApplyMode.RESTART_ONLY);

        assertEquals("a", Files.readString(gameDir.resolve("mods/a.jar")));
        assertFalse(Files.exists(gameDir.resolve("mods/b.jar")));
        assertFalse(Files.exists(dlcRoot.resolve("tmp/conflicts/mods/a.jar")));
        assertEquals("b", Files.readString(dlcRoot.resolve("tmp/conflicts/mods/b.jar")));

        itemB.setEnabled(false);
        DlcApplier.apply(List.of(itemA, itemB), DlcApplyMode.RESTART_ONLY);

        assertEquals("b", Files.readString(gameDir.resolve("mods/b.jar")));
        assertFalse(Files.exists(dlcRoot.resolve("tmp/conflicts/mods/b.jar")));
    }

    private static DlcItem item(String identifier, DlcType type, Path source, String target, boolean configType) {
        return item(identifier, type, source, target, configType, List.of());
    }

    private static DlcItem item(String identifier, DlcType type, Path source, String target, boolean configType, List<String> conflicts) {
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
            conflicts,
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
