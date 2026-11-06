package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Element;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public abstract class DlcUpdatePageScreen extends DlcPageScreen {
    private Element updateHistory;
    protected DlcUpdatePageScreen(Screen parent, Component title) { this(DlcUiPage.PAGE, parent, title); }
    protected DlcUpdatePageScreen(DlcUiPage page, Screen parent, Component title) { super(page, parent, title); }
    protected record UpdateRow(String label, String note, String changelog, Button action, Runnable manageBackups) {
        protected UpdateRow(String label, String note, String changelog, Button action) { this(label, note, changelog, action, null); }
    }

    protected final void renderExecution(java.util.function.Supplier<String> status, String baseVersion, String currentVersion) {
        renderExecution(status, baseVersion, currentVersion, () -> -1);
    }

    protected final void renderExecution(java.util.function.Supplier<String> status, String baseVersion, String currentVersion,
                                         java.util.function.DoubleSupplier fraction) {
        renderExecution(status, "dlc_manager.update.base", baseVersion, "dlc_manager.update.current", currentVersion, fraction);
    }

    protected final void renderExecution(java.util.function.Supplier<String> status,
                                         String firstKey, String firstVersion,
                                         String secondKey, String secondVersion,
                                         java.util.function.DoubleSupplier fraction) {
        content.parentElement.setAttribute("class", "page-shell update-page update-running-page");
        content.setAttribute("class", "update-running-content");
        var execution = node(content, "section", "update-execution");
        text(execution, tr("dlc_manager.update.execution_label"), "update-execution-label");
        var message = live(execution, () -> {
            String value = status.get();
            return value == null || value.isBlank() ? tr("dlc_manager.update.applying") : value;
        }, "update-execution-status");
        message.setAttribute("role", "status");
        message.setAttribute("aria-live", "polite");
        progress(execution, fraction);
        text(execution, tr("dlc_manager.update.execution_hint"), "update-execution-hint");
        var versions = node(execution, "div", "update-execution-versions");
        text(versions, tr(firstKey, firstVersion), "update-version");
        if (secondKey != null && !secondKey.isBlank()) {
            text(versions, tr(secondKey, secondVersion == null ? "" : secondVersion), "update-version");
        }
    }

    protected final Element updateSection(String baseVersion, String currentVersion) {
        return updateSection("dlc_manager.update.base", baseVersion, "dlc_manager.update.current", currentVersion);
    }

    protected final Element updateSection(String firstKey, String firstVersion,
                                          String secondKey, String secondVersion) {
        content.parentElement.setAttribute("class", "page-shell update-page");
        content.setAttribute("class", "update-content");
        var panel = node(content, "section", "update-panel");
        var versions = node(panel, "div", "update-versions");
        text(versions, tr(firstKey, firstVersion == null ? "" : firstVersion), "update-version");
        if (secondKey != null && !secondKey.isBlank()) {
            text(versions, tr(secondKey, secondVersion == null ? "" : secondVersion), "update-version");
        }
        return panel;
    }
    protected final void renderUpdates(Element list, List<UpdateRow> rows, boolean showEmpty) {
        double scroll = updateHistory == null ? 0 : updateHistory.getTargetScrollTop();
        var history = node(list, "div", "update-history");
        updateHistory = history;
        if (showEmpty && rows.isEmpty()) text(history, tr("dlc_manager.ui.empty_updates"), "empty-state");
        for (var update : rows) {
            var line = node(history, "div", "update-row");
            var copy = node(line, "div", "update-row-copy");
            text(copy, update.label, "update-transition");
            if (update.note != null && !update.note.isBlank()) text(copy, update.note, "update-note");
            if (update.changelog != null && !update.changelog.isBlank()) {
                String description = com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer.renderMarkdownComponent(update.changelog).getString();
                tip(line, () -> new DlcUiTooltip.Content(tr("dlc_manager.update.changelog_tooltip_title"), description, List.of(), ""));
            }
            var controls = node(line, "div", "row-controls");
            if (update.manageBackups != null) action(controls, tr("dlc_manager.update.world_manage"), update.manageBackups);
            if (update.action != null) mount(controls, update.action);
        }
        history.setScrollTop(scroll);
    }
    protected final void renderUpdateActions(Button... buttons) {
        for (var button : buttons) if (button != null) mount(footer, button);
    }
}
