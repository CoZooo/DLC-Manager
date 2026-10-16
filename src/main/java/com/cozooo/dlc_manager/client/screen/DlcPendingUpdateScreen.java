package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.DlcTextCompat;
import net.minecraft.client.gui.screens.Screen;

public final class DlcPendingUpdateScreen extends DlcPageScreen {
    private String status;

    public DlcPendingUpdateScreen(Screen parent) {
        super(DlcUiPage.UPDATE_RESULT, parent, DlcTextCompat.translatable("dlc_manager.update.title"));
        status = DlcTextCompat.translatable("dlc_manager.update.pending_applying").getString();
    }

    public void setStatus(String status) { this.status = status; }
    @Override protected void buildPage() { live(content, () -> status, "page-description"); }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void onClose() { }
}
