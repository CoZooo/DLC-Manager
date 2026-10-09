package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DlcManagerPageScreen extends DlcApricityScreen {
    protected DlcManagerPageScreen() { this(DlcUiPage.MANAGER); }
    protected DlcManagerPageScreen(DlcUiPage page) { super(page); }
    protected DlcUiButton searchClearButton;
    protected String searchQuery = "";
    protected DlcUiTooltip tooltip;
    private Document layoutDocument;
    private long layoutGeneration = -1;
    private boolean sidebarCollapsed;
    private double sidebarWidth = 172;
    private double rowHeight = 40;
    private final DlcUiMotion sidebarMotion = new DlcUiMotion(1);
    private double lastSidebarProgress = Double.NaN;
    private final Map<String, DlcUiMotion> groupMotions = new HashMap<>();
    private final List<GroupView> groupViews = new ArrayList<>();

    private static final class GroupView {
        final DlcUiMotion motion;
        final Element body, button, caret;
        final int itemCount;
        double lastProgress = Double.NaN;

        GroupView(DlcUiMotion motion, Element body, Element button, Element caret, int itemCount) {
            this.motion = motion;
            this.body = body;
            this.button = button;
            this.caret = caret;
            this.itemCount = itemCount;
        }
    }

    protected void bindManagerLayout(Document document, String title) {
        Element manager = document.getElementById("manager");
        sidebarWidth = layoutMetric(manager, "data-sidebar-width", 172);
        rowHeight = layoutMetric(manager, "data-row-height", 40);
        document.getElementById("manager-header").setAttribute("class", "workspace-header");
        restoreUiClasses(document.getElementById("manager-header"));
        DlcUiText.set(document.getElementById("manager-title"), title);
        document.getElementById("category-label").setTextContent(layoutTranslate("dlc_manager.ui.categories"));
        document.getElementById("tools-label").setTextContent(layoutTranslate("dlc_manager.ui.tools"));
        if (layoutDocument != document || layoutGeneration != document.getRefreshGeneration()) {
            layoutDocument = document;
            layoutGeneration = document.getRefreshGeneration();
            tooltip = new DlcUiTooltip(document);
            bindSidebar(document);
            bindSearch(document);
        }
    }

    protected abstract void onManagerSearch(Document document, String query);
    protected void clearManagerGroups() { groupViews.clear(); }

    protected Element addManagerGroup(Document document, Element list, Element heading, String key, int itemCount) {
        DlcUiMotion motion = groupMotions.computeIfAbsent(key, ignored -> new DlcUiMotion(1));
        Element body = list.appendChild(document.createElement("div"));
        body.setAttribute("class", "group-items");
        body.setAttribute("id", "dlc-group-" + groupViews.size());
        DlcUiButton collapse = button(document, heading, "", () -> {
            if (tooltip != null) tooltip.hide();
            motion.animateTo(motion.getTarget() == 0 ? 1 : 0, System.nanoTime());
            updateAnimations(document, System.nanoTime());
        });
        collapse.getElement().setAttribute("class", "button group-collapse");
        collapse.getElement().setAttribute("aria-controls", body.getAttribute("id"));
        Element caret = managerText(document, collapse.getElement(), "span", "", "group-caret");
        caret.setAttribute("aria-hidden", "true");
        caret.setInnerHTML("<svg viewBox=\"0 0 16 16\"><path d=\"M3 10 L8 5 L13 10\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.5\"></path></svg>");
        groupViews.add(new GroupView(motion, body, collapse.getElement(), caret, itemCount));
        return body;
    }

    protected Element styleManagerRow(Document document, Element entry, String name, boolean enabled, boolean pending) {
        entry.setAttribute("class", "dlc-row" + (enabled ? " enabled" : "") + (pending ? " pending" : ""));
        if (pending || enabled) managerText(document, entry, "span", "", "item-status-dot " + (pending ? "pending" : "enabled")).setAttribute("aria-hidden", "true");
        return managerText(document, entry, "span", name, "item-name");
    }

    protected DlcUiButton managerToggle(Document document, Element entry, boolean enabled, Runnable action) {
        DlcUiButton toggle = button(document, entry, layoutTranslate(enabled ? "dlc_manager.toggle.on" : "dlc_manager.toggle.off"), action);
        toggle.getElement().setAttribute("class", "button toggle" + (enabled ? " on" : ""));
        toggle.getElement().setAttribute("aria-pressed", Boolean.toString(enabled));
        return toggle;
    }

    private void bindSidebar(Document document) {
        Element sidebar = document.getElementById("sidebar-content");
        long generation = document.getRefreshGeneration();
        sidebar.addEventListener("wheel", event -> {
            if (!(event instanceof MouseEvent wheel) || Minecraft.getInstance().screen != this
                || getLinkedDocument() != document || !document.isCurrentGeneration(generation)) return;
            event.preventDefault();
            Document.runWithContext(document, () -> {
                sidebar.commitScrollMetricsFromLayout();
                double limit = Math.max(0, sidebar.scrollHeight - Box.of(sidebar).innerSize().height());
                sidebar.scrollTo(0, Math.max(0, Math.min(limit, sidebar.getTargetScrollTop() + wheel.scrollDelta)));
            });
        });
        Element slot = document.getElementById("sidebar-toggle-slot");
        clearUiContent(slot);
        DlcUiButton toggle = button(document, slot, "", () -> {
            double scroll = document.getElementById("manager-list").getTargetScrollTop();
            sidebarCollapsed = !sidebarCollapsed;
            sidebarMotion.animateTo(sidebarCollapsed ? 0 : 1, System.nanoTime());
            renderSidebarState(document);
            document.getElementById("manager-list").setScrollTop(scroll);
        });
        toggle.getElement().setAttribute("id", "sidebar-toggle");
        toggle.getElement().setAttribute("class", "button sidebar-toggle");
        toggle.getElement().setAttribute("aria-controls", "manager-sidebar");
        renderSidebarState(document);
        lastSidebarProgress = Double.NaN;
        updateAnimations(document, System.nanoTime());
    }

    private void renderSidebarState(Document document) {
        document.getElementById("manager").setAttribute("class", sidebarCollapsed ? "manager sidebar-collapsed" : "manager");
        restoreUiClasses(document.getElementById("manager"));
        Element toggle = document.getElementById("sidebar-toggle");
        toggle.setTextContent(sidebarCollapsed ? "»" : "«");
        toggle.setAttribute("aria-expanded", Boolean.toString(!sidebarCollapsed));
        toggle.setAttribute("aria-label", layoutTranslate(sidebarCollapsed ? "dlc_manager.ui.expand_sidebar" : "dlc_manager.ui.collapse_sidebar"));
        document.getElementById("manager-sidebar").setAttribute("aria-hidden", Boolean.toString(sidebarCollapsed));
    }

    private void bindSearch(Document document) {
        Element input = DlcUiTextInput.adapt(document.getElementById("manager-search"));
        input.setValue(searchQuery);
        input.setAttribute("placeholder", layoutTranslate("dlc_manager.ui.search"));
        input.setAttribute("aria-label", layoutTranslate("dlc_manager.ui.search"));
        DlcUiInputBinding searchBinding = bindInput(document, input, query -> onManagerSearch(document, query), true);
        Element actions = document.getElementById("search-actions");
        clearUiContent(actions);
        searchClearButton = button(document, actions, "×", () -> {
            input.setValue("");
            searchBinding.run();
        });
        searchClearButton.getElement().setAttribute("class", "button clear-search");
        searchClearButton.getElement().setAttribute("aria-label", layoutTranslate("dlc_manager.ui.clear_search"));
        searchClearButton.setEnabled(!searchQuery.isBlank());
    }

    protected void updateAnimations(Document document, long now) {
        Document.runWithContext(document, () -> {
            double progress = sidebarMotion.valueAt(now);
            if (progress != lastSidebarProgress) {
                document.getElementById("manager-sidebar").setAttribute("style",
                    "width:" + (sidebarWidth * progress) + "px;border-right-width:" + progress + "px;opacity:" + progress);
                lastSidebarProgress = progress;
            }
            for (GroupView group : groupViews) {
                double value = group.motion.valueAt(now);
                if (value == group.lastProgress) continue;
                boolean expanded = group.motion.getTarget() == 1;
                group.body.setAttribute("style", "height:" + (value == 1 ? "auto" : (group.itemCount * rowHeight * value) + "px")
                    + ";opacity:" + value + ";pointer-events:" + (expanded ? "auto" : "none"));
                group.body.setAttribute("aria-hidden", Boolean.toString(!expanded));
                group.button.setAttribute("aria-expanded", Boolean.toString(expanded));
                group.button.setAttribute("aria-label", layoutTranslate(expanded ? "dlc_manager.ui.collapse_group" : "dlc_manager.ui.expand_group"));
                group.caret.setAttribute("style", "transform:rotate(" + ((1 - value) * 180) + "deg)");
                group.lastProgress = value;
            }
        });
    }

    protected static Element managerText(Document document, Element parent, String tag, String value, String className) {
        Element element = document.createElement(tag);
        element.setAttribute("class", className);
        DlcUiText.set(element, value);
        return parent.appendChild(element);
    }


    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Document document = getLinkedDocument();
        if (document != null) {
            updateAnimations(document, System.nanoTime());
            Document.runWithContext(document, () -> { if (tooltip != null) tooltip.update(); });
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String layoutTranslate(String key) { return Component.translatable(key).getString(); }

    private static double layoutMetric(Element element, String name, double fallback) {
        try {
            double value = Double.parseDouble(element.getAttribute(name));
            return Double.isFinite(value) && value > 0 && value <= 10000 ? value : fallback;
        } catch (NumberFormatException | NullPointerException ignored) {
            return fallback;
        }
    }
}
