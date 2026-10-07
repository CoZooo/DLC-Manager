package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Element;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public abstract class DlcUpdatePageScreen extends DlcPageScreen {
    private Element updateHistory;
    protected DlcUpdatePageScreen(Screen parent, Component title) { super(parent, title); }
    protected record UpdateRow(String label, String note, String changelog, Button action) {}

    protected final Element updateSection(String baseVersion, String currentVersion) {
        content.parentElement.setAttribute("class", "page-shell update-page");
        content.setAttribute("class", "update-content");
        var panel = node(content, "section", "update-panel");
        var versions = node(panel, "div", "update-versions");
        text(versions, tr("dlc_manager.update.base", baseVersion), "update-version");
        text(versions, tr("dlc_manager.update.current", currentVersion), "update-version");
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
            if (update.action != null) mount(node(line, "div", "row-controls"), update.action);
        }
        history.setScrollTop(scroll);
    }
    protected final void renderUpdateActions(Button... buttons) {
        for (var button : buttons) if (button != null) mount(footer, button);
    }
}
