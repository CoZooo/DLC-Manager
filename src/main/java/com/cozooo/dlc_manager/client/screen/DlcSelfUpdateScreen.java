package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService.DownloadResult;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService.SelfUpdateResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DlcSelfUpdateScreen extends DlcPageScreen {
    private final Screen parent;
    private final boolean showSkipThisUpdateButton;
    private SelfUpdateResult result;
    private Button downloadButton;
    private Button skipThisUpdateButton;
    private Button backButton;
    private boolean checking;
    private boolean downloading;
    private String statusText;
    private String errorText;

    public DlcSelfUpdateScreen(Screen parent, SelfUpdateResult preset) {
        this(parent, preset, false);
    }

    public DlcSelfUpdateScreen(Screen parent, SelfUpdateResult preset, boolean showSkipThisUpdateButton) {
        super(DlcUiPage.SELF_UPDATE, parent, DlcTextCompat.translatable("dlc_manager.self_update.title"));
        this.parent = parent;
        this.result = preset;
        this.showSkipThisUpdateButton = showSkipThisUpdateButton;
    }

    private void initControls() {
        int buttonWidth = showSkipThisUpdateButton ? 110 : 140;
        int buttonHeight = 20;
        int gap = 6;
        int y = this.height - 40;
        int buttonCount = showSkipThisUpdateButton ? 3 : 2;
        int totalWidth = buttonWidth * buttonCount + gap * (buttonCount - 1);
        int startX = this.width / 2 - totalWidth / 2;
        downloadButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.self_update.download"), btn -> startDownload())
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build();
        if (showSkipThisUpdateButton) {
            skipThisUpdateButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.self_update.skip_this_update"), btn -> skipThisUpdate())
                .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
                .build();
        } else {
            skipThisUpdateButton = null;
        }
        backButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.self_update.skip"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(startX + (buttonWidth + gap) * (buttonCount - 1), y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(downloadButton);
        if (skipThisUpdateButton != null) {
            addRenderableWidget(skipThisUpdateButton);
        }
        addRenderableWidget(backButton);
        updateButtons();
        if (result == null) {
            startCheck();
        }
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
        statusText = DlcTextCompat.translatable("dlc_manager.self_update.downloading").getString();
        updateButtons();
        new Thread(() -> {
            DownloadResult download = DlcSelfUpdateService.downloadUpdate(result);
            Minecraft.getInstance().execute(() -> {
                downloading = false;
                if (download.success) {
                    showRestartPrompt();
                } else {
                    errorText = DlcTextCompat.translatable("dlc_manager.self_update.failed").getString();
                }
                updateButtons();
            });
        }, "dlc-self-update-download").start();
    }

    private void showRestartPrompt() {
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(choice -> {
            if (choice) {
                Minecraft.getInstance().stop();
            } else {
                Minecraft.getInstance().setScreen(parent);
            }
        }, DlcTextCompat.translatable("dlc_manager.self_update.title"),
            DlcTextCompat.translatable("dlc_manager.self_update.restart"),
            DlcTextCompat.translatable("dlc_manager.restart_now"),
            DlcTextCompat.translatable("dlc_manager.restart_later")));
    }

    private void skipThisUpdate() {
        if (result != null && result.hasUpdate) {
            DlcSelfUpdateService.skipPromptForUpdate(result);
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private void updateButtons() {
        if (downloadButton != null) {
            boolean enabled = !checking && !downloading && result != null && result.hasUpdate;
            downloadButton.active = enabled;
            downloadButton.visible = true;
        }
        if (skipThisUpdateButton != null) {
            skipThisUpdateButton.active = !downloading && !checking && result != null && result.hasUpdate;
            skipThisUpdateButton.visible = true;
        }
        if (backButton != null) {
            backButton.active = !downloading && !checking;
        }
    }

    @Override protected void buildPage() {
        initControls();
        var body = node(content, "div", "page-dialog");
        live(body, () -> result == null || result.currentVersion == null ? "" : tr("dlc_manager.self_update.current", result.currentVersion), "page-description");
        live(body, () -> result == null || result.latestVersion == null || !result.hasUpdate ? "" : tr("dlc_manager.self_update.latest", result.latestVersion), "section-title");
        live(body, () -> checking ? tr("dlc_manager.self_update.checking") : downloading ? statusText : errorText != null ? errorText : result != null && !result.hasUpdate ? tr("dlc_manager.self_update.none") : "", "page-message");
        mount(footer, backButton);
        if (skipThisUpdateButton != null) mount(footer, skipThisUpdateButton);
        mount(footer, downloadButton).getElement().setAttribute("class", "button primary");
    }
    @Override protected void updatePage() { updateButtons(); }

}
