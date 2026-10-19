package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element

internal object DlcUiRows {
    @JvmStatic fun managerRow(document: Document, entry: Element, name: String, enabled: Boolean, pending: Boolean): Element {
        entry.setAttribute("class", "dlc-row" + (if (enabled) " enabled" else "") + (if (pending) " pending" else ""))
        if (pending || enabled) text(document, entry, "span", "", "item-status-dot " + if (pending) "pending" else "enabled").setAttribute("aria-hidden", "true")
        return text(document, entry, "span", name, "item-name")
    }

    @JvmStatic fun text(document: Document, parent: Element, tag: String, value: String?, className: String): Element {
        val element = document.createElement(tag)
        element.setAttribute("class", className)
        DlcUiText.set(element, value)
        return parent.appendChild(element)
    }
}
