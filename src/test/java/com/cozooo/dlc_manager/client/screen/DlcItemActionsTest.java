package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DlcItemActionsTest {
    @AfterEach
    void resetState() {
        DlcRuntimeState.updateItemIndex(null);
        DlcRuntimeState.clearOverrides();
        DlcPresetRuntimeState.clearAll();
    }

    @Test
    void enablingIndependentItemRecordsPendingChange() {
        DlcItem item = item("addon", false, List.of());
        DlcRuntimeState.updateItemIndex(List.of(item));
        DlcRuntimeState.ensureBaseline(item.getId(), false);

        DlcItemActions.toggle(item);

        assertTrue(item.isEnabled());
        assertEquals(Boolean.TRUE, DlcRuntimeState.getOverride(item.getId()));
        assertTrue(DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled()));
    }

    @Test
    void disablingDependencyAlsoDisablesTransitiveDependents() {
        DlcItem core = item("core", true, List.of());
        DlcItem addon = item("addon", true, List.of("core"));
        DlcItem pack = item("pack", true, List.of("addon"));
        DlcRuntimeState.updateItemIndex(List.of(core, addon, pack));

        DlcItemActions.toggle(core);

        for (DlcItem item : List.of(core, addon, pack)) {
            assertFalse(item.isEnabled(), item.getId());
            assertEquals(Boolean.FALSE, DlcRuntimeState.getOverride(item.getId()));
        }
    }

    @Test
    void groupToggleEnablesThenDisablesWithoutWidgetObjects() {
        List<DlcItem> group = List.of(item("one", false, List.of()), item("two", false, List.of()));
        DlcRuntimeState.updateItemIndex(group);

        DlcItemActions.toggleGroup(group);
        assertTrue(group.stream().allMatch(DlcItem::isEnabled));

        DlcItemActions.toggleGroup(group);
        assertTrue(group.stream().noneMatch(DlcItem::isEnabled));
    }

    @Test
    void tooltipDependencyTraversalHandlesCycles() {
        DlcItem one = item("one", false, List.of("two"));
        DlcItem two = item("two", false, List.of("one"));
        DlcRuntimeState.updateItemIndex(List.of(one, two));

        assertEquals(List.of("two", "one"), DlcItemActions.dependencyNames(one));
    }

    private DlcItem item(String id, boolean enabled, List<String> dependencies) {
        return new DlcItem(id, id, id, DlcType.MOD, null, null, enabled,
            Map.of(), Map.of(), dependencies, List.of(), true, false);
    }
}
