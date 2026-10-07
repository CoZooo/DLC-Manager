package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import java.util.List;
import java.util.function.Supplier;

final class DlcUiTooltip {
    record Detail(String label, String text, boolean warning) {}
    record Content(String title, String description, List<Detail> details, String hint) {}

    private final Document document;
    private final long generation;
    private final Element panel;
    private Element owner;
    private Supplier<Content> pending;
    private double pointerX, pointerY, placedWidth = -1, placedHeight = -1, viewportWidth = -1, viewportHeight = -1;
    private double placedPointerX = Double.NaN, placedPointerY = Double.NaN;
    private boolean visible;

    DlcUiTooltip(Document document) {
        this.document = document;
        generation = document.getRefreshGeneration();
        panel = document.body.appendChild(document.createElement("div"));
        panel.setAttribute("id", "dlc-tooltip");
        panel.setAttribute("class", "dlc-tooltip");
        panel.setAttribute("role", "tooltip");
        panel.setAttribute("aria-hidden", "true");
        panel.setAttribute("style", "display:none");
        panel.setTopLayer(true);
        document.addEventListener("wheel", event -> {
            if (visible && event instanceof MouseEvent wheel && event.target instanceof Element target
                && owner != null && owner.contains(target)) {
                panel.commitScrollMetricsFromLayout();
                double limit = Math.max(0, panel.scrollHeight - Box.of(panel).innerSize().height());
                if (limit > 0) {
                    event.preventDefault();
                    panel.scrollTo(0, Math.max(0, Math.min(limit, panel.getTargetScrollTop() + wheel.scrollDelta)));
                    return;
                }
            }
            hide();
        });
    }

    void bind(Element target, Supplier<Content> content) {
        target.setAttribute("aria-describedby", "dlc-tooltip");
        target.addEventListener("mouseenter", event -> {
            if (!(event instanceof MouseEvent mouse) || !document.isCurrentGeneration(generation)) return;
            hide();
            owner = target;
            pending = content;
            pointerX = mouse.clientX;
            pointerY = mouse.clientY;
        });
        target.addEventListener("mousemove", event -> {
            if (owner != target || !(event instanceof MouseEvent mouse)) return;
            pointerX = mouse.clientX;
            pointerY = mouse.clientY;
        });
        target.addEventListener("mouseleave", event -> {
            if (owner == target) hide();
        });
    }

    void hide() {
        pending = null;
        owner = null;
        if (!visible) return;
        visible = false;
        panel.setAttribute("style", "display:none");
        panel.setAttribute("aria-hidden", "true");
    }

    void update() {
        if (!document.isCurrentGeneration(generation)) return;
        double vw = document.getViewport().layoutWidth(), vh = document.getViewport().layoutHeight();
        String limits = "max-width:" + Math.max(1, vw - 24) + "px;max-height:" + Math.max(1, vh - 24) + "px";
        if (pending != null && !visible) {
            Content content = pending.get();
            pending = null;
            panel.setInnerHTML("");
            append(panel, content.title(), "tooltip-title");
            append(panel, content.description(), "tooltip-description");
            for (Detail detail : content.details()) {
                if (detail.text() == null || detail.text().isBlank()) continue;
                Element section = append(panel, "", "tooltip-detail" + (detail.warning() ? " warning" : ""));
                append(section, detail.label(), "tooltip-label");
                append(section, detail.text(), "tooltip-value");
            }
            append(panel, content.hint(), "tooltip-hint");
            visible = true;
            placedWidth = placedHeight = viewportWidth = viewportHeight = -1;
            panel.setAttribute("style", "display:block;visibility:hidden;" + limits);
            panel.setScrollTop(0);
        }
        if (!visible) return;
        double width = panel.getOffsetWidth(), height = panel.getOffsetHeight();
        if (width <= 0 || height <= 0) return;
        if (width == placedWidth && height == placedHeight && vw == viewportWidth && vh == viewportHeight
            && pointerX == placedPointerX && pointerY == placedPointerY) return;
        double left = pointerX + 16, top = pointerY + 20;
        if (left + width > vw - 12) left = pointerX - width - 16;
        if (top + height > vh - 12) top = pointerY - height - 12;
        left = Math.max(12, Math.min(left, Math.max(12, vw - width - 12)));
        top = Math.max(12, Math.min(top, Math.max(12, vh - height - 12)));
        panel.setAttribute("style", "display:block;visibility:visible;left:" + left + "px;top:" + top + "px;" + limits);
        panel.setAttribute("aria-hidden", "false");
        placedWidth = width;
        placedHeight = height;
        viewportWidth = vw;
        viewportHeight = vh;
        placedPointerX = pointerX;
        placedPointerY = pointerY;
    }

    private Element append(Element parent, String value, String className) {
        Element element = document.createElement("div");
        element.setAttribute("class", className);
        if (value != null && !value.isBlank()) DlcUiText.set(element, value);
        else if (!className.startsWith("tooltip-detail")) return element;
        return parent.appendChild(element);
    }
}
