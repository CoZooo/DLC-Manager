package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.event.MouseEvent
import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element

class DlcUiButton(document: Document, container: Element, label: String, action: Runnable) {
    private val element: Element = container.appendChild(document.createElement("button"))
    private var enabled = true
    private var label: String? = null

    init {
        element.setAttribute("class", "button")
        element.setAttribute("type", "button")
        setDisplayString(label)
        element.addEventListener("mousedown") { event ->
            if (event is MouseEvent && event.button == 0 && enabled && !element.hasAttribute("disabled")) action.run()
        }
        element.addEventListener("click") { event ->
            if (event is MouseEvent && event.clickCount > 0) return@addEventListener
            if (enabled && !element.hasAttribute("disabled")) action.run()
        }
    }

    fun getElement(): Element = element

    fun setDisplayString(label: String) {
        if (this.label != label) {
            this.label = label
            element.setTextContent(label)
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled != enabled) {
            this.enabled = enabled
            if (enabled) element.removeAttribute("disabled") else element.setAttribute("disabled", "")
        }
    }
}
