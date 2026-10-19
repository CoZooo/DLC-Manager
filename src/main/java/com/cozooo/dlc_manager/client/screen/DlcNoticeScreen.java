package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.DlcTextCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.cozooo.dlc_manager.client.notice.DlcNoticeConfig;
import java.util.concurrent.CompletableFuture;
public class DlcNoticeScreen extends DlcPageScreen {
    private final DlcNoticeConfig config;
    private boolean neverShow;
    private String language = "";
    private String resolvedContents = "";
    private CompletableFuture<String> future;
    public DlcNoticeScreen(Screen parent, DlcNoticeConfig config) {
        super(DlcUiPage.NOTICE, parent, DlcTextCompat.translatable("dlc_manager.notice_title")); this.config = config; neverShow = config.isNeverShow();
    }
    @Override protected boolean showHeaderBackButton() { return false; }
    @Override protected void buildPage() {
        String lang = Minecraft.getInstance().options.languageCode;
        if (!lang.equals(language)) resolve(lang);
        String title = config.getTitle().resolve(lang);
        DlcUiText.set(pageDocument.getElementById("page-title"), title.isBlank() ? getTitle().getString() : title);
        String subhead = config.getSubhead().enabled ? config.getSubhead().text.resolve(lang) : "";
        var headerSubhead = pageDocument.getElementById("page-subhead");
        if (headerSubhead != null) {
            DlcUiText.set(headerSubhead, subhead);
            headerSubhead.setAttribute("style", subhead.isBlank() ? "display:none" : "display:block");
        }
        var body = node(content, "article", "page-prose");
        markdown(body, resolvedContents);
        if (config.getShowFrequency() != DlcNoticeConfig.ShowFrequency.ONCE) {
            footerLeading.setAttribute("style", "display:flex");
            toggle(footerLeading, tr("dlc_manager.notice.never_show"), neverShow, value -> { neverShow = value; refreshPage(); });
        }
        primary(tr("dlc_manager.notice.read"), this::onClose);
        status(future == null ? "" : tr("dlc_manager.notice.loading"));
    }
    private void resolve(String lang) {
        language = lang;
        String fallback = config.getContents().resolve(lang);
        if (config.getContentUrl(lang).isBlank()) { resolvedContents = fallback; future = null; }
        else { resolvedContents = tr("dlc_manager.notice.loading"); future = CompletableFuture.supplyAsync(() -> config.resolveContents(lang)); }
    }
    @Override protected void updatePage() {
        if (!Minecraft.getInstance().options.languageCode.equals(language)) { refreshPage(); return; }
        if (future != null && future.isDone()) {
            try { resolvedContents = future.getNow(""); } catch (Exception ignored) { resolvedContents = ""; }
            if (resolvedContents == null || resolvedContents.isBlank()) resolvedContents = config.getContents().resolve(language);
            future = null; refreshPage();
        }
    }
    @Override public void onClose() {
        if (config.getShowFrequency() != DlcNoticeConfig.ShowFrequency.ONCE) { config.setNeverShow(neverShow); config.save(); }
        super.onClose();
    }
}
