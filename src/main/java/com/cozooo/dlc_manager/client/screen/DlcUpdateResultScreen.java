package com.cozooo.dlc_manager.client.screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DlcUpdateResultScreen extends DlcPageScreen {
    private final boolean success;
    private final Component message;
    public DlcUpdateResultScreen(Screen parent, boolean success, Component message) {
        super(parent, Component.translatable("dlc_manager.update.title")); this.success = success; this.message = message;
    }
    @Override protected void buildPage() {
        var body = node(content, "div", "page-dialog");
        rich(node(body, "div", success ? "page-message success" : "page-message error"), message);
        if (success) {
            text(body, tr("dlc_manager.update.restart"), "page-description");
            action(footer, tr("dlc_manager.restart_later"), () -> { if (pageParent instanceof DlcUpdateManagerScreen manager) manager.refreshData(); onClose(); });
            primary(tr("dlc_manager.restart_now"), () -> Minecraft.getInstance().stop());
        }
    }
}
