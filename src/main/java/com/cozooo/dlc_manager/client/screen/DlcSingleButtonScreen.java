package com.cozooo.dlc_manager.client.screen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DlcSingleButtonScreen extends DlcPageScreen {
    private final Component message;
    private final Component buttonLabel;
    public DlcSingleButtonScreen(Screen parent, Component title, Component message, Component buttonLabel) {
        super(DlcUiPage.SINGLE_BUTTON, parent, title); this.message = message; this.buttonLabel = buttonLabel;
    }
    @Override protected void buildPage() {
        rich(node(content, "div", "page-dialog"), message);
        String key = java.util.Objects.requireNonNullElse(DlcScreenCompat.translationKey(buttonLabel), "");
        if (!java.util.Set.of("gui.back", "dlc_manager.back", "dlc_manager.pack.back").contains(key)) primary(buttonLabel.getString(), this::onClose);
    }
}
