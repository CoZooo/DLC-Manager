package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.CheckResult;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.DownloadResult;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.ReleaseStep;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.Side;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DlcModpackUpdateScreen extends DlcPageScreen {
    private static final int TEXT_COLOR = 0xC8C8C8;







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
    private final List<ChangelogLine> changelogLines = new ArrayList<>();
    private int changelogLeft;
    private int changelogRight;
    private int changelogTop;
    private int changelogBottom;
    private int changelogWidth;
    private int scrollOffset;
    private int maxScroll;
    private int visibleHeight;
    private int totalContentHeight;
    private int scrollbarX;
    private int scrollbarTop;
    private int scrollbarBottom;
    private int scrollbarHeight;
    private boolean draggingScrollbar;
    private int dragStartY;
    private int dragStartScroll;
    private String changelogLang = "";

    public DlcModpackUpdateScreen(Screen parent, CheckResult checkResult) {
        this(parent, checkResult, null, false);
    }

    public DlcModpackUpdateScreen(Screen parent, CheckResult checkResult, boolean allowSkipThisUpdate) {
        this(parent, checkResult, null, allowSkipThisUpdate);
    }

    public DlcModpackUpdateScreen(Screen parent, CheckResult checkResult, DlcServerEntry serverEntry) {
        this(parent, checkResult, serverEntry, false);
    }

    public DlcModpackUpdateScreen(Screen parent, CheckResult checkResult, DlcServerEntry serverEntry, boolean allowSkipThisUpdate) {
        super(serverEntry == null ? DlcUiPage.LOCAL_MODPACK_UPDATE : DlcUiPage.SERVER_MODPACK_UPDATE, parent, Component.translatable("dlc_manager.pack_update.title"));
        this.parent = parent;
        this.checkResult = checkResult == null ? new CheckResult() : checkResult;
        this.serverEntry = serverEntry;
        this.side = serverEntry == null ? Side.CLIENT : Side.SERVER;
        this.allowSkipThisUpdate = allowSkipThisUpdate;
    }

    private void initControls() {

        boolean showSkipThis = allowSkipThisUpdate && side == Side.CLIENT;
        int buttonWidth = showSkipThis ? 100 : 120;
        int buttonHeight = 20;
        int spacing = 6;
        int buttonCount = showSkipThis ? 4 : 3;
        int totalWidth = buttonWidth * buttonCount + spacing * (buttonCount - 1);
        int startX = this.width / 2 - totalWidth / 2;
        int y = this.height - 40;
        openButton = Button.builder(Component.translatable("dlc_manager.pack_update.open_modpack"), btn -> openModpackUrl())
            .bounds(startX + buttonWidth + spacing, y, buttonWidth, buttonHeight)
            .build();
        downloadButton = Button.builder(Component.translatable("dlc_manager.pack_update.download"), btn -> onDownloadButton())
            .bounds(startX, y, buttonWidth, buttonHeight)
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

    private String normalizeVersion(String value) {
        if (value == null || value.isBlank()) {
            return "1.0.0";
        }
        return value;
    }

    private String formatVersionLineValue(String current, String target) {
        String from = normalizeVersion(current);
        String to = normalizeVersion(target);
        if (!from.equals(to)) {
            return from + " -> " + to;
        }
        return from;
    }

    private String formatTransition(ReleaseStep step) {
        return normalizeVersion(step.oldBase) + "+" + normalizeVersion(step.oldDlc)
            + " -> "
            + normalizeVersion(step.newBase) + "+" + normalizeVersion(step.newDlc);
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
            errorText = DlcMessageAdapter.toDownloadFailureComponent("unknown").getString();
            return;
        }
        if (result.canceled) {
            statusText = "";
            errorText = Component.translatable("dlc_manager.pack_update.canceled").getString();
            return;
        }
        if (!result.success) {
            String reason = result.message == null || result.message.isBlank() ? "failed" : result.message;
            errorText = DlcMessageAdapter.toDownloadFailureComponent(reason).getString();
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
        DlcScreenCompat.addOrUpdatePeriodicNotification(Minecraft.getInstance().getToasts(),
            Component.translatable("dlc_manager.update.manager_title"),
            Component.translatable("dlc_manager.pack_update.download_success"));
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
                if (checkResult.hasLatestGap()) {
                    downloadButton.setMessage(Component.translatable("dlc_manager.pack_update.download_reachable"));
                } else {
                    downloadButton.setMessage(Component.translatable("dlc_manager.pack_update.download"));
                }
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

    private static final class ChangelogLine {
        private final FormattedCharSequence sequence;
        private final int color;
        private final float scale;
        private final int height;
        private final boolean hr;
        private final boolean empty;

        private ChangelogLine(FormattedCharSequence sequence, int color, float scale, int height, boolean hr, boolean empty) {
            this.sequence = sequence;
            this.color = color;
            this.scale = scale;
            this.height = height;
            this.hr = hr;
            this.empty = empty;
        }

        private static ChangelogLine text(FormattedCharSequence sequence, int color, float scale, int height) {
            return new ChangelogLine(sequence, color, scale, height, false, false);
        }

        private static ChangelogLine hr(int height) {
            return new ChangelogLine(FormattedCharSequence.EMPTY, TEXT_COLOR, 1.0f, height, true, false);
        }

        private static ChangelogLine empty(int height) {
            return new ChangelogLine(FormattedCharSequence.EMPTY, TEXT_COLOR, 1.0f, height, false, true);
        }
    }

    private boolean controlsReady;
    private int pageRevision;
    @Override protected void buildPage() {
        if (!controlsReady) { controlsReady = true; initControls(); }
        pageRevision = 0;

        var list = section(null, null); row(list, tr("dlc_manager.pack_update.base"), formatVersionLineValue(checkResult.currentBase, checkResult.targetBase)); row(list, tr("dlc_manager.pack_update.dlc"), formatVersionLineValue(checkResult.currentDlc, checkResult.targetDlc));
        if (checkResult.hasLatestGap()) text(list, tr("dlc_manager.pack_update.latest_unreachable_no_download"), "page-warning");
        var steps = checkResult.fullSteps.isEmpty() ? checkResult.steps : checkResult.fullSteps;
        for (var step : steps) {
            var part = section(formatTransition(step), null);
            markdown(part, step.resolveChangelog(DlcPlatformServices.languageCode()));
        }
        live(content, () -> statusText, "page-description"); live(content, () -> errorText, "page-error"); progress(content, () -> progress);
        if (skipButton != null) mount(footer, skipButton); if (skipThisUpdateButton != null) mount(footer, skipThisUpdateButton); mount(footer, openButton); mount(footer, downloadButton).getElement().setAttribute("class", "button primary");

    }
    @Override protected void updatePage() {
        updateButtons();
        if (pageRevision != 0) refreshPage();
    }

}
