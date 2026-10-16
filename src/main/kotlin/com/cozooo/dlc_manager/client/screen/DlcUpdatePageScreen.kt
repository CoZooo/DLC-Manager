package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Element
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

abstract class DlcUpdatePageScreen(page: DlcUiPage, parent: Screen?, title: Component) : DlcPageScreen(page, parent, title) {
    protected constructor(parent: Screen?, title: Component) : this(DlcUiPage.PAGE, parent, title)
    private var updateHistory: Element? = null
    protected data class UpdateRow(val label: String, val note: String?, val changelog: String?, val action: Button?, val manageBackups: Runnable? = null)

    protected fun renderExecution(status: java.util.function.Supplier<String?>, baseVersion: String, currentVersion: String,
                                  fraction: java.util.function.DoubleSupplier = java.util.function.DoubleSupplier { -1.0 }) {
        content.parentElement.setAttribute("class", "page-shell update-page update-running-page")
        content.setAttribute("class", "update-running-content")
        val execution = node(content, "section", "update-execution")
        text(execution, tr("dlc_manager.update.execution_label"), "update-execution-label")
        val message = live(execution, { status.get()?.takeIf { it.isNotBlank() } ?: tr("dlc_manager.update.applying") }, "update-execution-status")
        message.setAttribute("role", "status")
        message.setAttribute("aria-live", "polite")
        progress(execution, fraction)
        text(execution, tr("dlc_manager.update.execution_hint"), "update-execution-hint")
        val versions = node(execution, "div", "update-execution-versions")
        text(versions, tr("dlc_manager.update.base", baseVersion), "update-version")
        text(versions, tr("dlc_manager.update.current", currentVersion), "update-version")
    }

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
            val controls = node(line, "div", "row-controls")
            update.manageBackups?.let { action(controls, tr("dlc_manager.update.world_manage"), it) }
            update.action?.let { mount(controls, it) }
        }
        history.setScrollTop(scroll)
    }
    protected fun renderUpdateActions(vararg buttons: Button?) {
        buttons.filterNotNull().forEach { mount(footer, it) }
    }
}
