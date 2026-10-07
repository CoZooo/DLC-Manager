package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Element
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

abstract class DlcUpdatePageScreen(parent: Screen?, title: Component) : DlcPageScreen(parent, title) {
    private var updateHistory: Element? = null
    protected data class UpdateRow(val label: String, val note: String?, val changelog: String?, val action: Button?)

    protected fun updateSection(baseVersion: String?, currentVersion: String?): Element {
        content.parentElement.setAttribute("class", "page-shell update-page")
        content.setAttribute("class", "update-content")
        val panel = node(content, "section", "update-panel")
        val versions = node(panel, "div", "update-versions")
        text(versions, tr("dlc_manager.update.base", baseVersion.orEmpty()), "update-version")
        text(versions, tr("dlc_manager.update.current", currentVersion.orEmpty()), "update-version")
        return panel
    }

    protected fun renderUpdates(list: Element, rows: List<UpdateRow>, showEmpty: Boolean) {
        val scroll = updateHistory?.targetScrollTop ?: 0.0
        val history = node(list, "div", "update-history")
        updateHistory = history
        if (showEmpty && rows.isEmpty()) text(history, tr("dlc_manager.ui.empty_updates"), "empty-state")
        for (update in rows) {
            val line = node(history, "div", "update-row")
            val copy = node(line, "div", "update-row-copy")
            text(copy, update.label, "update-transition")
            update.note?.takeIf { it.isNotBlank() }?.let { text(copy, it, "update-note") }
            update.changelog?.takeIf { it.isNotBlank() }?.let {
                val description = com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer.renderMarkdownComponent(it).string
                tip(line) { DlcUiTooltip.Content(tr("dlc_manager.update.changelog_tooltip_title"), description, emptyList(), "") }
            }
            update.action?.let { mount(node(line, "div", "row-controls"), it) }
        }
        history.setScrollTop(scroll)
    }
    protected fun renderUpdateActions(vararg buttons: Button?) {
        buttons.filterNotNull().forEach { mount(footer, it) }
    }
}
