package com.cozooo.dlc_manager.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxDlcTogglePlannerTest {
    @Test
    void enablingIncludesMissingDependenciesBeforeTarget() {
        DlcFxDlcTogglePlanner.Item target = item("a", false, List.of("b"), List.of(), false);
        DlcFxDlcTogglePlanner.Item dependency = item("b", false, List.of(), List.of(), false);

        DlcFxDlcTogglePlanner.EnablePlan plan = DlcFxDlcTogglePlanner.planEnable(List.of(target, dependency), List.of("a"));

        assertEquals(List.of("b", "a"), plan.idsToEnable());
        assertEquals(List.of(new DlcFxDlcTogglePlanner.Dependency("a", "b")), plan.dependencies());
        assertTrue(plan.conflicts().isEmpty());
    }

    @Test
    void enablingReportsConflictsWithEnabledItems() {
        DlcFxDlcTogglePlanner.Item target = item("b", false, List.of(), List.of("a"), false);
        DlcFxDlcTogglePlanner.Item conflict = item("a", true, List.of(), List.of(), false);

        DlcFxDlcTogglePlanner.EnablePlan plan = DlcFxDlcTogglePlanner.planEnable(List.of(target, conflict), List.of("b"));

        assertEquals(List.of("b"), plan.idsToEnable());
        assertEquals(List.of(new DlcFxDlcTogglePlanner.Conflict("a", "b")), plan.conflicts());
    }

    @Test
    void enablingReportsReverseConflictsDeclaredByEnabledItems() {
        DlcFxDlcTogglePlanner.Item target = item("a", false, List.of(), List.of(), false);
        DlcFxDlcTogglePlanner.Item enabledConflict = item("b", true, List.of(), List.of("a"), false);

        DlcFxDlcTogglePlanner.EnablePlan plan = DlcFxDlcTogglePlanner.planEnable(List.of(target, enabledConflict), List.of("a"));

        assertEquals(List.of("a"), plan.idsToEnable());
        assertEquals(List.of(new DlcFxDlcTogglePlanner.Conflict("a", "b")), plan.conflicts());
    }

    @Test
    void enablingReportsConflictsIntroducedByDependencies() {
        DlcFxDlcTogglePlanner.Item target = item("a", false, List.of("dep"), List.of(), false);
        DlcFxDlcTogglePlanner.Item dependency = item("dep", false, List.of(), List.of("enabled"), false);
        DlcFxDlcTogglePlanner.Item enabledConflict = item("enabled", true, List.of(), List.of(), false);

        DlcFxDlcTogglePlanner.EnablePlan plan = DlcFxDlcTogglePlanner.planEnable(
                List.of(target, dependency, enabledConflict), List.of("a"));

        assertEquals(List.of("dep", "a"), plan.idsToEnable());
        assertEquals(List.of(new DlcFxDlcTogglePlanner.Dependency("a", "dep")), plan.dependencies());
        assertEquals(List.of(new DlcFxDlcTogglePlanner.Conflict("dep", "enabled")), plan.conflicts());
    }

    @Test
    void directConflictCollectionDoesNotIncludeDependencyIntroducedConflicts() {
        DlcFxDlcTogglePlanner.Item target = item("a", false, List.of("dep"), List.of(), false);
        DlcFxDlcTogglePlanner.Item dependency = item("dep", false, List.of(), List.of("enabled"), false);
        DlcFxDlcTogglePlanner.Item enabledConflict = item("enabled", true, List.of(), List.of(), false);

        List<DlcFxDlcTogglePlanner.Conflict> conflicts = DlcFxDlcTogglePlanner.collectConflictsForIds(
                List.of(target, dependency, enabledConflict), List.of("a"));

        assertTrue(conflicts.isEmpty());
    }

    @Test
    void enablingAlarmIncludesDependencies() {
        DlcFxDlcTogglePlanner.Item target = item("a", false, List.of("dep"), List.of(), false);
        DlcFxDlcTogglePlanner.Item dependency = item("dep", false, List.of(), List.of(), true);

        DlcFxDlcTogglePlanner.EnablePlan plan = DlcFxDlcTogglePlanner.planEnable(List.of(target, dependency), List.of("a"));

        assertTrue(plan.hasAlarm());
    }

    @Test
    void conflictSkipRemovesOnlySelectedConflictingTargets() {
        List<DlcFxDlcTogglePlanner.Item> items = List.of(
                item("a", false, List.of(), List.of("enabled"), false),
                item("b", false, List.of(), List.of(), false),
                item("enabled", true, List.of(), List.of(), false));
        DlcFxDlcTogglePlanner.EnablePlan plan = DlcFxDlcTogglePlanner.planEnable(items, List.of("a", "b"));

        List<String> filtered = DlcFxDlcTogglePlanner.filterConflictTargetIds(List.of("a", "b"), plan.conflicts());

        assertEquals(List.of("b"), filtered);
    }

    @Test
    void disablingIncludesDependentsRecursively() {
        DlcFxDlcTogglePlanner.Item base = item("base", true, List.of(), List.of(), false);
        DlcFxDlcTogglePlanner.Item child = item("child", true, List.of("base"), List.of(), false);
        DlcFxDlcTogglePlanner.Item grandChild = item("grand", true, List.of("child"), List.of(), false);

        List<String> ids = DlcFxDlcTogglePlanner.planDisable(List.of(base, child, grandChild), List.of("base"));

        assertEquals(List.of("base", "child", "grand"), ids);
    }

    @Test
    void disablingIncludesHiddenDependenciesWhenNoEnabledDependentStillNeedsThem() {
        DlcFxDlcTogglePlanner.Item visible = item("visible", true, List.of("hidden"), List.of(), false);
        DlcFxDlcTogglePlanner.Item hidden = new DlcFxDlcTogglePlanner.Item(
                "hidden", "hidden", true, List.of(), List.of(), true, false, true);

        List<String> ids = DlcFxDlcTogglePlanner.planDisable(List.of(visible, hidden), List.of("visible"));

        assertEquals(List.of("visible", "hidden"), ids);
    }

    @Test
    void disablingMatchesLocalRuntimeStyleForDependentsAndHiddenDependencies() {
        DlcFxDlcTogglePlanner.Item base = item("base", true, List.of("hidden"), List.of(), false);
        DlcFxDlcTogglePlanner.Item child = item("child", true, List.of("base"), List.of(), false);
        DlcFxDlcTogglePlanner.Item hidden = new DlcFxDlcTogglePlanner.Item(
                "hidden", "hidden", true, List.of(), List.of(), true, false, true);

        List<String> ids = DlcFxDlcTogglePlanner.planDisable(List.of(base, child, hidden), List.of("base"));

        assertEquals(List.of("base", "child", "hidden"), ids);
        assertEquals(Set.copyOf(ids).size(), ids.size());
    }

    private static DlcFxDlcTogglePlanner.Item item(String id, boolean enabled, List<String> dependencies, List<String> conflicts, boolean alarm) {
        return new DlcFxDlcTogglePlanner.Item(id, id, enabled, dependencies, conflicts, true, alarm, false);
    }
}
