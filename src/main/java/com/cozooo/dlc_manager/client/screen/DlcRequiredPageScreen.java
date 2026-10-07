package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public abstract class DlcRequiredPageScreen extends DlcPageScreen {
    protected DlcRequiredPageScreen(Screen parent, Component title) { super(parent, title); }
    protected record ResourceRow(String name, Supplier<String> description, Supplier<String> tip,
                                 Button button, DoubleSupplier progress) {}

    protected final void renderResources(List<ResourceRow> rows, Supplier<String> error,
                                         boolean showEmpty, Button applyAll, DoubleSupplier totalProgress) {
        var list = section(null, null);
        if (error != null) live(list, error, "page-error");
        if (showEmpty && rows.isEmpty()) text(list, tr("dlc_manager.ui.empty_resources"), "page-description");
        for (var resource : rows) {
            var item = row(list, resource.name, null);
            live(item.getChildren().get(0), resource.description, "row-description");
            tip(item, () -> new DlcUiTooltip.Content(resource.name, resource.tip.get(), List.of(), ""));
            var controls = node(item, "div", "row-controls");
            if (resource.button != null) mount(controls, resource.button);
            progress(controls, resource.progress);
        }
        if (applyAll != null) mount(footer, applyAll).getElement().setAttribute("class", "button primary");
        progress(content, totalProgress);
    }
}
