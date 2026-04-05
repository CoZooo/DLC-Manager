package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.helper.DlcFileOpsHelperClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DlcFileOpsHelperDownloadScreen extends Screen {
    private final Screen parent;
    private final Component returnTitle;
    private volatile float progress;
    private volatile long downloadedBytes;
    private volatile long totalBytes;
    private volatile boolean started;

    public DlcFileOpsHelperDownloadScreen(Screen parent, Component returnTitle) {
        super(Component.translatable("dlc_manager.helper.title"));
        this.parent = parent;
        this.returnTitle = returnTitle == null ? Component.translatable("dlc_manager.helper.title") : returnTitle;
    }

    @Override
    protected void init() {
        if (started) {
            return;
        }
        started = true;
        new Thread(() -> {
            try {
                DlcFileOpsHelperClient.downloadHelper((done, total) -> {
                    downloadedBytes = done;
                    totalBytes = total;
                    progress = total > 0 ? (done / (float) total) : 0f;
                });
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new DlcSingleButtonScreen(
                    parent,
                    returnTitle,
                    Component.translatable("dlc_manager.helper.downloaded_restart"),
                    Component.translatable("dlc_manager.back"))));
            } catch (Exception ex) {
                String message = ex.getMessage();
                if (message == null || message.isBlank()) {
                    message = "unknown error";
                }
                final String finalMessage = message;
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new DlcSingleButtonScreen(
                    parent,
                    returnTitle,
                    Component.translatable("dlc_manager.helper.download_failed", finalMessage),
                    Component.translatable("dlc_manager.back"))));
            }
        }, "dlc-helper-download").start();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF101010);
        int centerX = this.width / 2;
        int titleY = this.height / 2 - 36;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, titleY, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
            Component.translatable("dlc_manager.helper.downloading"),
            centerX, titleY + 18, 0xE6E6E6);
        int barX = centerX - 120;
        int barY = titleY + 42;
        drawProgressBar(guiGraphics, barX, barY, 240, 8, progress);
        String progressText;
        if (totalBytes > 0) {
            int percent = Math.round(Math.max(0f, Math.min(1f, progress)) * 100f);
            progressText = percent + "%";
        } else if (downloadedBytes > 0) {
            progressText = Long.toString(downloadedBytes);
        } else {
            progressText = "0%";
        }
        guiGraphics.drawCenteredString(this.font, progressText, centerX, barY + 14, 0xC8C8C8);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height, float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        int filled = (int) (width * clamped);
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B);
        guiGraphics.fill(x, y, x + filled, y + height, 0xFF4CAF50);
    }
}
