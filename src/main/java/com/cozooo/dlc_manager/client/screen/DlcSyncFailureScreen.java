package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.DlcTextCompat;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;

public final class DlcSyncFailureScreen extends DlcPageScreen {
    private final BooleanConsumer callback;
    private final String reason;
    private boolean answered;

    public DlcSyncFailureScreen(BooleanConsumer callback, String reason) {
        super(DlcUiPage.CONFIRM, Minecraft.getInstance().screen, DlcTextCompat.translatable("dlc_manager.sync.failed"));
        this.callback = callback;
        this.reason = reason;
    }
    @Override protected boolean showHeaderBackButton() { return false; }
    @Override protected void buildPage() {
        rich(node(content, "div", "page-dialog"), DlcTextCompat.translatable("dlc_manager.sync.failure_choice", reason));
        action(footer, tr("dlc_manager.sync.rollback"), () -> answer(true)).getElement().setAttribute("class", "button danger");
        primary(tr("dlc_manager.sync.keep"), () -> answer(false));
    }
    private void answer(boolean restore) {
        if (answered) return;
        answered = true;
        callback.accept(restore);
    }
    @Override public void onClose() { answer(false); }
}
