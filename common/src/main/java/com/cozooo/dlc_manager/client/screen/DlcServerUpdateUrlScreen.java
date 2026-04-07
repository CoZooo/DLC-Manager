package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class DlcServerUpdateUrlScreen extends Screen {
    private final Screen parent;
    private final DlcServerEntry entry;
    private EditBox urlField;
    private Button downloadButton;
    private Button pasteButton;
    private Button backButton;
    private volatile boolean downloading;
    private volatile float progress;
    private volatile String errorText;
    private volatile String statusText;
    private volatile long totalBytes;
    private volatile long downloadedBytes;
    private String urlValue = "";

    public DlcServerUpdateUrlScreen(Screen parent, DlcServerEntry entry) {
        super(Component.translatable("dlc_manager.update.import_url_title"));
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
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(rightX, buttonY, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(downloadButton);
        addRenderableWidget(pasteButton);
        addRenderableWidget(backButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int y = this.height / 2 - 80;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, y, 0xFFFFFF);
        y += 18;
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.url_prompt"), centerX, y, 0xC8C8C8);

        if (downloading) {
            int barY = this.height / 2 + 60;
            String status = statusText == null ? "" : statusText;
            guiGraphics.drawCenteredString(this.font, status, centerX, barY - 14, 0xE6E6E6);
            drawProgressBar(guiGraphics, centerX - 120, barY, 240, 8, progress);
            int percent = totalBytes > 0 ? Math.round(Math.max(0f, Math.min(1f, progress)) * 100f) : 0;
            guiGraphics.drawCenteredString(this.font, percent + "%", centerX, barY + 10, 0xC8C8C8);
        } else if (errorText != null && !errorText.isBlank()) {
            guiGraphics.drawCenteredString(this.font, errorText, centerX, this.height / 2 + 60, 0xFF6B6B);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
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
        if (downloading) {
            return;
        }
        String url = urlField.getValue() == null ? "" : urlField.getValue().trim();
        if (url.isBlank()) {
            errorText = Component.translatable("dlc_manager.update.url_empty").getString();
            return;
        }
        downloading = true;
        progress = 0f;
        downloadedBytes = 0L;
        totalBytes = 0L;
        errorText = null;
        statusText = Component.translatable("dlc_manager.update.url_downloading").getString();
        downloadButton.active = false;
        pasteButton.active = false;
        backButton.active = false;

        new Thread(() -> {
            Path downloaded = null;
            try {
                downloaded = downloadToUpdateDir(url);
                if (downloaded == null) {
                    throw new IOException("download failed");
                }
                DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(downloaded);
                if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null
                    || !DlcUpdateService.isImportPackageValid(downloaded)) {
                    Files.deleteIfExists(downloaded);
                    Minecraft.getInstance().execute(() -> {
                        errorText = Component.translatable("dlc_manager.update.import_invalid").getString();
                        downloading = false;
                        downloadButton.active = true;
                        pasteButton.active = true;
                        backButton.active = true;
                    });
                    return;
                }
                boolean imported = DlcServerConnection.importUpdatePackage(entry, 8000, downloaded);
                Minecraft.getInstance().execute(() -> {
                    downloading = false;
                    downloadButton.active = true;
                    pasteButton.active = true;
                    backButton.active = true;
                    if (!imported) {
                        errorText = Component.translatable("dlc_manager.update.failed").getString();
                        return;
                    }
                    if (parent instanceof DlcServerUpdateManagerScreen manager) {
                        manager.refreshData();
                    }
                    Minecraft.getInstance().setScreen(parent);
                });
            } catch (Exception ex) {
                Path toDelete = downloaded;
                if (toDelete != null) {
                    try {
                        Files.deleteIfExists(toDelete);
                    } catch (IOException ignored) {
                    }
                }
                String msg = ex.getMessage() == null ? "download failed" : ex.getMessage();
                Minecraft.getInstance().execute(() -> {
                    errorText = Component.translatable("dlc_manager.update.url_failed", msg).getString();
                    downloading = false;
                    downloadButton.active = true;
                    pasteButton.active = true;
                    backButton.active = true;
                });
            }
        }, "dlc-server-update-url-download").start();
    }

    private Path downloadToUpdateDir(String url) throws IOException {
        Path updateDir = DlcUpdateService.getUpdateDir();
        if (updateDir == null) {
            throw new IOException("missing dlc root");
        }
        Files.createDirectories(updateDir);
        String fileName = DlcDownloadService.suggestFileName(url);
        if (fileName == null || fileName.isBlank()) {
            fileName = DlcDownloadUtil.sanitizeFileName(DlcDownloadUtil.extractFileNameFromUrl(url));
        }
        if (fileName == null || fileName.isBlank()) {
            fileName = "update.zip";
        } else if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            fileName = fileName + ".zip";
        }
        totalBytes = 0L;
        downloadedBytes = 0L;
        return DlcDownloadService.download(url, updateDir, fileName, (done, total) -> {
            downloadedBytes = done;
            totalBytes = total;
            progress = total > 0 ? (done / (float) total) : 0f;
        }, null);
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height, float progress) {
        int filled = (int) (width * Math.max(0f, Math.min(1f, progress)));
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B);
        guiGraphics.fill(x, y, x + filled, y + height, 0xFF4CAF50);
    }
}
