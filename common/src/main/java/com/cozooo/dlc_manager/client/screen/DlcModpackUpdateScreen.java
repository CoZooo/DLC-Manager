package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.CheckResult;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.DownloadResult;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.ReleaseStep;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService.Side;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DlcModpackUpdateScreen extends DlcScreenCompat.MouseScroll {
    private static final int TEXT_COLOR = 0xC8C8C8;
    private static final int LABEL_COLOR = 0xDCDCDC;
    private static final int VERSION_COLOR = 0x7FD0FF;
    private static final int SECTION_COLOR = 0x93D9FF;
    private static final int WARNING_COLOR = 0xD7B56D;
    private static final int CONTENT_Y_OFFSET = -30;
    private static final int PANEL_BG = 0xAA121519;
    private static final int PANEL_BORDER = 0xFF2A2F36;
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
        super(Component.translatable("dlc_manager.pack_update.title"));
        this.parent = parent;
        this.checkResult = checkResult == null ? new CheckResult() : checkResult;
        this.serverEntry = serverEntry;
        this.side = serverEntry == null ? Side.CLIENT : Side.SERVER;
        this.allowSkipThisUpdate = allowSkipThisUpdate;
    }

    @Override
    protected void init() {
        relayoutChangelog(true);
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        updateButtons();
        relayoutChangelog(false);
        int centerX = this.width / 2;
        int y = this.height / 2 - 78 + CONTENT_Y_OFFSET;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, y, 0xFFFFFF);
        y += 20;

        y = drawCenteredVersionLine(guiGraphics, centerX, y,
            Component.translatable("dlc_manager.pack_update.base").getString(),
            checkResult.currentBase, checkResult.targetBase);
        y += 14;
        y = drawCenteredVersionLine(guiGraphics, centerX, y,
            Component.translatable("dlc_manager.pack_update.dlc").getString(),
            checkResult.currentDlc, checkResult.targetDlc);
        y += 16;
        y = renderChangelog(guiGraphics, y);

        if (downloading) {
            guiGraphics.drawCenteredString(this.font, statusText == null ? "" : statusText, centerX, y, 0xE6E6E6);
            y += 14;
            drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, progress);
            int percent = Math.round(Math.max(0f, Math.min(1f, progress)) * 100f);
            guiGraphics.drawCenteredString(this.font, percent + "%", centerX, y + 12, 0xC8C8C8);
            y += 26;
        } else if (errorText != null && !errorText.isBlank()) {
            guiGraphics.drawCenteredString(this.font, errorText, centerX, y, 0xFF7777);
        } else if (checkResult.hasLatestGap()) {
            Component warning = checkResult.canDownload()
                ? Component.translatable(
                    "dlc_manager.pack_update.latest_unreachable",
                    normalizeVersion(checkResult.downloadBase),
                    normalizeVersion(checkResult.downloadDlc)
                )
                : Component.translatable("dlc_manager.pack_update.latest_unreachable_no_download");
            guiGraphics.drawCenteredString(this.font, warning, centerX, y, WARNING_COLOR);
            y += 12;
            if (checkResult.canDownload()) {
                guiGraphics.drawCenteredString(this.font,
                    Component.translatable("dlc_manager.pack_update.partial_download_hint"),
                    centerX, y, WARNING_COLOR);
            } else {
                guiGraphics.drawCenteredString(this.font,
                    Component.translatable("dlc_manager.pack_update.asset_missing"),
                    centerX, y, WARNING_COLOR);
            }
        } else if (!checkResult.canDownload()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.pack_update.asset_missing"), centerX, y, WARNING_COLOR);
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    private int renderChangelog(GuiGraphics guiGraphics, int y) {
        if (changelogLines.isEmpty()) {
            return y;
        }
        guiGraphics.drawString(this.font, Component.translatable("dlc_manager.update.changelog_tooltip_title"),
            changelogLeft, y, 0xE6E6E6, false);
        y += 12;
        drawChangelogPanel(guiGraphics);
        return changelogBottom + 8;
    }

    private void drawChangelogPanel(GuiGraphics guiGraphics) {
        guiGraphics.fill(changelogLeft - 6, changelogTop - 6, changelogRight + 6, changelogBottom + 6, PANEL_BG);
        guiGraphics.fill(changelogLeft - 6, changelogTop - 6, changelogRight + 6, changelogTop - 5, PANEL_BORDER);
        guiGraphics.fill(changelogLeft - 6, changelogBottom + 5, changelogRight + 6, changelogBottom + 6, PANEL_BORDER);
        guiGraphics.fill(changelogLeft - 6, changelogTop - 6, changelogLeft - 5, changelogBottom + 6, PANEL_BORDER);
        guiGraphics.fill(changelogRight + 5, changelogTop - 6, changelogRight + 6, changelogBottom + 6, PANEL_BORDER);

        int textY = changelogTop - scrollOffset;
        guiGraphics.enableScissor(changelogLeft, changelogTop, changelogRight, changelogBottom);
        try {
            for (ChangelogLine line : changelogLines) {
                if (line.hr) {
                    int hrY = textY + line.height / 2;
                    if (hrY >= changelogTop && hrY <= changelogBottom - 1) {
                        guiGraphics.fill(changelogLeft, hrY, changelogRight, hrY + 1, 0xFF5A5A5A);
                    }
                    textY += line.height;
                    continue;
                }
                if (line.empty) {
                    textY += line.height;
                    continue;
                }
                if (textY <= changelogBottom - line.height && textY + line.height >= changelogTop) {
                    if (line.scale > 1.0f) {
                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().scale(line.scale, line.scale, 1.0f);
                        guiGraphics.drawString(this.font, line.sequence,
                            (int) (changelogLeft / line.scale),
                            (int) (textY / line.scale),
                            line.color, false);
                        guiGraphics.pose().popPose();
                    } else {
                        guiGraphics.drawString(this.font, line.sequence, changelogLeft, textY, line.color, false);
                    }
                }
                textY += line.height;
            }
        } finally {
            guiGraphics.disableScissor();
        }
        drawScrollBar(guiGraphics);
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

    private int drawCenteredVersionLine(GuiGraphics guiGraphics, int centerX, int y, String prefix, String current, String target) {
        String prefixText = prefix + " ";
        String versionText = formatVersionLineValue(current, target);
        int totalWidth = this.font.width(prefixText) + this.font.width(versionText);
        int x = centerX - totalWidth / 2;
        guiGraphics.drawString(this.font, prefixText, x, y, LABEL_COLOR, false);
        guiGraphics.drawString(this.font, versionText, x + this.font.width(prefixText), y, VERSION_COLOR, false);
        return y;
    }

    private String formatVersionLineValue(String current, String target) {
        String from = normalizeVersion(current);
        String to = normalizeVersion(target);
        if (!from.equals(to)) {
            return from + " -> " + to;
        }
        return from;
    }

    private void relayoutChangelog(boolean force) {
        String language = CrossPlatformRuntime.languageCode();
        int nextLeft = 40;
        int nextRight = Math.max(nextLeft + 120, this.width - 40);
        int nextTop = this.height / 2 - 4 + CONTENT_Y_OFFSET;
        int nextBottom = Math.max(nextTop + 60, this.height - 60 + CONTENT_Y_OFFSET);
        int nextWidth = Math.max(120, nextRight - nextLeft - 20);
        boolean changed = force
            || !language.equals(changelogLang)
            || nextLeft != changelogLeft
            || nextRight != changelogRight
            || nextTop != changelogTop
            || nextBottom != changelogBottom
            || nextWidth != changelogWidth;
        changelogLeft = nextLeft;
        changelogRight = nextRight;
        changelogTop = nextTop;
        changelogBottom = nextBottom;
        changelogWidth = nextWidth;
        scrollbarX = changelogRight - 4;
        scrollbarTop = changelogTop;
        scrollbarBottom = changelogBottom;
        visibleHeight = Math.max(10, changelogBottom - changelogTop);
        if (changed) {
            changelogLang = language;
            rebuildChangelogLines(language);
        }
    }

    private void rebuildChangelogLines(String language) {
        changelogLines.clear();
        int lineStep = this.font.lineHeight + 2;
        List<ReleaseStep> downloadableSteps = checkResult.steps == null ? List.of() : checkResult.steps;
        appendChangelogSteps(downloadableSteps, language, lineStep);
        List<ReleaseStep> unreachableSteps = resolveUnreachableSteps(downloadableSteps);
        if (!unreachableSteps.isEmpty()) {
            if (!changelogLines.isEmpty()) {
                changelogLines.add(ChangelogLine.empty(lineStep));
            }
            for (FormattedCharSequence line : this.font.split(
                Component.translatable("dlc_manager.pack_update.unreachable_changelog"),
                changelogWidth
            )) {
                changelogLines.add(ChangelogLine.text(line, WARNING_COLOR, 1.0f, lineStep));
            }
            changelogLines.add(ChangelogLine.empty(lineStep));
            appendChangelogSteps(unreachableSteps, language, lineStep);
        }
        totalContentHeight = 0;
        for (ChangelogLine line : changelogLines) {
            totalContentHeight += line.height;
        }
        maxScroll = Math.max(0, totalContentHeight - visibleHeight);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        if (maxScroll <= 0) {
            scrollOffset = 0;
        }
    }

    private void appendChangelogSteps(List<ReleaseStep> steps, String language, int lineStep) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
            ReleaseStep step = steps.get(stepIndex);
            if (step == null) {
                continue;
            }
            String transition = formatTransition(step);
            for (FormattedCharSequence line : this.font.split(Component.literal(transition), changelogWidth)) {
                changelogLines.add(ChangelogLine.text(line, SECTION_COLOR, 1.0f, lineStep));
            }
            String body = step.resolveChangelog(language);
            if (body != null && !body.isBlank()) {
                for (FormattedCharSequence line : DlcMarkdownRenderer.wrapMarkdown(this.font, body.trim(), changelogWidth)) {
                    if (DlcMarkdownRenderer.isHrLine(line)) {
                        changelogLines.add(ChangelogLine.hr(lineStep));
                        continue;
                    }
                    float scale = DlcMarkdownRenderer.getHeadingScale(this.font, line, changelogWidth);
                    int height = DlcMarkdownRenderer.getLineHeight(this.font, line, lineStep, changelogWidth);
                    changelogLines.add(ChangelogLine.text(line, TEXT_COLOR, scale, height));
                }
            }
            if (stepIndex < steps.size() - 1) {
                changelogLines.add(ChangelogLine.empty(lineStep));
            }
        }
    }

    private List<ReleaseStep> resolveUnreachableSteps(List<ReleaseStep> downloadableSteps) {
        List<ReleaseStep> fullSteps = checkResult.fullSteps == null ? List.of() : checkResult.fullSteps;
        if (fullSteps.isEmpty() || !checkResult.hasLatestGap()) {
            return List.of();
        }
        int index = 0;
        while (index < fullSteps.size()
            && downloadableSteps != null
            && index < downloadableSteps.size()
            && sameTransition(fullSteps.get(index), downloadableSteps.get(index))) {
            index++;
        }
        if (index >= fullSteps.size()) {
            return List.of();
        }
        return fullSteps.subList(index, fullSteps.size());
    }

    private boolean sameTransition(ReleaseStep left, ReleaseStep right) {
        if (left == null || right == null) {
            return false;
        }
        return normalizeVersion(left.oldBase).equals(normalizeVersion(right.oldBase))
            && normalizeVersion(left.oldDlc).equals(normalizeVersion(right.oldDlc))
            && normalizeVersion(left.newBase).equals(normalizeVersion(right.newBase))
            && normalizeVersion(left.newDlc).equals(normalizeVersion(right.newDlc));
    }

    private String formatTransition(ReleaseStep step) {
        return normalizeVersion(step.oldBase) + "+" + normalizeVersion(step.oldDlc)
            + " -> "
            + normalizeVersion(step.newBase) + "+" + normalizeVersion(step.newDlc);
    }

    @Override
    protected boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        return handleMouseScrolled(deltaY);
    }

    private boolean handleMouseScrolled(double delta) {
        if (maxScroll <= 0) {
            return false;
        }
        int step = this.font.lineHeight + 2;
        scrollOffset -= (int) Math.round(delta * step);
        if (scrollOffset < 0) {
            scrollOffset = 0;
        } else if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverChangelogContent(mouseX, mouseY)) {
            int textY = changelogTop - scrollOffset;
            for (ChangelogLine line : changelogLines) {
                if (line.hr || line.empty) {
                    textY += line.height;
                    continue;
                }
                if (mouseY >= textY && mouseY < textY + line.height) {
                    int relX = (int) mouseX - changelogLeft;
                    int maxWidth = Math.round(this.font.width(line.sequence) * line.scale);
                    if (relX >= 0 && relX <= maxWidth) {
                        int styleX = line.scale > 1.0f ? (int) (relX / line.scale) : relX;
                        Style style = findStyleAt(line.sequence, styleX);
                        if (style != null && handleComponentClicked(style)) {
                            return true;
                        }
                    }
                    break;
                }
                textY += line.height;
            }
        }
        if (button == 0 && isOverScrollbar(mouseX, mouseY)) {
            updateScrollFromDrag(mouseY);
            draggingScrollbar = true;
            dragStartY = (int) mouseY;
            dragStartScroll = scrollOffset;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar && button == 0) {
            int trackHeight = Math.max(1, (scrollbarBottom - scrollbarTop) - scrollbarHeight);
            int delta = (int) mouseY - dragStartY;
            int next = dragStartScroll + (int) Math.round(delta * (maxScroll / (double) trackHeight));
            scrollOffset = Math.max(0, Math.min(maxScroll, next));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isOverChangelogContent(double mouseX, double mouseY) {
        return mouseX >= changelogLeft && mouseX <= changelogRight
            && mouseY >= changelogTop && mouseY <= changelogBottom;
    }

    private Style findStyleAt(FormattedCharSequence sequence, int x) {
        if (sequence == null || x < 0) {
            return null;
        }
        return this.font.getSplitter().componentStyleAtWidth(sequence, x);
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        return maxScroll > 0
            && mouseX >= scrollbarX - 2 && mouseX <= scrollbarX + 6
            && mouseY >= scrollbarTop && mouseY <= scrollbarBottom;
    }

    private void updateScrollFromDrag(double mouseY) {
        if (maxScroll <= 0) {
            scrollOffset = 0;
            return;
        }
        int trackHeight = Math.max(1, scrollbarBottom - scrollbarTop - scrollbarHeight);
        int knobTop = (int) mouseY - scrollbarHeight / 2;
        int rel = Math.max(0, Math.min(trackHeight, knobTop - scrollbarTop));
        scrollOffset = (int) Math.round(rel * (maxScroll / (double) trackHeight));
    }

    private void drawScrollBar(GuiGraphics guiGraphics) {
        if (maxScroll <= 0) {
            return;
        }
        int trackHeight = Math.max(1, scrollbarBottom - scrollbarTop);
        scrollbarHeight = Math.max(18, (int) (trackHeight * (visibleHeight / (double) Math.max(visibleHeight, totalContentHeight))));
        if (scrollbarHeight > trackHeight) {
            scrollbarHeight = trackHeight;
        }
        int travel = Math.max(1, trackHeight - scrollbarHeight);
        int knobTop = scrollbarTop + (int) Math.round((scrollOffset / (double) maxScroll) * travel);
        guiGraphics.fill(scrollbarX, scrollbarTop, scrollbarX + 4, scrollbarBottom, 0x663A3F48);
        guiGraphics.fill(scrollbarX, knobTop, scrollbarX + 4, knobTop + scrollbarHeight, 0xFF8A95A8);
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
            errorText = DlcPackReleaseUpdateService.toDownloadFailureComponent("unknown").getString();
            return;
        }
        if (result.canceled) {
            statusText = "";
            errorText = Component.translatable("dlc_manager.pack_update.canceled").getString();
            return;
        }
        if (!result.success) {
            String reason = result.message == null || result.message.isBlank() ? "failed" : result.message;
            errorText = DlcPackReleaseUpdateService.toDownloadFailureComponent(reason).getString();
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
}
