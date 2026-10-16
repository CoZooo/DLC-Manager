package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class DlcServerPackUrlScreen extends DlcUrlPageScreen {
    private final DlcServerPackManagerScreen parent;
    private final DlcServerEntry entry;
    private EditBox urlField;
    private Button downloadButton;
    private Button pasteButton;
    private Button backButton;
    private boolean working;
    private String urlValue = "";
    private String statusText;

    public DlcServerPackUrlScreen(DlcServerPackManagerScreen parent, DlcServerEntry entry) {
        super(DlcUiPage.SERVER_PACK_URL, parent, DlcTextCompat.translatable("dlc_manager.pack.import_url_title"));
        this.parent = parent;
        this.entry = entry;
    }

    private void initControls() {
        int fieldWidth = Math.min(320, this.width - 40);
        int fieldHeight = 18;
        int fieldX = this.width / 2 - fieldWidth / 2;
        int fieldY = this.height / 2 - 20;
        urlField = new EditBox(this.font, fieldX, fieldY, fieldWidth, fieldHeight, DlcTextCompat.empty());
        urlField.setMaxLength(Integer.MAX_VALUE);
        DlcScreenCompat.setFocused(urlField, true);
        urlField.setValue(urlValue);
        addRenderableWidget(urlField);

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = fieldY + 30;
        int leftX = this.width / 2 - buttonWidth - 10 - buttonWidth / 2;
        int centerX = this.width / 2 - buttonWidth / 2;
        int rightX = this.width / 2 + buttonWidth / 2 + 10;
        downloadButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.update.url_download"), btn -> startDownload())
            .bounds(leftX, buttonY, buttonWidth, buttonHeight)
            .build();
        pasteButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.update.url_paste"), btn -> pasteClipboard())
            .bounds(centerX, buttonY, buttonWidth, buttonHeight)
            .build();
        backButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.pack.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(rightX, buttonY, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(downloadButton);
        addRenderableWidget(pasteButton);
        addRenderableWidget(backButton);
        updateButtons();
    }

    private void pasteClipboard() {
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard != null) {
            urlField.setValue(clipboard.trim());
        }
    }

    private void startDownload() {
        if (working) {
            return;
        }
        String url = urlField.getValue() == null ? "" : urlField.getValue().trim();
        if (url.isBlank()) {
            statusText = DlcTextCompat.translatable("dlc_manager.update.url_empty").getString();
            return;
        }
        working = true;
        statusText = DlcTextCompat.translatable("dlc_manager.pack.url_downloading").getString();
        updateButtons();
        new Thread(() -> {
            boolean ok;
            try {
                ok = DlcServerConnection.importPackPackageFromUrl(entry, 8000, url, null);
            } catch (Exception ex) {
                ok = false;
                statusText = DlcTextCompat.translatable("dlc_manager.update.url_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()).getString();
            }
            boolean success = ok;
            Minecraft.getInstance().execute(() -> {
                working = false;
                updateButtons();
                if (success) {
                    Minecraft.getInstance().setScreen(parent);
                    parent.refresh();
                } else if (statusText == null || statusText.isBlank() || DlcTextCompat.translatable("dlc_manager.pack.url_downloading").getString().equals(statusText)) {
                    statusText = DlcTextCompat.translatable("dlc_manager.pack.import_failed", "download failed").getString();
                }
            });
        }, "dlc-server-pack-url").start();
    }

    private void updateButtons() {
        if (downloadButton != null) downloadButton.active = !working;
        if (pasteButton != null) pasteButton.active = !working;
        if (backButton != null) backButton.active = !working;
    }

    @Override protected void buildPage() {
        if (urlField != null) urlValue = urlField.getValue();
        initControls();
        renderUrlForm("dlc_manager.pack.url_prompt", urlField, pasteButton, downloadButton,
                () -> statusText, null, null, null, () -> working);
    }
    @Override protected void updatePage() { updateButtons(); if (urlField != null) urlField.active = !working; }

    @Override public boolean shouldCloseOnEsc() { return !working; }
}
