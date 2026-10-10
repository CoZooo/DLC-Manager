package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.DlcTextCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DlcUpdateChangelogScreen extends DlcPageScreen {
    private final String changelog;
    private final Runnable onDone;
    public DlcUpdateChangelogScreen(Screen parent, String changelog, Runnable onDone) {
        super(DlcUiPage.UPDATE_CHANGELOG, parent, DlcTextCompat.translatable("dlc_manager.update.changelog_title"));
        this.changelog = changelog == null ? "" : changelog; this.onDone = onDone;
    }
    @Override protected void buildPage() {
        markdown(node(content, "div", "page-prose"), changelog);
        primary(tr("dlc_manager.update.changelog_button"), this::onClose);
    }
    @Override public void onClose() {
        if (onDone != null) onDone.run();
        if (Minecraft.getInstance().screen == this) super.onClose();
    }
}
