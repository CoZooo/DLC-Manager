package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.render.Drawer;
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
    private Content renderedContent;
    private double pointerX, pointerY, placedPointerX = Double.NaN, placedPointerY = Double.NaN, placedWidth = -1, placedHeight = -1, viewportWidth = -1, viewportHeight = -1;
    private boolean visible;

    DlcUiTooltip(Document document) {
        this.document = document;
        generation = document.getRefreshGeneration();
        panel = document.body.appendChild(document.createElement("div"));
        panel.setAttribute("id", "dlc-tooltip");
        panel.setAttribute("class", "dlc-tooltip");
        panel.setAttribute("role", "tooltip");
        panel.setAttribute("aria-hidden", "true");
        panel.setAttribute("style", "display:block;visibility:hidden;left:0;top:0;transform:translate(0px,0px)");
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
        panel.setInlineStyleProperty("visibility", "hidden");
        panel.setAttribute("aria-hidden", "true");
    }

    void update() {
        if (!document.isCurrentGeneration(generation) || !visible && pending == null) return;
        double vw = document.getViewport().layoutWidth(), vh = document.getViewport().layoutHeight();
        if (pending != null && !visible) {
            Content content = pending.get();
            pending = null;
            if (!content.equals(renderedContent)) {
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
                renderedContent = new Content(content.title(), content.description(), List.copyOf(content.details()), content.hint());
                document.markDirty(document.documentElement, Drawer.REORDER);
            }
            visible = true;
            placedWidth = placedHeight = -1;
            panel.setScrollTop(0);
        }
        if (!visible) return;
        boolean resized = vw != viewportWidth || vh != viewportHeight;
        if (resized) {
            panel.setInlineStyleProperty("max-width", Math.max(1, vw - 24) + "px");
            panel.setInlineStyleProperty("max-height", Math.max(1, vh - 24) + "px");
        }
        if (placedWidth < 0 || resized) {
            document.commitPendingStyleRecalcForRender();
            document.commitRenderState();
        }
        double width = panel.getOffsetWidth(), height = panel.getOffsetHeight();
        if (width <= 0 || height <= 0) return;
        if (width == placedWidth && height == placedHeight && !resized && pointerX == placedPointerX && pointerY == placedPointerY) return;
        double left = pointerX + 16, top = pointerY + 20;
        if (left + width > vw - 12) left = pointerX - width - 16;
        if (top + height > vh - 12) top = pointerY - height - 12;
        left = Math.max(12, Math.min(left, Math.max(12, vw - width - 12)));
        top = Math.max(12, Math.min(top, Math.max(12, vh - height - 12)));
        panel.setInlineStyleProperty("transform", "translate(" + left + "px," + top + "px)");
        if (placedWidth < 0) {
            panel.setInlineStyleProperty("visibility", "visible");
            panel.setAttribute("aria-hidden", "false");
        }
        placedPointerX = pointerX;
        placedPointerY = pointerY;
        placedWidth = width;
        placedHeight = height;
        viewportWidth = vw;
        viewportHeight = vh;
    }

    private Element append(Element parent, String value, String className) {
        Element element = document.createElement("div");
        element.setAttribute("class", className);
        if (value != null && !value.isBlank()) DlcUiText.set(element, value);
        else if (!className.startsWith("tooltip-detail")) return element;
        return parent.appendChild(element);
    }
}
