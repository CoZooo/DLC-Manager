package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
//? if >=1.20.1 <26.1 {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if <1.20.1 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Widget;
*///?}
//? if >=26.1 {
//import net.minecraft.client.gui.GuiGraphicsExtractor;
//?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class DlcPageScreen extends DlcApricityScreen {
    protected final Screen pageParent;
    private final Component pageTitle;
    protected Document pageDocument;
    protected Element content;
    protected Element toolbar;
    protected Element footer;
    protected Element footerLeading;
    private DlcUiTooltip tooltip;
    private String lastStatus;
    private DlcUiButton backAction;
    private final java.util.List<Runnable> bindings = new java.util.ArrayList<>();

    protected DlcPageScreen(Screen parent, Component title) {
        this(DlcUiPage.PAGE, parent, title);
    }

    protected DlcPageScreen(DlcUiPage page, Screen parent, Component title) {
        super(page);
        pageParent = parent;
        pageTitle = title;
    }

    @Override public Component getTitle() { return pageTitle; }

    @Override protected final void bindDocument(Document document) {
        pageDocument = document;
        tooltip = new DlcUiTooltip(document);
        lastStatus = null;
        content = document.getElementById("page-content");
        toolbar = document.getElementById("page-toolbar");
        footer = document.getElementById("page-actions");
        footerLeading = document.getElementById("page-leading-actions");
        DlcUiText.set(document.getElementById("page-title"), pageTitle.getString());
        Element back = document.getElementById("page-back");
        clearUiContent(back);
        backAction = null;
        back.setAttribute("style", showHeaderBackButton() ? "" : "display:none");
        back.parentElement.setAttribute("data-has-back", Boolean.toString(showHeaderBackButton()));
        if (showHeaderBackButton()) {
            backAction = action(back, "‹", this::onClose);
            backAction.getElement().setAttribute("aria-label", tr("gui.back"));
        }
        refreshPage();
    }

    protected boolean showHeaderBackButton() { return true; }

    protected abstract void buildPage();
    protected void updatePage() {}

    protected final void refreshPage() {
        if (pageDocument == null || !pageDocument.isActive()) return;
        Document.runWithContext(pageDocument, () -> {
            double scroll = content.getScrollTop();
            tooltip.hide();
            bindings.clear();
            clearUiSlots();
            clearUiContent(content);
            clearUiContent(toolbar);
            clearUiContent(footer);
            clearUiContent(footerLeading);
            footerLeading.setAttribute("style", "display:none");
            buildPage();
            restoreUiClasses(content);
            restoreUiClasses(content.parentElement);
            restoreUiClasses(toolbar);
            restoreUiClasses(footer);
            restoreUiClasses(footerLeading);
            content.setScrollTop(scroll);
        });
    }

    @Override public void tick() {
        super.tick();
        if (pageDocument != null && pageDocument.isActive()) Document.runWithContext(pageDocument, () -> { if (backAction != null) backAction.setEnabled(shouldCloseOnEsc()); updatePage(); for (Runnable binding : java.util.List.copyOf(bindings)) binding.run(); });
    }

    //? if >=1.20.1 <26.1 {
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (tooltip != null && pageDocument != null) Document.runWithContext(pageDocument, tooltip::update);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    //?}

    //? if <1.20.1 {
    /*@Override public void render(PoseStack graphics, int mouseX, int mouseY, float partialTick) {
        if (tooltip != null && pageDocument != null) Document.runWithContext(pageDocument, tooltip::update);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    *///?}
    //? if >=26.1 {
    /*@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (tooltip != null && pageDocument != null) Document.runWithContext(pageDocument, tooltip::update);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    *///?}

    protected Element node(Element parent, String tag, String classes) {
        String slot = classes == null || classes.isBlank() ? "" : classes.strip().split("\\s+", 2)[0];
        Element target = slot.isEmpty() ? parent : uiSlot(slot, parent);
        Element element = target.appendChild(pageDocument.createElement(tag));
        element.setAttribute("class", classes);
        return element;
    }

    protected Element text(Element parent, String value, String classes) {
        Element element = node(parent, "div", classes);
        DlcUiText.set(element, value == null ? "" : value);
        return element;
    }

    protected Element section(String title, String description) {
        Element section = node(content, "section", "page-section");
        if (title != null && !title.isBlank()) text(section, title, "section-title");
        if (description != null && !description.isBlank()) text(section, description, "page-description");
        return section;
    }

    protected Element row(Element parent, String title, String description) {
        Element row = node(parent, "div", "page-row");
        Element label = node(row, "div", "row-copy");
        text(label, title, "row-title");
        if (description != null && !description.isBlank()) text(label, description, "row-description");
        return row;
    }

    protected DlcUiButton action(Element parent, String label, Runnable run) {
        return button(pageDocument, parent, label, run);
    }

    protected DlcUiButton primary(String label, Runnable run) {
        DlcUiButton button = action(uiSlot("primary-action", footer), label, run);
        button.getElement().setAttribute("class", "button primary");
        return button;
    }

    protected Element input(Element parent, String label, String value, String type, Consumer<String> change) {
        Element field = node(parent, "label", "form-field");
        if (!label.isBlank() && !type.equals("search")) text(field, label, "field-label");
        Element input = field.appendChild(new DlcUiTextInput(pageDocument));
        input.setAttribute("class", "page-input");
        input.setAttribute("type", type);
        input.setAttribute("aria-label", label);
        if (type.equals("search")) input.setAttribute("placeholder", label);
        input.setValue(value == null ? "" : value);
        bindInput(pageDocument, input, change, type.equals("search"));
        return input;
    }

    protected DlcUiButton toggle(Element parent, String label, boolean selected, Consumer<Boolean> change) {
        boolean[] state = {selected};
        DlcUiButton button = action(parent, label, () -> change.accept(!state[0]));
        button.getElement().setAttribute("class", "button choice" + (selected ? " selected" : ""));
        button.getElement().setAttribute("aria-pressed", String.valueOf(selected));
        return button;
    }

    //? if >=1.20.1 {
    @Override protected <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> T addRenderableWidget(T widget) {
        return widget;
    }
    //?}

    //? if <1.20.1 {
    /*@Override protected <T extends net.minecraft.client.gui.components.events.GuiEventListener & Widget & net.minecraft.client.gui.narration.NarratableEntry> T addRenderableWidget(T widget) {
        return widget;
    }
    *///?}

    protected DlcUiButton mount(Element parent, net.minecraft.client.gui.components.Button model) {
        DlcUiButton button = action(parent, model.getMessage().getString(), () -> { if (model.active && model.visible) DlcScreenCompat.press(model); });
        boolean[] shown = {true};
        Runnable binding = () -> {
            button.setDisplayString(model.getMessage().getString());
            button.setEnabled(model.active);
            if (shown[0] != model.visible) {
                shown[0] = model.visible;
                button.getElement().setAttribute("style", model.visible ? "" : "display:none");
            }
        };
        bindings.add(binding); binding.run();
        return button;
    }

    protected Element mount(Element parent, net.minecraft.client.gui.components.EditBox model, String label, String type) {
        Element field = input(parent, label, model.getValue(), type, model::setValue);
        Runnable binding = () -> {
            if (!field.getValue().equals(model.getValue())) field.setValue(model.getValue());
            if (model.active) field.removeAttribute("disabled"); else if (!field.hasAttribute("disabled")) field.setAttribute("disabled", "");
        };
        bindings.add(binding); binding.run();
        return field;
    }

    protected Element live(Element parent, Supplier<String> value, String classes) {
        Element element = text(parent, "", classes);
        String[] previous = {null};
        Runnable binding = () -> {
            String next = value.get();
            if (!java.util.Objects.equals(next, previous[0])) { previous[0] = next; DlcUiText.set(element, next == null ? "" : next); }
        };
        bindings.add(binding); binding.run();
        return element;
    }

    protected void showWhen(Element element, java.util.function.BooleanSupplier visible) {
        Boolean[] previous = {null};
        Runnable binding = () -> {
            boolean next = visible.getAsBoolean();
            if (previous[0] == null || previous[0] != next) {
                previous[0] = next;
                element.setAttribute("style", next ? "" : "display:none");
            }
        };
        bindings.add(binding);
        binding.run();
    }

    protected Element progress(Element parent, java.util.function.DoubleSupplier value) {
        Element track = node(parent, "div", "progress-track");
        track.setAttribute("role", "progressbar"); track.setAttribute("aria-valuemin", "0"); track.setAttribute("aria-valuemax", "100");
        Element fill = node(track, "div", "progress-fill");
        Runnable binding = DlcUiProgress.binding(track, fill, value);
        bindings.add(binding); binding.run(); return track;
    }

    protected void markdown(Element parent, String value) {
        rich(parent, com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer.renderMarkdownComponent(value));
    }

    protected void rich(Element parent, Component value) {
        value.visit((style, chunk) -> {
            String marker = style.getInsertion();
            if ("__dlc_hr__".equals(marker)) {
                node(parent, "div", "prose-rule");
                return java.util.Optional.empty();
            }
            Element span = node(parent, "span", "rich-text");
            StringBuilder css = new StringBuilder("display:inline;white-space:pre-wrap;");
            if (style.getColor() != null) css.append("color:").append(String.format(java.util.Locale.ROOT, "#%06x", style.getColor().getValue())).append(';');
            if (style.isBold()) css.append("font-weight:700;");
            if (style.isItalic()) css.append("font-style:italic;");
            if (style.isUnderlined()) css.append("text-decoration:underline;");
            if (style.isStrikethrough()) css.append("text-decoration:line-through;");
            if (marker != null && marker.startsWith("__dlc_heading__:")) {
                int level = Integer.parseInt(marker.substring(marker.lastIndexOf(':') + 1));
                css.append("font-size:").append(Math.max(16, 26 - level * 2)).append("px;");
            }
            if (style.getClickEvent() != null) {
                css.append("cursor:pointer;color:var(--accent);");
                span.addEventListener("click", event -> DlcScreenCompat.handleClick(this, style));
            } else css.append("pointer-events:none;");
            span.setAttribute("style", css.toString());
            span.setTextContent(chunk);
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
    }


    protected void tip(Element owner, Supplier<DlcUiTooltip.Content> content) { tooltip.bind(owner, content); }
    protected void status(String text) {
        String next = text == null ? "" : text;
        if (!java.util.Objects.equals(next, lastStatus)) { lastStatus = next; DlcUiText.set(pageDocument.getElementById("page-status"), next); }
    }
    protected static String tr(String key, Object... args) { return DlcTextCompat.translatable(key, args).getString(); }
    @Override public void onClose() { if (shouldCloseOnEsc()) Minecraft.getInstance().setScreen(pageParent); }

}
