package com.cozooo.dlc_manager.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxResourceDownloadActionFlowTest {
    @Test
    void downloadMissingUsesLocalStyleMissingCollectionAndStartsBatch() {
        Harness harness = new Harness(List.of("a", "b"), List.of("a"));
        DlcFxResourceDownloadActionFlow<String> flow = new DlcFxResourceDownloadActionFlow<>(harness);

        flow.downloadMissing();

        assertEquals(List.of(List.of("a", "b")), harness.started);
        assertFalse(harness.messages.contains("empty"));
    }

    @Test
    void downloadMissingReportsEmptyWhenNothingIsMissing() {
        Harness harness = new Harness(List.of(), List.of("a"));
        DlcFxResourceDownloadActionFlow<String> flow = new DlcFxResourceDownloadActionFlow<>(harness);

        flow.downloadMissing();

        assertEquals(List.of(), harness.started);
        assertEquals(List.of("empty"), harness.messages);
    }

    @Test
    void downloadSelectedUsesSelectedMissingItemsOnly() {
        Harness harness = new Harness(List.of("a", "b"), List.of("b"));
        DlcFxResourceDownloadActionFlow<String> flow = new DlcFxResourceDownloadActionFlow<>(harness);

        flow.downloadSelected();

        assertEquals(List.of(List.of("b")), harness.started);
    }

    @Test
    void downloadSelectedReportsSelectionRequiredWhenNothingIsSelected() {
        Harness harness = new Harness(List.of("a"), List.of());
        DlcFxResourceDownloadActionFlow<String> flow = new DlcFxResourceDownloadActionFlow<>(harness);

        flow.downloadSelected();

        assertEquals(List.of(), harness.started);
        assertEquals(List.of("select"), harness.messages);
    }

    @Test
    void downloadOneStartsSingleItemOnlyWhenAvailable() {
        Harness harness = new Harness(List.of("a"), List.of());
        DlcFxResourceDownloadActionFlow<String> flow = new DlcFxResourceDownloadActionFlow<>(harness);

        flow.downloadOne("x");

        assertEquals(List.of(List.of("x")), harness.started);
    }

    @Test
    void actionsStopWhenUnavailable() {
        Harness harness = new Harness(List.of("a"), List.of("a"));
        harness.available = false;
        DlcFxResourceDownloadActionFlow<String> flow = new DlcFxResourceDownloadActionFlow<>(harness);

        flow.downloadMissing();
        flow.downloadSelected();
        flow.downloadOne("a");

        assertTrue(harness.started.isEmpty());
        assertEquals(3, harness.requireCalls);
    }

    private static final class Harness implements DlcFxResourceDownloadActionFlow.Actions<String> {
        private final List<String> missing;
        private final List<String> selected;
        private final List<List<String>> started = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();
        private boolean available = true;
        private int requireCalls;

        private Harness(List<String> missing, List<String> selected) {
            this.missing = missing;
            this.selected = selected;
        }

        @Override
        public boolean requireAvailable() {
            requireCalls++;
            return available;
        }

        @Override
        public List<String> missingItems() {
            return missing;
        }

        @Override
        public List<String> selectedItems() {
            return selected;
        }

        @Override
        public void startDownload(List<String> items) {
            started.add(List.copyOf(items));
        }

        @Override
        public void showNoMissingDownloads() {
            messages.add("empty");
        }

        @Override
        public void showSelectListItem() {
            messages.add("select");
        }
    }
}
