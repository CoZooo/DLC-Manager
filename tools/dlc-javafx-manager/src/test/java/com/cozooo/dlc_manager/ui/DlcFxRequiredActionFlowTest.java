package com.cozooo.dlc_manager.ui;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxRequiredActionFlowTest {
    @Test
    void installMissingStartsAllMissingTargets() {
        Harness harness = new Harness();
        harness.missing = List.of("a", "b");
        DlcFxRequiredActionFlow<String> flow = new DlcFxRequiredActionFlow<>(harness);

        flow.installMissing();

        assertEquals(List.of(List.of("a", "b")), harness.installs);
        assertTrue(harness.messages.isEmpty());
    }

    @Test
    void installMissingReportsNoInstallableWhenEmpty() {
        Harness harness = new Harness();
        harness.missing = List.of();
        DlcFxRequiredActionFlow<String> flow = new DlcFxRequiredActionFlow<>(harness);

        flow.installMissing();

        assertTrue(harness.installs.isEmpty());
        assertEquals(List.of("no-installable"), harness.messages);
    }

    @Test
    void installSelectedUsesLocalStyleSelectedInstallTargets() {
        Harness harness = new Harness();
        harness.selected = List.of("selected");
        harness.selectedTargets = List.of("dependency", "selected");
        DlcFxRequiredActionFlow<String> flow = new DlcFxRequiredActionFlow<>(harness);

        flow.installSelected();

        assertEquals(List.of(List.of("dependency", "selected")), harness.installs);
    }

    @Test
    void installSelectedReportsSelectionRequiredWhenEmpty() {
        Harness harness = new Harness();
        harness.selected = List.of();
        DlcFxRequiredActionFlow<String> flow = new DlcFxRequiredActionFlow<>(harness);

        flow.installSelected();

        assertTrue(harness.installs.isEmpty());
        assertEquals(List.of("select"), harness.messages);
    }

    @Test
    void installSelectedReportsAlreadyDownloadedWhenNoSelectedTargetsNeedInstall() {
        Harness harness = new Harness();
        harness.selected = List.of("selected");
        harness.selectedTargets = List.of();
        DlcFxRequiredActionFlow<String> flow = new DlcFxRequiredActionFlow<>(harness);

        flow.installSelected();

        assertTrue(harness.installs.isEmpty());
        assertEquals(List.of("selected-downloaded"), harness.messages);
    }

    @Test
    void toggleUninstallsInstalledItemOtherwiseInstallsSingleItem() {
        Harness harness = new Harness();
        harness.installedItems = List.of("installed");
        DlcFxRequiredActionFlow<String> flow = new DlcFxRequiredActionFlow<>(harness);

        flow.toggle("installed");
        flow.toggle("missing");

        assertEquals(List.of("installed"), harness.uninstalls);
        assertEquals(List.of(List.of("missing")), harness.installs);
    }

    @Test
    void actionsStopWhenUnavailableOrActive() {
        Harness harness = new Harness();
        harness.available = false;
        DlcFxRequiredActionFlow<String> flow = new DlcFxRequiredActionFlow<>(harness);

        flow.installMissing();
        flow.installSelected();
        flow.toggle("a");

        assertTrue(harness.installs.isEmpty());
        assertEquals(3, harness.requireCalls);

        harness.available = true;
        harness.activeItems = List.of("a");
        flow.toggle("a");

        assertTrue(harness.installs.isEmpty());
        assertTrue(harness.uninstalls.isEmpty());
    }

    private static final class Harness implements DlcFxRequiredActionFlow.Actions<String> {
        private List<String> missing = List.of();
        private List<String> selected = List.of();
        private List<String> selectedTargets = List.of();
        private List<String> installedItems = List.of();
        private List<String> activeItems = List.of();
        private final List<List<String>> installs = new ArrayList<>();
        private final List<String> uninstalls = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();
        private boolean available = true;
        private int requireCalls;

        @Override
        public boolean requireAvailable() {
            requireCalls++;
            return available;
        }

        @Override
        public List<String> missingInstallTargets() {
            return missing;
        }

        @Override
        public List<String> selectedItems() {
            return selected;
        }

        @Override
        public List<String> selectedInstallTargets(List<String> selectedItems) {
            return selectedTargets;
        }

        @Override
        public boolean isInstalled(String item) {
            return installedItems.contains(item);
        }

        @Override
        public boolean isActive(String item) {
            return activeItems.contains(item);
        }

        @Override
        public void startInstall(List<String> items) {
            installs.add(List.copyOf(items));
        }

        @Override
        public void uninstall(String item) {
            uninstalls.add(item);
        }

        @Override
        public void showNoInstallable() {
            messages.add("no-installable");
        }

        @Override
        public void showSelectListItem() {
            messages.add("select");
        }

        @Override
        public void showSelectedDownloaded() {
            messages.add("selected-downloaded");
        }
    }
}
