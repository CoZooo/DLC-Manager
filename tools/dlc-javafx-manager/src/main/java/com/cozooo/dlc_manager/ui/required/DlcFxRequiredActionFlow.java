package com.cozooo.dlc_manager.ui.required;

import java.util.List;

public final class DlcFxRequiredActionFlow<T> {
    public interface Actions<T> {
        boolean requireAvailable();

        List<T> missingInstallTargets();

        List<T> selectedItems();

        List<T> selectedInstallTargets(List<T> selectedItems);

        boolean isInstalled(T item);

        boolean isActive(T item);

        void startInstall(List<T> items);

        void uninstall(T item);

        void showNoInstallable();

        void showSelectListItem();

        void showSelectedDownloaded();
    }

    private final Actions<T> actions;

    public DlcFxRequiredActionFlow(Actions<T> actions) {
        this.actions = actions;
    }

    public void installMissing() {
        if (!actions.requireAvailable()) {
            return;
        }
        List<T> targets = safeItems(actions.missingInstallTargets());
        if (targets.isEmpty()) {
            actions.showNoInstallable();
            return;
        }
        actions.startInstall(targets);
    }

    public void installSelected() {
        if (!actions.requireAvailable()) {
            return;
        }
        List<T> selected = safeItems(actions.selectedItems());
        if (selected.isEmpty()) {
            actions.showSelectListItem();
            return;
        }
        List<T> targets = safeItems(actions.selectedInstallTargets(selected));
        if (targets.isEmpty()) {
            actions.showSelectedDownloaded();
            return;
        }
        actions.startInstall(targets);
    }

    public void toggle(T item) {
        if (!actions.requireAvailable() || item == null || actions.isActive(item)) {
            return;
        }
        if (actions.isInstalled(item)) {
            actions.uninstall(item);
            return;
        }
        actions.startInstall(List.of(item));
    }

    private static <T> List<T> safeItems(List<T> items) {
        return items == null ? List.of() : items;
    }
}
