package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.CheckResult;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.DownloadResult;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.Side;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.atomic.AtomicBoolean;

public class DlcPackReleaseUpdateScreen extends Screen {
    private final Screen parent;
    private final CheckResult checkResult;
    private final DlcServerEntry serverEntry;
    private final Side side;
    private final boolean allowSkipThisUpdate;
    private Button openButton;
    private Button downloadButton;
    private Button skipThisUpdateButton;
    private Button skipButton;
    private volatile boolean downloading;
    private volatile float progress;
    private volatile String statusText = "";
    private volatile String errorText = "";
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    public DlcPackReleaseUpdateScreen(Screen parent, CheckResult checkResult) {
        this(parent, checkResult, null, false);
    }

    public DlcPackReleaseUpdateScreen(Screen parent, CheckResult checkResult, boolean allowSkipThisUpdate) {
        this(parent, checkResult, null, allowSkipThisUpdate);
    }

    public DlcPackReleaseUpdateScreen(Screen parent, CheckResult checkResult, DlcServerEntry serverEntry) {
        this(parent, checkResult, serverEntry, false);
    }

    public DlcPackReleaseUpdateScreen(Screen parent, CheckResult checkResult, DlcServerEntry serverEntry, boolean allowSkipThisUpdate) {
        super(Component.translatable("dlc_manager.pack_update.title"));
        this.parent = parent;
        this.checkResult = checkResult == null ? new CheckResult() : checkResult;
        this.serverEntry = serverEntry;
        this.side = serverEntry == null ? Side.CLIENT : Side.SERVER;
        this.allowSkipThisUpdate = allowSkipThisUpdate;
    }

    @Override
    protected void init() {
        boolean showSkipThis = allowSkipThisUpdate && side == Side.CLIENT;
        int buttonWidth = showSkipThis ? 100 : 120;
        int buttonHeight = 20;
        int spacing = 6;
        int buttonCount = showSkipThis ? 4 : 3;
        int totalWidth = buttonWidth * buttonCount + spacing * (buttonCount - 1);
        int startX = this.width / 2 - totalWidth / 2;
        int y = this.height - 40;
        openButton = Button.builder(Component.translatable("dlc_manager.pack_update.open_modpack"), btn -> openModpackUrl())
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build();
        downloadButton = Button.builder(Component.translatable("dlc_manager.pack_update.download"), btn -> onDownloadButton())
            .bounds(startX + buttonWidth + spacing, y, buttonWidth, buttonHeight)
            .build();
        int thirdX = startX + (buttonWidth + spacing) * 2;
        if (showSkipThis) {
            skipThisUpdateButton = Button.builder(Component.translatable("dlc_manager.self_update.skip_this_update"), btn -> skipThisUpdate())
                .bounds(thirdX, y, buttonWidth, buttonHeight)
                .build();
            skipButton = Button.builder(Component.translatable("dlc_manager.pack_update.skip"), btn -> Minecraft.getInstance().setScreen(parent))
                .bounds(startX + (buttonWidth + spacing) * 3, y, buttonWidth, buttonHeight)
                .build();
        } else {
            skipThisUpdateButton = null;
            skipButton = Button.builder(Component.translatable("dlc_manager.pack_update.skip"), btn -> Minecraft.getInstance().setScreen(parent))
                .bounds(thirdX, y, buttonWidth, buttonHeight)
                .build();
        }
        addRenderableWidget(openButton);
        addRenderableWidget(downloadButton);
        if (skipThisUpdateButton != null) {
            addRenderableWidget(skipThisUpdateButton);
        }
        addRenderableWidget(skipButton);
        updateButtons();
    }

    private void skipThisUpdate() {
        DlcPackReleaseUpdateService.skipPromptForUpdate(checkResult, side);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        updateButtons();
        int centerX = this.width / 2;
        int y = this.height / 2 - 78;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, y, 0xFFFFFF);
        y += 20;

        String baseLabel = formatVersionLine(Component.translatable("dlc_manager.pack_update.base").getString(),
            checkResult.currentBase, checkResult.targetBase);
        guiGraphics.drawCenteredString(this.font, baseLabel, centerX, y, 0xDCDCDC);
        y += 14;
        String dlcLabel = formatVersionLine(Component.translatable("dlc_manager.pack_update.dlc").getString(),
            checkResult.currentDlc, checkResult.targetDlc);
        guiGraphics.drawCenteredString(this.font, dlcLabel, centerX, y, 0xDCDCDC);
        y += 16;
        y = renderChangelog(guiGraphics, centerX, y);

