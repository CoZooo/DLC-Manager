package com.cozooo.dlc_manager.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxRequiredSelectionModelTest {
    @Test
    void installTargetsIncludeMissingDependenciesBeforeSelectedItem() {
        DlcFxRequiredSelectionModel.Item selected = item("a", false, false, "b");
        DlcFxRequiredSelectionModel.Item dependency = item("b", false, false);

        List<String> ids = DlcFxRequiredSelectionModel.installTargetIds(
                List.of(selected, dependency),
                List.of(selected)
        );

        assertEquals(List.of("b", "a"), ids);
    }

    @Test
    void applyTargetsUseDependencyClosureWhenDownloadsAreAlreadyPresent() {
        DlcFxRequiredSelectionModel.Item selected = item("a", true, true, "b");
        DlcFxRequiredSelectionModel.Item dependency = item("b", true, true);

        List<String> ids = DlcFxRequiredSelectionModel.applyTargetIds(
                List.of(selected, dependency),
                List.of(selected)
        );

        assertEquals(List.of("b", "a"), ids);
    }

    @Test
    void installedDependenciesAreNotReinstalled() {
        DlcFxRequiredSelectionModel.Item selected = item("a", false, false, "b");
        DlcFxRequiredSelectionModel.Item dependency = item("b", true, false);

        List<String> ids = DlcFxRequiredSelectionModel.installTargetIds(
                List.of(selected, dependency),
                List.of(selected)
        );

        assertEquals(List.of("a"), ids);
    }

    private static DlcFxRequiredSelectionModel.Item item(String id, boolean hasFile, boolean needsApply, String... dependencies) {
        return new DlcFxRequiredSelectionModel.Item(id, hasFile, needsApply, List.of(dependencies));
    }
}
