package com.cozooo.dlc_manager.ui.remote;

import java.util.List;

public final class DlcFxResourceDownloadActionFlow<T> {
    public interface Actions<T> {
        boolean requireAvailable();

        List<T> missingItems();

        List<T> selectedItems();

        void startDownload(List<T> items);

        void showNoMissingDownloads();

        void showSelectListItem();
    }

    private final Actions<T> actions;

    public DlcFxResourceDownloadActionFlow(Actions<T> actions) {
        this.actions = actions;
    }

    public void downloadMissing() {
        if (!actions.requireAvailable()) {
            return;
        }
        List<T> missing = safeItems(actions.missingItems());
        if (missing.isEmpty()) {
            actions.showNoMissingDownloads();
            return;
        }
        actions.startDownload(missing);
    }

    public void downloadSelected() {
        if (!actions.requireAvailable()) {
            return;
        }
        List<T> selected = safeItems(actions.selectedItems());
        if (selected.isEmpty()) {
            actions.showSelectListItem();
            return;
        }
        actions.startDownload(selected);
    }

    public void downloadOne(T item) {
        if (!actions.requireAvailable() || item == null) {
            return;
        }
        actions.startDownload(List.of(item));
    }

    private static <T> List<T> safeItems(List<T> items) {
        return items == null ? List.of() : items;
    }
}
