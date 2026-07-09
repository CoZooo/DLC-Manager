package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator;
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.core.DlcPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class DlcUpdateUrlScreen extends Screen {
    private final Screen parent;
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
    private boolean applyingUpdates;

    public DlcUpdateUrlScreen(Screen parent) {
        super(Component.translatable("dlc_manager.update.import_url_title"));
        this.parent = parent;
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
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
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
                DlcUpdateService.ImportValidationResult validation = DlcUpdateService.validateImportPackage(downloaded);
                if (!validation.isValid()) {
                    Files.deleteIfExists(downloaded);
                    Minecraft.getInstance().execute(() -> {
                        Component message = DlcMessageAdapter.toImportValidationComponent(validation);
                        showToast(Component.translatable("dlc_manager.update.manager_title"),
                            message);
                        errorText = message.getString();
                        downloading = false;
                        downloadButton.active = true;
                        pasteButton.active = true;
                        backButton.active = true;
                    });
                    return;
                }
                boolean imported = DlcUpdateService.importUpdatePackage(downloaded);
                if (!imported) {
                    showToast(Component.translatable("dlc_manager.update.manager_title"),
                        Component.translatable("dlc_manager.update.dlc_root_missing"));
                    Minecraft.getInstance().execute(() -> {
                        errorText = Component.translatable("dlc_manager.update.dlc_root_missing").getString();
                        downloading = false;
                        downloadButton.active = true;
                        pasteButton.active = true;
                        backButton.active = true;
                    });
                    return;
                }
                Minecraft.getInstance().execute(() -> {
                    downloading = false;
                    promptImportedUpdate();
                });
                return;
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
        }, "dlc-update-url-download").start();
    }

    private void promptImportedUpdate() {
        UpdateCheckResult check = DlcUpdateService.checkForUpdates(true);
        if (check != null && check.hasUpdates()) {
            Minecraft.getInstance().setScreen(new DlcUpdatePromptScreen(parent, check));
            return;
        }
        showToast(Component.translatable("dlc_manager.update.manager_title"),
            Component.translatable("dlc_manager.update.no_updates"));
        Minecraft.getInstance().setScreen(parent);
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
            statusText = Component.translatable("dlc_manager.update.url_downloading").getString();
            downloadedBytes = done;
            totalBytes = total;
            progress = total > 0 ? (done / (float) total) : 0f;
        }, null);
    }

    private void confirmReupdate(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        String changelog = DlcUpdateService.buildChangelogText(
            DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true),
            DlcPlatformServices.languageCode());
        confirmReupdateNow(baseOld, dlcOld, baseNew, dlcNew, changelog);
    }

    private void confirmReupdateNow(String baseOld, String dlcOld, String baseNew, String dlcNew, String changelog) {
        Component title = DlcConfirmUtil.buildUpdateManagerTitle();
        Component message = DlcConfirmUtil.buildUpdateConfirmReapplyMessage(baseOld, dlcOld, baseNew, dlcNew, changelog);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                var chain = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true);
                DlcUpdatePackGuard.confirmBeforeUpdate(parent, chain,
                    (packCheck, uninstallIncompatible) -> startReupdate(baseOld, dlcOld, baseNew, dlcNew, chain, packCheck, uninstallIncompatible));
            } else {
                Minecraft.getInstance().setScreen(parent);
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void startReupdate(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        var chain = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true);
        startReupdate(baseOld, dlcOld, baseNew, dlcNew, chain, null, false);
    }

    private void startReupdate(String baseOld, String dlcOld, String baseNew, String dlcNew, java.util.List<DlcUpdatePackage> chain,
                               DlcPackUpdateGuard.CheckResult packCheck, boolean uninstallIncompatible) {
        if (chain == null || chain.isEmpty()) {
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(parent, false, Component.translatable("dlc_manager.update.failed")));
            return;
        }
        setApplyingUpdates(true);
        new Thread(() -> {
            DlcPackUpdateCoordinator.UpdateSession packSession = null;
            try {
                DlcPackUpdateCoordinator coordinator = new DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot());
                packSession = coordinator.prepareForUpdate(packCheck, uninstallIncompatible);
                UpdateApplyResult result = DlcUpdateService.applyUpdates(chain, false);
                if (result != null && result.isSuccess()) {
                    packSession.finishAfterSuccessfulUpdate();
                } else {
                    packSession.restoreAfterFailedUpdate();
                }
                UpdateApplyResult finalResult = result;
                Minecraft.getInstance().execute(() -> handleReupdateResult(finalResult, null));
            } catch (Exception ex) {
                DlcPackUpdateCoordinator.UpdateSession session = packSession;
                if (session != null) {
                    try {
                        session.restoreAfterFailedUpdate();
                    } catch (Exception restoreError) {
                        ex.addSuppressed(restoreError);
                    }
                }
                String message = ex.getMessage() == null ? "Failed to apply update" : ex.getMessage();
                Minecraft.getInstance().execute(() -> {
                    setApplyingUpdates(false);
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(parent, false, Component.literal(message)));
                });
            }
        }, "dlc-update-url-apply").start();
    }

    private void handleReupdateResult(UpdateApplyResult result, UpdateCheckResult check) {
        setApplyingUpdates(false);
        if (result != null && result.isMissing()) {
            UpdateCheckResult missingCheck = check == null ? DlcUpdateService.checkForUpdates(true) : check;
            Minecraft.getInstance().setScreen(new DlcUpdateMissingScreen(parent, missingCheck, result.getMissingPaths()));
            return;
        }
        if (result == null || !result.isSuccess()) {
            Component component = DlcMessageAdapter.toFailureComponent(result);
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(parent, false, component));
            return;
        }
        if (tryShowPostUpdateDownloads(result)) {
            return;
        }
        Component message = result != null && result.isHelperScheduled()
            ? Component.translatable("dlc_manager.update.deferred_restart")
            : Component.translatable("dlc_manager.update.success");
        Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(parent, true, message));
    }

    private void setApplyingUpdates(boolean applying) {
        applyingUpdates = applying;
        if (downloadButton != null) {
            downloadButton.active = !applying;
        }
        if (pasteButton != null) {
            pasteButton.active = !applying;
        }
        if (backButton != null) {
            backButton.active = !applying;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !applyingUpdates;
    }

    @Override
    public void onClose() {
        if (!applyingUpdates) {
            super.onClose();
        }
    }

    private boolean tryShowPostUpdateDownloads(UpdateApplyResult result) {
        if (result == null || !result.isSuccess()) {
            return false;
        }
        if (result.hasRequiredAdd()) {
            java.util.List<DlcRequiredEntry> missing = DlcRequiredManager.collectMissingDownloadsForPrompt();
            if (!missing.isEmpty()) {
                Minecraft.getInstance().setScreen(new DlcRequiredDownloadScreen(parent, missing, result));
                return true;
            }
        }
        if (result.hasRemoteAdd()) {
            java.util.List<DlcRemoteEntry> missing = DlcRemoteManager.collectMissing();
            if (!missing.isEmpty()) {
                Minecraft.getInstance().setScreen(new DlcRemoteDownloadScreen(createSuccessResultScreen(result), missing));
                return true;
            }
            return false;
        }
        return false;
    }

    private Screen createSuccessResultScreen(UpdateApplyResult result) {
        Component message = result != null && result.isHelperScheduled()
            ? Component.translatable("dlc_manager.update.deferred_restart")
            : Component.translatable("dlc_manager.update.success");
        return new DlcUpdateResultScreen(parent, true, message);
    }

    private void showToast(Component title, Component desc) {
        Minecraft minecraft = Minecraft.getInstance();
        DlcScreenCompat.addOrUpdatePeriodicNotification(minecraft.getToasts(), title, desc);
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height, float progress) {
        int filled = (int) (width * Math.max(0f, Math.min(1f, progress)));
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B);
        guiGraphics.fill(x, y, x + filled, y + height, 0xFF4CAF50);
    }
}
