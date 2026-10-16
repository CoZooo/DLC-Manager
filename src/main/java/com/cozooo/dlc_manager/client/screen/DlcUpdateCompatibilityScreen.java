package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.platform.DlcTextCompat;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class DlcUpdateCompatibilityScreen extends DlcPageScreen {
    private final Screen parent;
    private final List<String> parameters;
    private final Consumer<DlcUpdatePackage.CompatibilityAction> proceed;
    private boolean answered;

    public DlcUpdateCompatibilityScreen(Screen parent, List<String> parameters, Consumer<DlcUpdatePackage.CompatibilityAction> proceed) {
        super(DlcUiPage.CONFIRM, parent, DlcTextCompat.translatable("dlc_manager.update.unknown_title"));
        this.parent = parent;
        this.parameters = List.copyOf(parameters);
        this.proceed = proceed;
    }

    @Override protected void buildPage() {
        text(content, tr("dlc_manager.update.unknown_description"), "page-warning");
        for (String parameter : parameters) text(content, parameter, "page-description");
        action(footer, tr("dlc_manager.update.cancel"), this::onClose);
        action(footer, tr("dlc_manager.update.ignore_unknown"), () -> answer(DlcUpdatePackage.CompatibilityAction.IGNORE));
        primary(tr("dlc_manager.update.update_manager_retry"), () -> answer(DlcUpdatePackage.CompatibilityAction.UPDATE_MANAGER));
    }

    private void answer(DlcUpdatePackage.CompatibilityAction action) {
        if (answered) return;
        answered = true;
        Minecraft.getInstance().setScreen(parent);
        proceed.accept(action);
    }

    @Override public void onClose() {
        if (answered) return;
        answered = true;
        Minecraft.getInstance().setScreen(parent);
    }
}
