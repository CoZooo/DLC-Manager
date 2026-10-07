package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public abstract class DlcUrlPageScreen extends DlcPageScreen {
    protected DlcUrlPageScreen(Screen parent, Component title) { super(parent, title); }

    protected final void renderUrlForm(String prompt, EditBox field, Button paste, Button download,
                                       Supplier<String> message, Supplier<String> error,
                                       DoubleSupplier progressValue, Supplier<String> detail, java.util.function.BooleanSupplier downloading) {
        var form = node(content, "div", "page-dialog");
        var inputRow = node(form, "div", "url-input-row");
        if (field != null) {
            var input = mount(inputRow, field, "", "url");
            input.setAttribute("placeholder", tr(prompt));
            input.setAttribute("aria-label", tr(prompt));
        }
        if (paste != null) mount(inputRow, paste);
        live(form, message, "page-message");
        if (error != null) live(form, error, "page-message error");
        if (progressValue != null) {
            var downloadStatus = node(form, "div", "url-download-status");
            showWhen(downloadStatus, downloading);
            progress(downloadStatus, progressValue);
            if (detail != null) live(downloadStatus, detail, "page-description");
        }
        if (download != null) mount(footer, download).getElement().setAttribute("class", "button primary");
    }
}
