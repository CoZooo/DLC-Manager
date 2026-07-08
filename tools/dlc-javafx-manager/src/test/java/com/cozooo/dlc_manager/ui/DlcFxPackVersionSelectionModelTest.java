package com.cozooo.dlc_manager.ui;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class DlcFxPackVersionSelectionModelTest {
    @Test
    void selectingVersionStoresKeyAndClearsPendingOperationForIdentifier() {
        DlcFxPackPendingActionModel<Entry> pending = new DlcFxPackPendingActionModel<>(Entry::id, Entry::version);
        pending.toggle(new Entry("rail", "2.0.0"), new Entry("rail", "1.0.0"));
        LinkedHashMap<String, String> selectedVersions = new LinkedHashMap<>();
        DlcFxPackVersionSelectionModel<Entry> model = new DlcFxPackVersionSelectionModel<>(selectedVersions, pending, Entry::id, Entry::key);

        model.select(new Entry("rail", "3.0.0"));

        assertEquals("rail@3.0.0", selectedVersions.get("rail"));
        assertFalse(pending.hasOperationChanges());
    }

    private record Entry(String id, String version) {
        String key() {
            return id + "@" + version;
        }
    }
}
