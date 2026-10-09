package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Element;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

public abstract class DlcRemotePageScreen extends DlcPageScreen {
    private final List<Runnable> selections = new ArrayList<>();
    private Element downloadList;
    private double scrollTop;

    protected DlcRemotePageScreen(Screen parent, Component title) { this(DlcUiPage.PAGE, parent, title); }
    protected DlcRemotePageScreen(DlcUiPage page, Screen parent, Component title) { super(page, parent, title); }

    protected final Element remoteList(BooleanSupplier selected, BooleanSupplier enabled, Runnable selectAll) {
        content.parentElement.setAttribute("class", "page-shell remote-page");
        content.setAttribute("class", "remote-content");
        selections.clear();
        text(content, tr("dlc_manager.remote.desc"), "remote-description");
        selection(node(content, "div", "remote-list-heading"), tr("dlc_manager.remote.select_all"), selected, enabled, selectAll);
        scrollTop = downloadList == null ? 0 : downloadList.getTargetScrollTop();
        downloadList = node(content, "div", "remote-list");
        return downloadList;
    }

    protected final Element remoteRow(Element list, String name, BooleanSupplier selected, BooleanSupplier enabled, Runnable change) {
        var row = node(list, "div", "remote-row");
        selection(row, name, selected, enabled, change);
        return row;
    }

    private void selection(Element parent, String name, BooleanSupplier selected, BooleanSupplier enabled, Runnable change) {
        var button = action(parent, "", () -> {
            if (enabled.getAsBoolean()) { change.run(); updateRemoteSelections(); }
        });
        var element = button.getElement();
        element.setAttribute("class", "button remote-selection");
        element.setAttribute("role", "checkbox");
        element.setAttribute("aria-label", name);
        var box = node(element, "span", "remote-checkbox");
        box.setAttribute("aria-hidden", "true");
        box.setInnerHTML("<svg class=\"remote-checkmark\" viewBox=\"0 0 16 16\"><path d=\"M3 8 L6.5 11.5 L13 4.5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"></path></svg>");
        rich(node(element, "span", "remote-name"), com.cozooo.dlc_manager.client.core.DlcTextFormatter.parse(name));
        Boolean[] previous = {null};
        Runnable sync = () -> {
            boolean checked = selected.getAsBoolean();
            if (previous[0] == null || previous[0] != checked) {
                previous[0] = checked;
                element.setAttribute("class", "button remote-selection" + (checked ? " checked" : ""));
                element.setAttribute("aria-checked", Boolean.toString(checked));
            }
            button.setEnabled(enabled.getAsBoolean());
        };
        selections.add(sync);
        sync.run();
    }

    protected final void remoteTip(Element owner, String name, java.util.Map<String, String> tips) {
        String language = com.cozooo.dlc_manager.core.DlcDcConfigIO.normalizeLangKey(com.cozooo.dlc_manager.platform.DlcPlatformServices.languageCode());
        String description = tips == null || tips.isEmpty() ? "" : tips.getOrDefault(language, tips.getOrDefault("en_us", tips.values().iterator().next()));
        tip(owner, () -> new DlcUiTooltip.Content(name, description, List.of(), ""));
    }

    protected final void updateRemoteSelections() { selections.forEach(Runnable::run); }

    protected final Element remoteStatus() {
        downloadList.setScrollTop(scrollTop);
        return node(content, "div", "remote-status");
    }

    protected final void remoteProgress(Element parent, DoubleSupplier value) {
        var meter = node(parent, "div", "remote-progress");
        progress(meter, value);
        live(meter, () -> Math.round(Math.max(0, Math.min(1, value.getAsDouble())) * 100) + "%", "remote-percent");
    }
}
