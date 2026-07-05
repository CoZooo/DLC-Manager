package com.cozooo.dlc_manager.ui.update;

public final class DlcFxUpdateToolbarFlow {
    public interface UrlImportPrompt {
        void submit(String url, String fileName);
    }

    public interface Actions {
        boolean enabled();
        void disabled();
        void refresh();
        void importPackage();
        void promptUrlImport(UrlImportPrompt prompt);
        void emptyUrl();
        void importUrl(String url, String fileName);
        void extraAction();
    }

    private final Actions actions;

    public DlcFxUpdateToolbarFlow(Actions actions) {
        this.actions = actions;
    }

    public void refresh() {
        if (!actions.enabled()) {
            actions.disabled();
            return;
        }
        actions.refresh();
    }

    public void importPackage() {
        if (!actions.enabled()) {
            actions.disabled();
            return;
        }
        actions.importPackage();
    }

    public void importUrl() {
        if (!actions.enabled()) {
            actions.disabled();
            return;
        }
        actions.promptUrlImport(this::submitUrl);
    }

    public void extraAction() {
        if (!actions.enabled()) {
            actions.disabled();
            return;
        }
        actions.extraAction();
    }

    private void submitUrl(String rawUrl, String fileName) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.isBlank()) {
            actions.emptyUrl();
            return;
        }
        actions.importUrl(url, fileName == null ? "" : fileName.trim());
    }
}
