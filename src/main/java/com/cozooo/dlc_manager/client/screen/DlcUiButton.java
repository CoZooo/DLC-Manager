package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

public final class DlcUiButton {
    private final Element element;
    private boolean enabled = true;
    private String label;

    public DlcUiButton(Document document, Element container, String label, Runnable action) {
        element = container.appendChild(document.createElement("button"));
        element.setAttribute("class", "button");
        element.setAttribute("type", "button");
        setDisplayString(label);
        element.addEventListener("click", event -> {
            if (enabled) action.run();
        });
    }

    public Element getElement() { return element; }

    public void setDisplayString(String label) {
        if (!java.util.Objects.equals(this.label, label)) {
            this.label = label;
            element.setTextContent(label);
        }
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) element.removeAttribute("disabled");
            else element.setAttribute("disabled", "");
        }
    }
}
