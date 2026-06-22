package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DlcServerPackUrlScreen extends Screen {
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
        super(Component.translatable("dlc_manager.pack.import_url_title"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        int fieldWidth = Math.min(320, this.width - 40);
        int fieldHeight = 18;
        int fieldX = this.width / 2 - fieldWidth / 2;
        int fieldY = this.height / 2 - 20;
        urlField = new EditBox(this.font, fieldX, fieldY, fieldWidth, fieldHeight, Component.empty());
        urlField.setMaxLength(Integer.MAX_VALUE);
        urlField.setFocused(true);
        urlField.setValue(urlValue);
        addRenderableWidget(urlField);

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = fieldY + 30;
        int leftX = this.width / 2 - buttonWidth - 10 - buttonWidth / 2;
        int centerX = this.width / 2 - buttonWidth / 2;
        int rightX = this.width / 2 + buttonWidth / 2 + 10;
        downloadButton = Button.builder(Component.translatable("dlc_manager.update.url_download"), btn -> startDownload())
            .bounds(leftX, buttonY, buttonWidth, buttonHeight)
            .build();
        pasteButton = Button.builder(Component.translatable("dlc_manager.update.url_paste"), btn -> pasteClipboard())
            .bounds(centerX, buttonY, buttonWidth, buttonHeight)
            .build();
        backButton = Button.builder(Component.translatable("dlc_manager.pack.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(rightX, buttonY, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(downloadButton);
        addRenderableWidget(pasteButton);
        addRenderableWidget(backButton);
        updateButtons();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int y = this.height / 2 - 80;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, y, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.pack.url_prompt"), centerX, y + 18, 0xC8C8C8);
        if (statusText != null && !statusText.isBlank()) {
            guiGraphics.drawCenteredString(this.font, statusText, centerX, this.height / 2 + 60, working ? 0xE6E6E6 : 0xFF6B6B);
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (urlField != null && urlField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (urlField != null && urlField.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        if (urlField != null) {
            urlValue = urlField.getValue();
        }
        super.resize(minecraft, width, height);
        if (urlField != null) {
            urlField.setValue(urlValue);
        }
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
            statusText = Component.translatable("dlc_manager.update.url_empty").getString();
            return;
        }
        working = true;
        statusText = Component.translatable("dlc_manager.pack.url_downloading").getString();
        updateButtons();
        new Thread(() -> {
            boolean ok;
            try {
                ok = DlcServerConnection.importPackPackageFromUrl(entry, 8000, url, null);
            } catch (Exception ex) {
                ok = false;
                statusText = Component.translatable("dlc_manager.update.url_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()).getString();
            }
            boolean success = ok;
            Minecraft.getInstance().execute(() -> {
                working = false;
                updateButtons();
                if (success) {
                    Minecraft.getInstance().setScreen(parent);
                    parent.refresh();
                } else if (statusText == null || statusText.isBlank() || Component.translatable("dlc_manager.pack.url_downloading").getString().equals(statusText)) {
                    statusText = Component.translatable("dlc_manager.pack.import_failed", "download failed").getString();
                }
            });
        }, "dlc-server-pack-url").start();
    }

    private void updateButtons() {
        if (downloadButton != null) downloadButton.active = !working;
        if (pasteButton != null) pasteButton.active = !working;
        if (backButton != null) backButton.active = !working;
    }
}
