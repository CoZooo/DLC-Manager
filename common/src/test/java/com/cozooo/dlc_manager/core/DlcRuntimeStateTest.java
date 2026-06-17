package com.cozooo.dlc_manager.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcRuntimeStateTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void resetIndex() {
        DlcRuntimeState.updateItemIndex(null);
        DlcRuntimeState.clearOverrides();
        DlcRuntimeState.clearAllPendingReload();
        PlatformServices.setProvider(null);
    }

    @Test
    void indexesDependenciesAndCollectsRecursiveGraphs() {
        DlcItem core = item("core", true, false, List.of(), List.of());
        DlcItem addon = item("addon", true, false, List.of("core"), List.of());
        DlcItem pack = item("pack", false, false, List.of("addon"), List.of());
        DlcRuntimeState.updateItemIndex(List.of(core, addon, pack));

        assertEquals(core, DlcRuntimeState.getItemByIdentifier("core"));
        assertEquals(List.of(addon), DlcRuntimeState.getDependents("core"));
        assertEquals(Set.of(addon, core), DlcRuntimeState.collectDependenciesRecursive(pack));
        assertEquals(Set.of(core, addon, pack), DlcRuntimeState.collectDependentsRecursive(core));
    }

    @Test
    void collectMissingDependenciesReturnsMissingLinesAndKnownItemsToEnable() {
        DlcItem known = item("known", false, false, List.of(), List.of());
        DlcItem target = item("target", false, false, List.of("known", "missing"), List.of());
        DlcRuntimeState.updateItemIndex(List.of(known, target));

        DlcRuntimeState.DependencyBatchResult result = DlcRuntimeState.collectMissingDependencies(List.of(target));

        assertEquals(2, result.getLines().size());
        assertEquals(List.of(known), result.getItemsToEnable());
        assertEquals("target", result.getLines().get(0).getItemName());
        assertEquals("known", result.getLines().get(0).getDependencyName());
        assertEquals(Set.of("missing"), DlcRuntimeState.collectMissingDependencyIdentifiers(target));
        assertTrue(DlcRuntimeState.hasMissingDependencies(target));
    }

    @Test
    void wildcardPathDependenciesAreSatisfiedByAnyMatchingPath() throws Exception {
        Path gameDir = tempDir.resolve("game");
        PlatformServices.setProvider(new TestPlatformProvider(gameDir, tempDir.resolve("config")));
        Files.createDirectories(gameDir.resolve("mods").resolve("alpha"));
        Files.writeString(gameDir.resolve("mods").resolve("alpha").resolve("required.txt"), "required");
        DlcItem target = item("target", false, false, List.of("mods/*/required.txt"), List.of());
        DlcRuntimeState.updateItemIndex(List.of(target));

        assertEquals(Set.of(), DlcRuntimeState.collectMissingDependencyIdentifiers(target));
        assertFalse(DlcRuntimeState.hasMissingDependencies(target));
    }

    @Test
    void collectConflictsIncludesDirectReverseAndDependencyConflictsOnce() {
        DlcItem enabledConflict = item("enabledConflict", true, false, List.of(), List.of("target"));
        DlcItem dependency = item("dependency", false, false, List.of(), List.of("target"));
        DlcItem target = item("target", false, false, List.of("dependency"), List.of("enabledConflict"));
        DlcRuntimeState.updateItemIndex(List.of(enabledConflict, dependency, target));

        DlcRuntimeState.ConflictBatchResult result = DlcRuntimeState.collectConflicts(List.of(target));

        assertEquals(2, result.getLines().size());
        assertEquals("enabledConflict", result.getLines().get(0).getLeftName());
        assertEquals("target", result.getLines().get(0).getRightName());
        assertEquals("dependency", result.getLines().get(1).getLeftName());
        assertEquals("target", result.getLines().get(1).getRightName());
    }

    @Test
    void hiddenDependenciesDisableOnlyWhenNoEnabledDependentStillNeedsThem() {
        DlcItem hidden = item("hidden", true, true, List.of(), List.of());
        DlcItem disabled = item("disabled", false, false, List.of("hidden"), List.of());
        DlcItem stillEnabled = item("enabled", true, false, List.of("hidden"), List.of());
        DlcRuntimeState.updateItemIndex(List.of(hidden, disabled, stillEnabled));

        assertEquals(Set.of(), DlcRuntimeState.collectHiddenDependenciesToDisable(List.of(disabled)));

        stillEnabled.setEnabled(false);
        assertEquals(Set.of(hidden), DlcRuntimeState.collectHiddenDependenciesToDisable(List.of(disabled)));
    }

    @Test
    void pendingBaselineAndOverrideStateAreTrackedSeparately() {
        DlcItem item = item("runtime-state-item", false, false, List.of(), List.of());

        DlcRuntimeState.markPendingRestart(List.of(item));
        DlcRuntimeState.markPendingReload(List.of(item));
        assertTrue(DlcRuntimeState.isPending(item.getId()));
        assertTrue(DlcRuntimeState.isPendingRestart(item.getId()));
        assertTrue(DlcRuntimeState.isPendingReload(item.getId()));
        DlcRuntimeState.clearPendingRestart(item.getId());
        DlcRuntimeState.clearPendingReload(item.getId());
        assertFalse(DlcRuntimeState.isPending(item.getId()));

        DlcRuntimeState.ensureBaseline(item.getId(), false);
        DlcRuntimeState.ensureBaseline(item.getId(), true);
        assertTrue(DlcRuntimeState.isBaselineDifferent(item.getId(), true));
        DlcRuntimeState.updateBaseline(item.getId(), true);
        assertFalse(DlcRuntimeState.isBaselineDifferent(item.getId(), true));

        DlcRuntimeState.setOverride(item.getId(), true);
        assertEquals(Boolean.TRUE, DlcRuntimeState.getOverride(item.getId()));
        DlcRuntimeState.clearOverride(item.getId());
        assertEquals(null, DlcRuntimeState.getOverride(item.getId()));
    }

    private static DlcItem item(String identifier, boolean enabled, boolean hidden, List<String> dependencies, List<String> conflicts) {
        return new DlcItem(
            "test:" + identifier,
            identifier,
            identifier + ".jar",
            DlcType.MOD,
            Path.of(identifier + ".jar"),
            null,
            enabled,
            null,
            null,
            dependencies,
            conflicts,
            true,
            hidden
        );
    }
}
