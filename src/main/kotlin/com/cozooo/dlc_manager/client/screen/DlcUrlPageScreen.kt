package com.cozooo.dlc_manager.client.screen

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.function.DoubleSupplier
import java.util.function.Supplier

abstract class DlcUrlPageScreen(parent: Screen?, title: Component) : DlcPageScreen(parent, title) {
    protected fun renderUrlForm(prompt: String, field: EditBox?, paste: Button?, download: Button?,
                                message: Supplier<String?>, error: Supplier<String?>?,
                                progressValue: DoubleSupplier?, detail: Supplier<String?>?, downloading: java.util.function.BooleanSupplier) {
        val form = node(content, "div", "page-dialog")
        val inputRow = node(form, "div", "url-input-row")
        field?.let {
            val input = mount(inputRow, it, "", "url")
            input.setAttribute("placeholder", tr(prompt))
            input.setAttribute("aria-label", tr(prompt))
        }
        paste?.let { mount(inputRow, it) }
        live(form, message, "page-message")
        error?.let { live(form, it, "page-message error") }
        progressValue?.let {
            val downloadStatus = node(form, "div", "url-download-status")
            showWhen(downloadStatus, downloading)
            progress(downloadStatus, it)
            detail?.let { text -> live(downloadStatus, text, "page-description") }
        }
        download?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }
    }
}
