package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService.DownloadResult;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService.SelfUpdateResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DlcSelfUpdateScreen extends Screen {
    private final Screen parent;
    private SelfUpdateResult result;
    private Button downloadButton;
    private Button backButton;
    private boolean checking;
    private boolean downloading;
    private String statusText;
    private String errorText;

    public DlcSelfUpdateScreen(Screen parent, SelfUpdateResult preset) {
        super(Component.translatable("dlc_manager.self_update.title"));
        this.parent = parent;
        this.result = preset;
    }

    @Override
    protected void init() {
        int buttonWidth = 140;
        int buttonHeight = 20;
        int y = this.height - 40;
        downloadButton = Button.builder(Component.translatable("dlc_manager.self_update.download"), btn -> startDownload())
            .bounds(this.width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build();
        backButton = Button.builder(Component.translatable("dlc_manager.self_update.skip"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(this.width / 2 + 6, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(downloadButton);
        addRenderableWidget(backButton);
        updateButtons();
        if (result == null) {
            startCheck();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int centerX = this.width / 2;
        int y = 30;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, y, 0xFFFFFF);
        y += 18;
        if (result != null && result.currentVersion != null && !result.currentVersion.isBlank()) {
            guiGraphics.drawCenteredString(this.font,
                Component.translatable("dlc_manager.self_update.current", result.currentVersion),
                centerX, y, 0xC8C8C8);
            y += 14;
        }
        if (result != null && result.latestVersion != null && !result.latestVersion.isBlank() && result.hasUpdate) {
            guiGraphics.drawCenteredString(this.font,
                Component.translatable("dlc_manager.self_update.latest", result.latestVersion),
                centerX, y, 0xC8C8C8);
            y += 14;
        }
        if (checking) {
            guiGraphics.drawCenteredString(this.font,
                Component.translatable("dlc_manager.self_update.checking"),
                centerX, y, 0xE6E6E6);
        } else if (downloading) {
            String status = statusText == null ? "" : statusText;
            guiGraphics.drawCenteredString(this.font, status, centerX, y, 0xE6E6E6);
        } else if (errorText != null && !errorText.isBlank()) {
            guiGraphics.drawCenteredString(this.font, errorText, centerX, y, 0xFF6B6B);
        } else if (result != null && !result.hasUpdate) {
            guiGraphics.drawCenteredString(this.font,
                Component.translatable("dlc_manager.self_update.none"),
                centerX, y, 0xC8C8C8);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void startCheck() {
        if (checking) {
            return;
        }
        checking = true;
        errorText = null;
        statusText = null;
        updateButtons();
        new Thread(() -> {
            SelfUpdateResult check = DlcSelfUpdateService.checkForUpdate();
            Minecraft.getInstance().execute(() -> {
                checking = false;
                result = check;
                updateButtons();
            });
        }, "dlc-self-update-check").start();
    }

    private void startDownload() {
        if (downloading || checking || result == null || !result.hasUpdate) {
            return;
        }
        downloading = true;
        errorText = null;
        statusText = Component.translatable("dlc_manager.self_update.downloading").getString();
        updateButtons();
        new Thread(() -> {
            DownloadResult download = DlcSelfUpdateService.downloadUpdate(result);
            Minecraft.getInstance().execute(() -> {
                downloading = false;
                if (download.success) {
                    showRestartPrompt();
                } else {
                    errorText = Component.translatable("dlc_manager.self_update.failed").getString();
                }
                updateButtons();
            });
        }, "dlc-self-update-download").start();
    }

    private void showRestartPrompt() {
        Minecraft.getInstance().setScreen(new ConfirmScreen(choice -> {
            if (choice) {
                Minecraft.getInstance().stop();
            } else {
                Minecraft.getInstance().setScreen(parent);
            }
        }, Component.translatable("dlc_manager.self_update.title"),
            Component.translatable("dlc_manager.self_update.restart"),
            Component.translatable("dlc_manager.restart_now"),
            Component.translatable("dlc_manager.restart_later")));
    }

    private void updateButtons() {
        if (downloadButton != null) {
            boolean enabled = !checking && !downloading && result != null && result.hasUpdate;
            downloadButton.active = enabled;
            downloadButton.visible = true;
        }
        if (backButton != null) {
            backButton.active = !downloading && !checking;
        }
    }
}
