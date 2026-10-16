package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Element
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.Locale

abstract class DlcPackPageScreen(page: DlcUiPage, parent: Screen?, title: Component) : DlcPageScreen(page, parent, title) {
    protected constructor(parent: Screen?, title: Component) : this(DlcUiPage.PAGE, parent, title)
    protected data class PackDetails(val name: Component, val description: Component, val authors: List<String>, val version: String, val icon: String?, val selectedKey: String)
    protected data class PackVersion(val key: String, val label: String)
    protected data class PackAction(val label: String, val danger: Boolean, val run: Runnable)
    protected interface PackAdapter<G> {
        fun details(group: G): PackDetails
        fun versions(group: G): List<PackVersion>
        fun actions(group: G): List<PackAction>
        fun selectVersion(group: G, key: String)
        fun move(index: Int, direction: Int)
    }
    protected abstract fun canEditPacks(): Boolean

    protected fun <G> renderPackRows(list: Element, groups: List<G>, adapter: PackAdapter<G>) {
        for ((index, group) in groups.withIndex()) {
            val pack = adapter.details(group)
            val line = node(list, "div", "page-row pack-row")
            val order = node(line, "div", "pack-order")
            action(order, "▲") { if (canEditPacks()) adapter.move(index, -1) }.setEnabled(canEditPacks() && index > 0)
            action(order, "▼") { if (canEditPacks()) adapter.move(index, 1) }.setEnabled(canEditPacks() && index + 1 < groups.size)
            pack.icon?.let { node(line, "texture", "pack-icon").setAttribute("src", it) }
                ?: text(line, pack.name.string.take(1).ifBlank { "P" }.uppercase(Locale.ROOT), "pack-icon pack-icon-placeholder")
            val copy = node(line, "div", "row-copy")
            rich(node(copy, "div", "row-title"), pack.name)
            text(copy, pack.authors.joinToString(", "), "row-description")
            val versionLine = node(copy, "div", "pack-version-line")
            text(versionLine, tr("dlc_manager.pack.version_prefix"), "pack-version-label")
            val versions = adapter.versions(group)
            if (versions.size > 1) {
                val select = node(versionLine, "select", "page-input pack-version")
                select.setAttribute("aria-label", tr("dlc_manager.pack.version_prefix"))
                for (version in versions) {
                    val option = node(select, "option", ""); option.setAttribute("value", version.key); option.setTextContent(version.label)
                }
                select.setValue(pack.selectedKey)
                select.addEventListener("change") { if (canEditPacks() && select.isConnected) adapter.selectVersion(group, select.getValue()) }
                if (!canEditPacks()) select.setAttribute("disabled", "")
            } else text(versionLine, pack.version, "pack-version-value")
            rich(node(copy, "div", "pack-description"), pack.description)
            val controls = node(line, "div", "row-controls")
            for (choice in adapter.actions(group)) {
                val button = action(controls, choice.label) { if (canEditPacks()) choice.run.run() }
                button.setEnabled(canEditPacks())
                if (choice.danger) button.getElement().setAttribute("class", "button danger")
            }
        }
    }

    protected fun renderPackFooter(importButton: Button?, urlButton: Button?, cancelButton: Button?, applyButton: Button?, pending: Boolean) {
        footerLeading.setAttribute("style", "display:flex")
        importButton?.let { mount(footerLeading, it) }
        urlButton?.let { mount(footerLeading, it) }
        if (pending) cancelButton?.let { mount(footer, it) }
        applyButton?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }
    }
}
