package com.cozooo.dlc_manager.ui.packs;

public final class DlcFxPackImportFlow {
    public interface UrlImportPrompt {
        void submit(String url, String fileName);
    }

    public interface Actions {
        boolean enabled();
        void disabled();
        void refresh();
        void importArchive();
        void promptUrlImport(UrlImportPrompt prompt);
        void emptyUrl();
        void importUrl(String url, String fileName);
    }

    private final Actions actions;

    public DlcFxPackImportFlow(Actions actions) {
        this.actions = actions;
    }

    public void refresh() {
        if (!actions.enabled()) {
            actions.disabled();
            return;
        }
        actions.refresh();
    }

    public void importArchive() {
        if (!actions.enabled()) {
            actions.disabled();
            return;
        }
        actions.importArchive();
    }

    public void importUrl() {
        if (!actions.enabled()) {
            actions.disabled();
            return;
        }
        actions.promptUrlImport(this::submitUrl);
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
