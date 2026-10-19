package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

final class DlcUiRows {
    private DlcUiRows() {}

    static Element managerRow(Document document, Element entry, String name, boolean enabled, boolean pending) {
        entry.setAttribute("class", "dlc-row" + (enabled ? " enabled" : "") + (pending ? " pending" : ""));
        if (pending || enabled) text(document, entry, "span", "", "item-status-dot " + (pending ? "pending" : "enabled")).setAttribute("aria-hidden", "true");
        return text(document, entry, "span", name, "item-name");
    }

    static Element text(Document document, Element parent, String tag, String value, String className) {
        Element element = document.createElement(tag);
        element.setAttribute("class", className);
        DlcUiText.set(element, value);
        return parent.appendChild(element);
    }
}