        if (downloading) {
            guiGraphics.drawCenteredString(this.font, statusText == null ? "" : statusText, centerX, y, 0xE6E6E6);
            y += 14;
            drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, progress);
            int percent = Math.round(Math.max(0f, Math.min(1f, progress)) * 100f);
            guiGraphics.drawCenteredString(this.font, percent + "%", centerX, y + 12, 0xC8C8C8);
            y += 26;
        } else if (errorText != null && !errorText.isBlank()) {
            guiGraphics.drawCenteredString(this.font, errorText, centerX, y, 0xFF7777);
        } else if (!checkResult.canDownload()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.pack_update.asset_missing"), centerX, y, 0xD7B56D);
        }

    }

    private int renderChangelog(GuiGraphics guiGraphics, int centerX, int y) {
        String changelog = checkResult.resolveCombinedChangelog(CrossPlatformRuntime.languageCode());
        if (changelog == null || changelog.isBlank()) {
            return y;
        }
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.changelog_tooltip_title"), centerX, y, 0xE6E6E6);
        y += 12;
        int maxWidth = Math.max(120, this.width - 80);
        int maxBottom = this.height - 90;
        for (net.minecraft.util.FormattedCharSequence line : DlcMarkdownRenderer.wrapMarkdown(this.font, changelog, maxWidth)) {
            if (y > maxBottom) {
                break;
            }
            if (DlcMarkdownRenderer.isHrLine(line)) {
                guiGraphics.fill(centerX - maxWidth / 2, y + (this.font.lineHeight + 2) / 2,
                    centerX + maxWidth / 2, y + (this.font.lineHeight + 2) / 2 + 1, 0xFF5A5A5A);
                y += this.font.lineHeight + 2;
                continue;
            }
            float scale = DlcMarkdownRenderer.getHeadingScale(this.font, line, maxWidth);
            int drawHeight = DlcMarkdownRenderer.getLineHeight(this.font, line, this.font.lineHeight + 2, maxWidth);
            int x = centerX - Math.round(this.font.width(line) * scale / 2f);
            if (scale > 1.0f) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().scale(scale, scale, 1.0f);
                guiGraphics.drawString(this.font, line, (int) (x / scale), (int) (y / scale), 0xC8C8C8, false);
                guiGraphics.pose().popPose();
            } else {
                guiGraphics.drawString(this.font, line, x, y, 0xC8C8C8, false);
            }
            y += drawHeight;
        }
        return y + 2;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !downloading;
    }

    @Override
    public void onClose() {
        if (!downloading) {
            super.onClose();
        }
    }

    private void openModpackUrl() {
        String url = checkResult.modpackUrl == null ? "" : checkResult.modpackUrl.trim();
        if (url.isBlank()) {
            return;
        }
        Util.getPlatform().openUri(url);
    }

    private String formatVersionLine(String prefix, String current, String target) {
        String from = normalizeVersion(current);
        String to = normalizeVersion(target);
        if (!from.equals(to)) {
            return prefix + " " + from + " -> " + to;
        }
        return prefix + " " + from;
    }

    private String normalizeVersion(String value) {
        if (value == null || value.isBlank()) {
            return "1.0.0";
        }
        return value;
    }

    private void onDownloadButton() {
        if (downloading) {
            cancelRequested.set(true);
            return;
        }
        startDownload();
    }

    private void startDownload() {
        if (!checkResult.canDownload()) {
            return;
        }
        downloading = true;
        progress = 0f;
        errorText = "";
        cancelRequested.set(false);
        statusText = Component.translatable("dlc_manager.pack_update.downloading").getString();
        updateButtons();
        new Thread(() -> {
            DownloadResult result = side == Side.SERVER
                ? DlcPackReleaseUpdateService.downloadServerPackages(
                    checkResult,
                    serverEntry,
                    600000,
                    this::onProgress,
                    cancelRequested::get
                )
                : DlcPackReleaseUpdateService.downloadClientPackages(
                    checkResult,
                    this::onProgress,
                    cancelRequested::get
                );
            Minecraft.getInstance().execute(() -> finishDownload(result));
        }, "dlc-pack-release-download").start();
    }

    private void onProgress(int stepIndex, int stepCount, String fileName, long downloaded, long total, float overallProgress) {
        this.progress = overallProgress;
        String safeFile = fileName == null || fileName.isBlank() ? "package.zip" : fileName;
        this.statusText = Component.translatable("dlc_manager.pack_update.downloading_file", stepIndex + 1, stepCount, safeFile).getString();
    }

    private void finishDownload(DownloadResult result) {
        downloading = false;
        updateButtons();
        if (result == null) {
            errorText = Component.translatable("dlc_manager.pack_update.download_failed", "unknown").getString();
            return;
        }
        if (result.canceled) {
            statusText = "";
            errorText = Component.translatable("dlc_manager.pack_update.canceled").getString();
            return;
        }
        if (!result.success) {
            String reason = result.message == null || result.message.isBlank() ? "failed" : result.message;
            errorText = Component.translatable("dlc_manager.pack_update.download_failed", reason).getString();
            return;
        }
        if (side == Side.SERVER) {
            if (parent instanceof DlcServerUpdateManagerScreen manager) {
                manager.refreshData();
            }
            Minecraft.getInstance().setScreen(parent);
            return;
        }
        UpdateCheckResult check = DlcUpdateService.checkForUpdates(true);
        if (check != null && check.hasUpdates()) {
            DlcUpdateClient.showPrompt(parent, check);
            return;
        }
        SystemToast.addOrUpdate(
            Minecraft.getInstance().getToasts(),
            SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
            Component.translatable("dlc_manager.update.manager_title"),
            Component.translatable("dlc_manager.pack_update.download_success")
        );
        Minecraft.getInstance().setScreen(parent);
    }

    private void updateButtons() {
        if (openButton != null) {
            boolean hasUrl = checkResult.modpackUrl != null && !checkResult.modpackUrl.isBlank();
            openButton.active = hasUrl && !downloading;
        }
        if (downloadButton != null) {
            if (downloading) {
                downloadButton.active = true;
                downloadButton.setMessage(Component.translatable("dlc_manager.pack_update.cancel_download"));
            } else {
                downloadButton.setMessage(Component.translatable("dlc_manager.pack_update.download"));
                downloadButton.active = checkResult.canDownload();
            }
        }
        if (skipButton != null) {
            skipButton.active = !downloading;
        }
        if (skipThisUpdateButton != null) {
            skipThisUpdateButton.active = !downloading;
        }
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height, float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        int filled = Math.max(0, Math.min(width, Math.round(width * clamped)));
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B);
        if (filled > 0) {
            guiGraphics.fill(x, y, x + filled, y + height, 0xFF4CAF50);
        }
        guiGraphics.fill(x, y - 1, x + width, y, 0xFF4A4A4A);
        guiGraphics.fill(x, y + height, x + width, y + height + 1, 0xFF1A1A1A);
    }
}
