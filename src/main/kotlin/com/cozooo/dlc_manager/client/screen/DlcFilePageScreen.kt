package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.function.BooleanSupplier
import java.util.function.Supplier

abstract class DlcFilePageScreen(parent: Screen?, title: Component) : DlcPageScreen(parent, title) {
    private val selections = mutableListOf<Runnable>()

    protected fun fileList(title: String?): Element {
        selections.clear()
        return section(title, null)
    }

    protected fun fileRow(list: Element, name: String, description: String?, checked: Supplier<String>,
                          enabled: BooleanSupplier, select: Runnable, open: Runnable?) {
        val line = node(list, "div", "page-row file-row")
        val button = action(line, "") {
            if (enabled.asBoolean) { select.run(); syncSelections() }
        }
        val box = button.getElement()
        box.setAttribute("class", "button file-checkbox")
        box.setAttribute("role", "checkbox")
        box.setAttribute("aria-label", tr("dlc_manager.ui.select") + " " + name)
        box.setInnerHTML("<svg class=\"file-checkmark\" viewBox=\"0 0 16 16\"><path d=\"M3 8 L6.5 11.5 L13 4.5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"></path></svg><span class=\"file-mixed\"></span>")
        val copy = node(line, "div", "row-copy")
        text(copy, name, "row-title")
        if (!description.isNullOrBlank()) text(copy, description, "row-description")
        if (open != null) {
            val document = pageDocument
            val generation = document.refreshGeneration
            line.setAttribute("class", "page-row file-row directory-row")
            line.addEventListener("dblclick") { event ->
                val target = event.target
                if (target is Element && box.contains(target)) return@addEventListener
                if (enabled.asBoolean && Minecraft.getInstance().screen === this
                    && document === getLinkedDocument() && document.isCurrentGeneration(generation)) {
                    event.preventDefault()
                    Document.runWithContext(document, open)
                }
            }
        }
        var previous: String? = null
        val sync = Runnable {
            val state = checked.get()
            if (state != previous) {
                previous = state
                box.setAttribute("aria-checked", state)
            }
            button.setEnabled(enabled.asBoolean)
        }
        selections.add(sync)
        sync.run()
    }

    private fun syncSelections() { selections.forEach { it.run() } }
    override fun updatePage() { syncSelections() }
}
