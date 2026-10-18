package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public abstract class DlcFilePageScreen extends DlcPageScreen {
    private final List<Runnable> selections = new ArrayList<>();

    protected DlcFilePageScreen(Screen parent, Component title) { this(DlcUiPage.PAGE, parent, title); }
    protected DlcFilePageScreen(DlcUiPage page, Screen parent, Component title) { super(page, parent, title); }

    protected final Element fileList(String title) {
        selections.clear();
        return section(title, null);
    }

    protected final void fileRow(Element list, String name, String description, Supplier<String> checked,
                                 BooleanSupplier enabled, Runnable select, Runnable open) {
        fileRowLine(list, name, description, checked, enabled, select, open);
    }

    protected final Element fileRowLine(Element list, String name, String description, Supplier<String> checked,
                                        BooleanSupplier enabled, Runnable select, Runnable open) {
        Element line = node(list, "div", "page-row file-row");
        var button = action(line, "", () -> {
            if (enabled.getAsBoolean()) { select.run(); syncSelections(); }
        });
        Element box = button.getElement();
        box.setAttribute("class", "button file-checkbox");
        box.setAttribute("role", "checkbox");
        box.setAttribute("aria-label", tr("dlc_manager.ui.select") + " " + name);
        box.setInnerHTML("<svg class=\"file-checkmark\" viewBox=\"0 0 16 16\"><path d=\"M3 8 L6.5 11.5 L13 4.5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"></path></svg><span class=\"file-mixed\"></span>");
        Element copy = node(line, "div", "row-copy");
        text(copy, name, "row-title");
        if (description != null && !description.isBlank()) text(copy, description, "row-description");
        if (open != null) {
            Document document = pageDocument;
            long generation = document.getRefreshGeneration();
            line.setAttribute("class", "page-row file-row directory-row");
            line.addEventListener("dblclick", event -> {
                if (event.target instanceof Element target && box.contains(target)) return;
                if (enabled.getAsBoolean() && Minecraft.getInstance().screen == this
                    && document == getLinkedDocument() && document.isCurrentGeneration(generation)) {
                    event.preventDefault();
                    Document.runWithContext(document, open);
                }
            });
        }
        String[] previous = {null};
        Runnable sync = () -> {
            String state = checked.get();
            if (!state.equals(previous[0])) {
                previous[0] = state;
                box.setAttribute("aria-checked", state);
            }
            button.setEnabled(enabled.getAsBoolean());
        };
        selections.add(sync);
        sync.run();
        return line;
    }

    private void syncSelections() { selections.forEach(Runnable::run); }
    @Override protected void updatePage() { syncSelections(); }
}
