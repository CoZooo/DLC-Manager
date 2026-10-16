package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Element;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Locale;

public abstract class DlcPackPageScreen extends DlcPageScreen {
    protected DlcPackPageScreen(Screen parent, Component title) { this(DlcUiPage.PAGE, parent, title); }
    protected DlcPackPageScreen(DlcUiPage page, Screen parent, Component title) { super(page, parent, title); }
    protected record PackDetails(Component name, Component description, List<String> authors, String version, String icon, String selectedKey) {}
    protected record PackVersion(String key, String label) {}
    protected record PackAction(String label, boolean danger, Runnable run) {}
    protected interface PackAdapter<G> {
        PackDetails details(G group);
        List<PackVersion> versions(G group);
        List<PackAction> actions(G group);
        void selectVersion(G group, String key);
        void move(int index, int direction);
    }
    protected abstract boolean canEditPacks();

    protected final <G> void renderPackRows(Element list, List<G> groups, PackAdapter<G> adapter) {
        for (int i = 0; i < groups.size(); i++) {
            final int index = i;
            G group = groups.get(i);
            PackDetails pack = adapter.details(group);
            var line = node(list, "div", "page-row pack-row");
            var order = node(line, "div", "pack-order");
            action(order, "▲", () -> { if (canEditPacks()) adapter.move(index, -1); }).setEnabled(canEditPacks() && index > 0);
            action(order, "▼", () -> { if (canEditPacks()) adapter.move(index, 1); }).setEnabled(canEditPacks() && index + 1 < groups.size());
            if (pack.icon != null) node(line, "texture", "pack-icon").setAttribute("src", pack.icon);
            else text(line, pack.name.getString().isBlank() ? "P" : pack.name.getString().substring(0, 1).toUpperCase(Locale.ROOT), "pack-icon pack-icon-placeholder");
            var copy = node(line, "div", "row-copy");
            rich(node(copy, "div", "row-title"), pack.name);
            text(copy, String.join(", ", pack.authors), "row-description");
            var versionLine = node(copy, "div", "pack-version-line");
            text(versionLine, tr("dlc_manager.pack.version_prefix"), "pack-version-label");
            var versions = adapter.versions(group);
            if (versions.size() > 1) {
                var select = node(versionLine, "select", "page-input pack-version");
                select.setAttribute("aria-label", tr("dlc_manager.pack.version_prefix"));
                for (var version : versions) {
                    var option = node(select, "option", ""); option.setAttribute("value", version.key); option.setTextContent(version.label);
                }
                select.setValue(pack.selectedKey);
                select.addEventListener("change", event -> { if (canEditPacks() && select.isConnected()) adapter.selectVersion(group, select.getValue()); });
                if (!canEditPacks()) select.setAttribute("disabled", "");
            } else text(versionLine, pack.version, "pack-version-value");
            rich(node(copy, "div", "pack-description"), pack.description);
            var controls = node(line, "div", "row-controls");
            for (var choice : adapter.actions(group)) {
                var button = action(controls, choice.label, () -> { if (canEditPacks()) choice.run.run(); });
                button.setEnabled(canEditPacks());
                if (choice.danger) button.getElement().setAttribute("class", "button danger");
            }
        }
    }

    protected final void renderPackFooter(Button importButton, Button urlButton, Button cancelButton, Button applyButton, boolean pending) {
        footerLeading.setAttribute("style", "display:flex");
        if (importButton != null) mount(footerLeading, importButton);
        if (urlButton != null) mount(footerLeading, urlButton);
        if (pending && cancelButton != null) mount(footer, cancelButton);
        if (applyButton != null) mount(footer, applyButton).getElement().setAttribute("class", "button primary");
    }
}
