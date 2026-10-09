package com.cozooo.dlc_manager.client.screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
public class DlcRedirectConfirmScreen extends DlcPageScreen {
    private final String url;
    private final String notice;
    public DlcRedirectConfirmScreen(Screen parent, String url, String notice) {
        super(DlcUiPage.REDIRECT_CONFIRM, parent, Component.translatable("dlc_manager.redirect.title"));
        this.url = url == null ? "" : url; this.notice = notice == null ? "" : notice;
    }
    @Override protected void buildPage() {
        var body = node(content, "div", "page-dialog");
        text(body, tr("dlc_manager.redirect.header"), "section-title");
        text(body, tr("dlc_manager.redirect.prompt"), "page-description");
        text(body, url, "page-message success");
        if (!notice.isBlank()) text(body, notice, "page-message");
        action(footer, DlcConfirmUtil.confirmNo().getString(), this::onClose);
        action(footer, tr("dlc_manager.redirect.copy"), () -> { Minecraft.getInstance().keyboardHandler.setClipboard(url); onClose(); });
        primary(tr("dlc_manager.redirect.open"), () -> { Util.getPlatform().openUri(url); onClose(); });
    }
}
