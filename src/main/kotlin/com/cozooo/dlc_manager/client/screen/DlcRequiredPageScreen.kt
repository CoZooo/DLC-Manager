package com.cozooo.dlc_manager.client.screen

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.function.DoubleSupplier
import java.util.function.Supplier

abstract class DlcRequiredPageScreen(parent: Screen?, title: Component) : DlcPageScreen(parent, title) {
    protected data class ResourceRow(val name: String, val description: Supplier<String?>, val tip: Supplier<String>,
                                    val button: Button?, val progress: DoubleSupplier)

    protected fun renderResources(rows: List<ResourceRow>, error: Supplier<String?>?,
                                  showEmpty: Boolean, applyAll: Button?, totalProgress: DoubleSupplier) {
        val list = section(null, null)
        error?.let { live(list, it, "page-error") }
        if (showEmpty && rows.isEmpty()) text(list, tr("dlc_manager.ui.empty_resources"), "page-description")
        for (resource in rows) {
            val item = row(list, resource.name, null)
            live(item.children[0], resource.description, "row-description")
            tip(item) { DlcUiTooltip.Content(resource.name, resource.tip.get(), emptyList(), "") }
            val controls = node(item, "div", "row-controls")
            resource.button?.let { mount(controls, it) }
            progress(controls, resource.progress)
        }
        applyAll?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }
        progress(content, totalProgress)
    }
}
