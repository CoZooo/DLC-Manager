package com.cozooo.dlc_manager.client;

import com.cozooo.dlc_manager.client.notice.DlcNoticeManager;
import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer;
import com.cozooo.dlc_manager.client.text.DlcTextFormatter;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen;
import com.cozooo.dlc_manager.client.screen.DlcRequiredDownloadScreen;
import com.cozooo.dlc_manager.client.screen.DlcSelfUpdateScreen;
import com.cozooo.dlc_manager.client.screen.DlcServerListScreen;
import com.cozooo.dlc_manager.client.screen.DlcServerMaLiLibScreen;
import com.cozooo.dlc_manager.client.screen.DlcServerManagerScreen;
import com.cozooo.dlc_manager.client.screen.DlcServerPasswordScreen;
import com.cozooo.dlc_manager.client.screen.DlcPackReleaseUpdateScreen;
import com.cozooo.dlc_manager.client.screen.DlcUpdatePromptScreen;
import com.cozooo.dlc_manager.client.screen.DlcUpdateChangelogScreen;
import com.cozooo.dlc_manager.client.server.DlcServerCache;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.client.DlcStartupStateSync;
import com.cozooo.dlc_manager.server.DlcApiService;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcNoticeConfig;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class DlcClientRuntime {
    private static volatile boolean startupCheckRunning = false;
    private static volatile boolean startupChecksCompleted = false;
    private static volatile boolean selfUpdatePrompted = false;
    private static String titleNoticeLang = "";
    private static String titleNoticeTitle = "";
    private static String titleNoticeSubhead = "";
    private static boolean titleNoticeSubheadEnabled = false;
    private static String titleNoticeText = "";
    private static boolean titleNoticeLoading = false;
    private static int titleNoticeScrollOffset = 0;
    private static boolean titleNoticeDraggingScrollbar = false;
    private static int titleNoticeDragStartY = 0;
    private static int titleNoticeDragStartScroll = 0;
    private static CompletableFuture<String> titleNoticeFuture;
    private static volatile boolean firstRunManagerHandled = false;
    private static final String FIRST_RUN_MARKER_NAME = "ShownOnFirstRun";
    private static final Logger LOGGER = LogUtils.getLogger();

    private DlcClientRuntime() {
    }

    public static AbstractWidget findButtonByKey(Screen screen, String key) {
        for (Object child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                if (widget.getMessage().getContents() instanceof TranslatableContents contents) {
                    if (key.equals(contents.getKey())) {
                        return widget;
                    }
                }
            }
        }
        return null;
    }

    public static boolean shouldShowMainMenuNoticeButton() {
        return DlcPathConfig.isNoticeDisplayModeMainMenuButton() && DlcNoticeManager.isNoticeEnabled();
    }

    public static boolean shouldShowMainMenuNoticePanel() {
        return DlcPathConfig.isNoticeDisplayModeMainMenuPanel()
            && DlcNoticeManager.isNoticeEnabled()
            && DlcPathConfig.getNoticePanelWidth() > 0
            && DlcPathConfig.getNoticePanelHeight() > 0;
    }

    public static void openNoticeFromTitle(Screen parent) {
        DlcNoticeManager.forceShow(parent);
    }

    public static void maybeOpenManagerOnFirstRun(Screen parent) {
        if (firstRunManagerHandled) {
            return;
        }
        firstRunManagerHandled = true;
        if (parent == null || !DlcPathConfig.isShowOnFirstRun()) {
            return;
        }
        Path dlcRoot = DlcPathConfig.getDlcRoot();
        if (dlcRoot == null) {
            return;
        }
        Path markerPath = dlcRoot.resolve(FIRST_RUN_MARKER_NAME);
        try {
            if (Files.exists(markerPath)) {
                return;
            }
            Files.createDirectories(dlcRoot);
            Files.createFile(markerPath);
        } catch (IOException ignored) {
            return;
        }
        Screen managerScreen = new DlcMaLiLibScreen(parent);
        if (DlcRemoteManager.tryShowFromManager(managerScreen)) {
            return;
        }
        Minecraft.getInstance().setScreen(managerScreen);
    }

    public static void renderTitleNoticePanel(Screen screen, GuiGraphics guiGraphics) {
        renderTitleNoticePanel(screen, guiGraphics, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public static void renderTitleNoticePanel(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!shouldShowMainMenuNoticePanel() || screen == null || guiGraphics == null) {
            titleNoticeDraggingScrollbar = false;
            return;
        }
        DlcNoticeConfig config = DlcNoticeConfig.load();
        if (!config.isNoticeEnabled()) {
            titleNoticeDraggingScrollbar = false;
            return;
        }
        TitleNoticePanelLayout layout = buildTitleNoticePanelLayout(screen, config);
        if (layout == null) {
            titleNoticeDraggingScrollbar = false;
            return;
        }
        clampTitleNoticeScroll(layout.maxScroll);
        if (titleNoticeDraggingScrollbar && mouseY != Integer.MIN_VALUE && layout.maxScroll > 0) {
            int trackHeight = Math.max(1, (layout.scrollbarBottom - layout.scrollbarTop) - layout.scrollbarHeight);
            int delta = mouseY - titleNoticeDragStartY;
            int next = titleNoticeDragStartScroll + (int) Math.round(delta * (layout.maxScroll / (double) trackHeight));
            titleNoticeScrollOffset = Math.max(0, Math.min(layout.maxScroll, next));
        }

        guiGraphics.fill(layout.x, layout.y, layout.right, layout.bottom, 0xCC101215);
        guiGraphics.fill(layout.x, layout.y, layout.right, layout.y + 1, 0xFF2A2F36);
        guiGraphics.fill(layout.x, layout.bottom - 1, layout.right, layout.bottom, 0xFF2A2F36);
        guiGraphics.fill(layout.x, layout.y, layout.x + 1, layout.bottom, 0xFF2A2F36);
        guiGraphics.fill(layout.right - 1, layout.y, layout.right, layout.bottom, 0xFF2A2F36);

        int titleY = layout.y + layout.padding;
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            DlcTextFormatter.parse(layout.title),
            layout.x + (layout.right - layout.x) / 2,
            titleY,
            0xFFFFFF
        );
        if (!layout.subheadLines.isEmpty()) {
            int subheadY = titleY + Minecraft.getInstance().font.lineHeight + 2;
            for (FormattedCharSequence subheadLine : layout.subheadLines) {
                int subheadWidth = Minecraft.getInstance().font.width(subheadLine);
                int subheadX = layout.x + ((layout.right - layout.x) - subheadWidth) / 2;
                if (subheadX < layout.textX) {
                    subheadX = layout.textX;
                }
                guiGraphics.drawString(Minecraft.getInstance().font, subheadLine, subheadX, subheadY, 0xBFC7D5, false);
                subheadY += layout.lineStep;
            }
        }

        int drawY = layout.textTop - titleNoticeScrollOffset;
        guiGraphics.enableScissor(layout.x + 1, layout.textTop, layout.right - 1, layout.textBottom);
        try {
            for (FormattedCharSequence line : layout.bodyLines) {
                if (DlcMarkdownRenderer.isHrLine(line)) {
                    int hrY = drawY + layout.lineStep / 2;
                    if (hrY >= layout.textTop && hrY <= layout.textBottom - 1) {
                        guiGraphics.fill(layout.textX, hrY, layout.textRight, hrY + 1, 0xFF5A5A5A);
                    }
                    drawY += layout.lineStep;
                    continue;
                }
                float scale = DlcMarkdownRenderer.getHeadingScale(Minecraft.getInstance().font, line, layout.textWidth);
                int lineHeight = DlcMarkdownRenderer.getLineHeight(Minecraft.getInstance().font, line, layout.lineStep, layout.textWidth);
                if (drawY > layout.textBottom - lineHeight) {
                    break;
                }
                if (drawY + lineHeight <= layout.textTop) {
                    drawY += lineHeight;
                    continue;
                }
                if (scale > 1.0f) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().scale(scale, scale, 1.0f);
                    guiGraphics.drawString(
                        Minecraft.getInstance().font,
                        line,
                        (int) (layout.textX / scale),
                        (int) (drawY / scale),
                        0xFFFFFF,
                        false
                    );
                    guiGraphics.pose().popPose();
                } else {
                    guiGraphics.drawString(Minecraft.getInstance().font, line, layout.textX, drawY, 0xE6E6E6, false);
                }
                drawY += lineHeight;
            }
        } finally {
            guiGraphics.disableScissor();
        }
        drawTitleNoticeScrollbar(guiGraphics, layout);
    }

    public static boolean handleTitleNoticePanelClick(Screen screen, double mouseX, double mouseY, int button) {
        if (button != 0 || !shouldShowMainMenuNoticePanel() || screen == null) {
            return false;
        }
        DlcNoticeConfig config = DlcNoticeConfig.load();
        if (!config.isNoticeEnabled()) {
            return false;
        }
        TitleNoticePanelLayout layout = buildTitleNoticePanelLayout(screen, config);
        if (layout == null) {
            return false;
        }
        clampTitleNoticeScroll(layout.maxScroll);
        if (!layout.contains(mouseX, mouseY)) {
            return false;
        }
        if (layout.isOverScrollbar(mouseX, mouseY)) {
            updateTitleNoticeScrollFromTrack(layout, mouseY);
            titleNoticeDraggingScrollbar = true;
            titleNoticeDragStartY = (int) mouseY;
            titleNoticeDragStartScroll = titleNoticeScrollOffset;
            return true;
        }
        if (layout.isOverContent(mouseX, mouseY)) {
            int drawY = layout.textTop - titleNoticeScrollOffset;
            for (FormattedCharSequence line : layout.bodyLines) {
                if (DlcMarkdownRenderer.isHrLine(line)) {
                    drawY += layout.lineStep;
                    continue;
                }
                float scale = DlcMarkdownRenderer.getHeadingScale(Minecraft.getInstance().font, line, layout.textWidth);
                int lineHeight = DlcMarkdownRenderer.getLineHeight(Minecraft.getInstance().font, line, layout.lineStep, layout.textWidth);
                if (drawY > layout.textBottom - lineHeight) {
                    break;
                }
                if (mouseY >= drawY && mouseY < drawY + lineHeight) {
                    int relX = (int) mouseX - layout.textX;
                    int maxWidth = Math.round(Minecraft.getInstance().font.width(line) * scale);
                    if (relX >= 0 && relX <= maxWidth) {
                        int styleX = scale > 1.0f ? (int) (relX / scale) : relX;
                        Style style = findStyleAt(line, styleX);
                        if (style != null) {
                            ClickEvent click = style.getClickEvent();
                            if (click != null && click.getAction() == ClickEvent.Action.OPEN_URL && click.getValue() != null && !click.getValue().isBlank()) {
                                Util.getPlatform().openUri(click.getValue());
                            }
                        }
                    }
                    return true;
                }
                drawY += lineHeight;
            }
        }
        return true;
    }

    public static boolean handleTitleNoticePanelScroll(Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!shouldShowMainMenuNoticePanel() || screen == null) {
            return false;
        }
        DlcNoticeConfig config = DlcNoticeConfig.load();
        if (!config.isNoticeEnabled()) {
            return false;
        }
        TitleNoticePanelLayout layout = buildTitleNoticePanelLayout(screen, config);
        if (layout == null || !layout.contains(mouseX, mouseY)) {
            return false;
        }
        clampTitleNoticeScroll(layout.maxScroll);
        if (layout.maxScroll > 0 && verticalAmount != 0.0D) {
            titleNoticeScrollOffset -= (int) Math.round(verticalAmount * layout.lineStep);
            clampTitleNoticeScroll(layout.maxScroll);
        }
        return true;
    }

    public static boolean handleTitleNoticePanelDrag(Screen screen, double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0 || !titleNoticeDraggingScrollbar || !shouldShowMainMenuNoticePanel() || screen == null) {
            return false;
        }
        DlcNoticeConfig config = DlcNoticeConfig.load();
        if (!config.isNoticeEnabled()) {
            titleNoticeDraggingScrollbar = false;
            return false;
        }
        TitleNoticePanelLayout layout = buildTitleNoticePanelLayout(screen, config);
        if (layout == null || layout.maxScroll <= 0) {
            titleNoticeDraggingScrollbar = false;
            return false;
        }
        int trackHeight = Math.max(1, (layout.scrollbarBottom - layout.scrollbarTop) - layout.scrollbarHeight);
        int delta = (int) mouseY - titleNoticeDragStartY;
        int next = titleNoticeDragStartScroll + (int) Math.round(delta * (layout.maxScroll / (double) trackHeight));
        titleNoticeScrollOffset = Math.max(0, Math.min(layout.maxScroll, next));
        return true;
    }

    public static boolean handleTitleNoticePanelRelease(Screen screen, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (titleNoticeDraggingScrollbar) {
            titleNoticeDraggingScrollbar = false;
            return true;
        }
        return false;
    }

    private static TitleNoticePanelLayout buildTitleNoticePanelLayout(Screen screen, DlcNoticeConfig config) {
        String lang = Minecraft.getInstance().options.languageCode;
        updateTitleNoticeText(config, lang);
        String title = titleNoticeTitle;
        if (title == null || title.isBlank()) {
            title = Component.translatable("dlc_manager.notice_title").getString();
        }
        String body = titleNoticeText;
        if (body == null || body.isBlank()) {
            body = titleNoticeLoading
                ? Component.translatable("dlc_manager.notice.loading").getString()
                : config.getContents().resolve(lang);
        }
        String subhead = titleNoticeSubhead;
        boolean subheadEnabled = titleNoticeSubheadEnabled;

        int panelWidth = Math.max(120, DlcPathConfig.getNoticePanelWidth());
        int panelHeight = Math.max(60, DlcPathConfig.getNoticePanelHeight());
        panelWidth = Math.min(panelWidth, Math.max(120, screen.width - 8));
        panelHeight = Math.min(panelHeight, Math.max(60, screen.height - 8));
        int[] pos = DlcPathConfig.computeNoticePanelPosition(screen.width, screen.height, panelWidth, panelHeight, 0, 0);
        int x = pos[0];
        int y = pos[1];
        int maxX = Math.max(0, screen.width - panelWidth);
        int maxY = Math.max(0, screen.height - panelHeight);
        if (x < 0) {
            x = 0;
        } else if (x > maxX) {
            x = maxX;
        }
        if (y < 0) {
            y = 0;
        } else if (y > maxY) {
            y = maxY;
        }

        int right = x + panelWidth;
        int bottom = y + panelHeight;
        int padding = 6;
        int lineStep = Minecraft.getInstance().font.lineHeight + 2;
        int titleY = y + padding;
        int contentTop = titleY + Minecraft.getInstance().font.lineHeight + 4;
        List<FormattedCharSequence> subheadLines = List.of();
        if (subheadEnabled && subhead != null && !subhead.isBlank()) {
            subheadLines = wrapMultiline(subhead, Math.max(40, panelWidth - padding * 2));
            if (!subheadLines.isEmpty()) {
                contentTop += subheadLines.size() * lineStep + 2;
            }
        }
        int contentBottom = bottom - padding;
        int visibleHeight = Math.max(10, contentBottom - contentTop);
        int textX = x + padding;
        int scrollbarGap = 4;
        int scrollbarWidth = 4;
        int textRightNoBar = right - padding;
        int textWidthNoBar = Math.max(20, textRightNoBar - textX);

        List<FormattedCharSequence> bodyLines = DlcMarkdownRenderer.wrapMarkdown(Minecraft.getInstance().font, body, textWidthNoBar);
        int totalContentHeight = computeTotalLineHeight(bodyLines, lineStep, textWidthNoBar);
        int maxScroll = Math.max(0, totalContentHeight - visibleHeight);
        int textRight = textRightNoBar;
        int textWidth = textWidthNoBar;
        int scrollbarX = textRight - scrollbarWidth;

        if (maxScroll > 0) {
            textRight = Math.max(textX + 20, right - padding - scrollbarWidth - scrollbarGap);
            textWidth = Math.max(20, textRight - textX);
            bodyLines = DlcMarkdownRenderer.wrapMarkdown(Minecraft.getInstance().font, body, textWidth);
            totalContentHeight = computeTotalLineHeight(bodyLines, lineStep, textWidth);
            maxScroll = Math.max(0, totalContentHeight - visibleHeight);
            scrollbarX = textRight + scrollbarGap;
        }

        int scrollbarHeight = Math.max(12, visibleHeight * visibleHeight / Math.max(1, totalContentHeight));
        if (scrollbarHeight > visibleHeight) {
            scrollbarHeight = visibleHeight;
        }

        return new TitleNoticePanelLayout(
            x, y, right, bottom, padding,
            textX, textRight, contentTop, contentBottom, textWidth,
            lineStep, visibleHeight, totalContentHeight, maxScroll,
            scrollbarX, contentTop, contentBottom, scrollbarWidth, scrollbarHeight,
            title, subheadLines, bodyLines
        );
    }

    private static void drawTitleNoticeScrollbar(GuiGraphics guiGraphics, TitleNoticePanelLayout layout) {
        if (layout.maxScroll <= 0 || layout.visibleHeight <= 0) {
            return;
        }
        int barY = layout.scrollbarYForScroll(titleNoticeScrollOffset);
        guiGraphics.fill(layout.scrollbarX, layout.scrollbarTop, layout.scrollbarX + layout.scrollbarWidth, layout.scrollbarBottom, 0xFF2B2B2B);
        guiGraphics.fill(layout.scrollbarX, barY, layout.scrollbarX + layout.scrollbarWidth, barY + layout.scrollbarHeight, 0xFF6B6B6B);
    }

    private static void updateTitleNoticeScrollFromTrack(TitleNoticePanelLayout layout, double mouseY) {
        if (layout.maxScroll <= 0 || layout.visibleHeight <= 0) {
            titleNoticeScrollOffset = 0;
            return;
        }
        int maxTrack = Math.max(1, layout.visibleHeight - layout.scrollbarHeight);
        int barCenter = (int) mouseY - layout.scrollbarTop - (layout.scrollbarHeight / 2);
        int next = (int) Math.round((barCenter / (double) maxTrack) * layout.maxScroll);
        titleNoticeScrollOffset = Math.max(0, Math.min(layout.maxScroll, next));
    }

    private static void clampTitleNoticeScroll(int maxScroll) {
        if (titleNoticeScrollOffset < 0) {
            titleNoticeScrollOffset = 0;
        } else if (titleNoticeScrollOffset > maxScroll) {
            titleNoticeScrollOffset = maxScroll;
        }
    }

    private static int computeTotalLineHeight(List<FormattedCharSequence> lines, int lineStep, int textWidth) {
        int total = 0;
        for (FormattedCharSequence line : lines) {
            if (DlcMarkdownRenderer.isHrLine(line)) {
                total += lineStep;
            } else {
                total += DlcMarkdownRenderer.getLineHeight(Minecraft.getInstance().font, line, lineStep, textWidth);
            }
        }
        return total;
    }

    private static List<FormattedCharSequence> wrapMultiline(String text, int maxWidth) {
        return DlcTextFormatter.splitMultiline(Minecraft.getInstance().font, text, maxWidth);
    }

    private static final class TitleNoticePanelLayout {
        private final int x;
        private final int y;
        private final int right;
        private final int bottom;
        private final int padding;
        private final int textX;
        private final int textRight;
        private final int textTop;
        private final int textBottom;
        private final int textWidth;
        private final int lineStep;
        private final int visibleHeight;
        private final int totalContentHeight;
        private final int maxScroll;
        private final int scrollbarX;
        private final int scrollbarTop;
        private final int scrollbarBottom;
        private final int scrollbarWidth;
        private final int scrollbarHeight;
        private final String title;
        private final List<FormattedCharSequence> subheadLines;
        private final List<FormattedCharSequence> bodyLines;

        private TitleNoticePanelLayout(
            int x, int y, int right, int bottom, int padding,
            int textX, int textRight, int textTop, int textBottom, int textWidth,
            int lineStep, int visibleHeight, int totalContentHeight, int maxScroll,
            int scrollbarX, int scrollbarTop, int scrollbarBottom, int scrollbarWidth, int scrollbarHeight,
            String title, List<FormattedCharSequence> subheadLines, List<FormattedCharSequence> bodyLines
        ) {
            this.x = x;
            this.y = y;
            this.right = right;
            this.bottom = bottom;
            this.padding = padding;
            this.textX = textX;
            this.textRight = textRight;
            this.textTop = textTop;
            this.textBottom = textBottom;
            this.textWidth = textWidth;
            this.lineStep = lineStep;
            this.visibleHeight = visibleHeight;
            this.totalContentHeight = totalContentHeight;
            this.maxScroll = maxScroll;
            this.scrollbarX = scrollbarX;
            this.scrollbarTop = scrollbarTop;
            this.scrollbarBottom = scrollbarBottom;
            this.scrollbarWidth = scrollbarWidth;
            this.scrollbarHeight = scrollbarHeight;
            this.title = title;
            this.subheadLines = subheadLines;
            this.bodyLines = bodyLines;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= right && mouseY >= y && mouseY <= bottom;
        }

        private boolean isOverContent(double mouseX, double mouseY) {
            return mouseX >= textX && mouseX <= textRight
                && mouseY >= textTop && mouseY <= textBottom;
        }

        private boolean isOverScrollbar(double mouseX, double mouseY) {
            if (maxScroll <= 0) {
                return false;
            }
            return mouseX >= scrollbarX - 2 && mouseX <= scrollbarX + scrollbarWidth + 2
                && mouseY >= scrollbarTop && mouseY <= scrollbarBottom;
        }

        private int scrollbarYForScroll(int scrollOffset) {
            if (maxScroll <= 0) {
                return scrollbarTop;
            }
            int maxTrack = Math.max(1, visibleHeight - scrollbarHeight);
            return scrollbarTop + (int) ((scrollOffset / (float) maxScroll) * maxTrack);
        }
    }

    private static Style findStyleAt(FormattedCharSequence sequence, int x) {
        final int[] width = {0};
        final Style[] result = {null};
        sequence.accept((index, style, codePoint) -> {
            if (result[0] != null) {
                return false;
            }
            String text = new String(Character.toChars(codePoint));
            int w = Minecraft.getInstance().font.width(text);
            if (width[0] + w > x) {
                result[0] = style;
                return false;
            }
            width[0] += w;
            return true;
        });
        return result[0];
    }

    public static boolean canOpenServerManager() {
        if (!DlcPathConfig.isServerSupportOn()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        if (minecraft.hasSingleplayerServer()) {
            return false;
        }
        return minecraft.player.hasPermissions(2);
    }

    public static void openManagerFromCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        Screen managerScreen = new DlcMaLiLibScreen(parent);
        if (DlcRemoteManager.tryShowFromManager(managerScreen)) {
            return;
        }
        minecraft.setScreen(managerScreen);
    }

    public static void openSelfUpdateFromCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new DlcSelfUpdateScreen(minecraft.screen, null));
    }

    public static void openUpdateManagerFromCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        DlcUpdateClient.openUpdateManager(minecraft.screen);
    }

    public static void openUpdatePromptFromCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        UpdateCheckResult result = DlcUpdateService.checkForUpdates();
        if (result == null || !result.hasUpdates()) {
            SystemToast.addOrUpdate(
                minecraft.getToasts(),
                SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                Component.translatable("dlc_manager.update.no_updates"),
                Component.empty()
            );
            return;
        }
        DlcUpdateClient.showPrompt(minecraft.screen, result);
    }

    public static void openServerManagerFromCommand() {
        if (!canOpenServerManager()) {
            return;
        }
        openServerManagerFromIngame(null);
    }

    public static void openServerManagerFromIngame(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        ServerData data = minecraft.getCurrentServer();
        if (data == null) {
            minecraft.setScreen(new DlcServerListScreen(parent));
            return;
        }
        DlcServerEntry entry = createEntryFromServer(data);
        if (entry == null) {
            minecraft.setScreen(new DlcServerListScreen(parent));
            return;
        }
        DlcServerList.load();
        DlcServerEntry existing = findEntryByKey(entry.getKey());
        if (existing != null) {
            entry = existing;
        }
        if (entry.password != null && !entry.password.isBlank()) {
            DlcServerEntry finalEntry = entry;
            CompletableFuture
                .supplyAsync(() -> fetchServerInfo(finalEntry))
                .whenComplete((info, throwable) -> minecraft.execute(() -> {
                    if (info != null) {
                        saveEntryToList(finalEntry);
                        DlcServerCache.put(finalEntry.getKey(), info);
                        minecraft.setScreen(new DlcServerManagerScreen(parent, finalEntry, info, DlcServerManagerScreen.Tab.SERVERS, true));
                        return;
                    }
                    minecraft.setScreen(new DlcServerPasswordScreen(parent, finalEntry, false, true));
                }));
            return;
        }
        minecraft.setScreen(new DlcServerPasswordScreen(parent, entry, false, true));
    }

    public static void runStartupChecksAsync(Screen screen) {
        DlcApiService.start(null);
        DlcApiService.setClientStateChangedListener(() -> Minecraft.getInstance().execute(DlcMaLiLibScreen::refreshCurrentOpenScreen));
        if (startupCheckRunning || startupChecksCompleted) {
            return;
        }
        DlcStartupStateSync.syncOnStartup();
        startupCheckRunning = true;
        startupChecksCompleted = true;
        new Thread(() -> {
            LOGGER.info("[dlc-manager] startup check begin");
            long startAt = System.currentTimeMillis();
            UpdateCheckResult updateResult = null;
            boolean hasUpdate = false;
            DlcPackReleaseUpdateService.CheckResult packUpdateResult = null;
            boolean hasPackUpdate = false;
            DlcSelfUpdateService.SelfUpdateResult selfUpdateResult = null;
            boolean hasSelfUpdate = false;
            boolean hasRequired = false;
            java.util.List<DlcRequiredEntry> missing = java.util.List.of();
            boolean updateManagerOn = DlcPathConfig.isUpdateManagerOn();
            boolean selfUpdateAutoCheckOn = DlcPathConfig.isSelfUpdateAutoCheckOn();
            String changelogText = "";
            String changelogVersion = null;
            String currentBaseVersion = "1.0.0";
            String currentDlcVersion = "1.0.0";
            Path dlcRoot = DlcPaths.dlcRoot();
            if (dlcRoot != null) {
                DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
                if (version != null) {
                    if (version.getBase() != null && !version.getBase().isBlank()) {
                        currentBaseVersion = version.getBase();
                    }
                    if (version.getDlc() != null && !version.getDlc().isBlank()) {
                        currentDlcVersion = version.getDlc();
                    }
                }
            }
            String repoUrl = DlcPathConfig.getUpdateGithubRepoUrl();
            if (repoUrl != null && !repoUrl.isBlank()) {
                long packAt = System.currentTimeMillis();
                packUpdateResult = DlcPackReleaseUpdateService.checkForUpdates(
                    DlcPathConfig.getModpackUrl(),
                    repoUrl,
                    currentBaseVersion,
                    currentDlcVersion,
                    DlcPackReleaseUpdateService.Side.CLIENT
                );
                boolean promptSkipped = packUpdateResult != null
                    && packUpdateResult.hasUpdate()
                    && DlcPackReleaseUpdateService.isPromptSkippedForUpdate(packUpdateResult, DlcPackReleaseUpdateService.Side.CLIENT);
                boolean alreadyDownloaded = packUpdateResult != null
                    && packUpdateResult.hasUpdate()
                    && DlcPackReleaseUpdateService.hasDownloadedClientPackages(packUpdateResult);
                hasPackUpdate = packUpdateResult != null && packUpdateResult.hasUpdate() && !alreadyDownloaded && !promptSkipped;
                LOGGER.info("[dlc-manager] pack update check done: hasUpdate={}, alreadyDownloaded={}, promptSkipped={}, took={}ms",
                    hasPackUpdate, alreadyDownloaded, promptSkipped, System.currentTimeMillis() - packAt);
            }
            if (updateManagerOn) {
                if (dlcRoot != null) {
                    DlcUpdateVersion currentVersion = DlcUpdateVersion.load(dlcRoot);
                    if (currentVersion != null) {
                        String currentBase = currentVersion.getBase();
                        String currentDlc = currentVersion.getDlc();
                        String currentMarker = composeChangelogMarker(currentBase, currentDlc);
                        String shown = currentVersion.getChangelogShown();
                        if (shown != null && !shown.isBlank() && !isPairMarker(shown)) {
                            String migratedShown = composeChangelogMarker(currentBase, shown);
                            currentVersion.setChangelogShown(migratedShown);
                            currentVersion.save(dlcRoot);
                            shown = migratedShown;
                        }
                        if (currentDlc != null && !currentDlc.isBlank()
                            && shown != null && !shown.isBlank()
                            && !isSameChangelogMarker(shown, currentMarker, currentDlc)) {
                            List<DlcUpdateMeta> pending = collectPendingChangelogMeta(shown, currentMarker, currentBase, currentDlc);
                            if (!pending.isEmpty()) {
                                changelogText = buildPendingChangelogText(pending, CrossPlatformRuntime.languageCode());
                                if (changelogText != null && !changelogText.isBlank()) {
                                    changelogVersion = currentMarker;
                                }
                            }
                        }
                    }
                }
            }
            if (updateManagerOn && !DlcUpdateClient.isPrompted()) {
                long updateAt = System.currentTimeMillis();
                updateResult = DlcUpdateService.checkForUpdates();
                boolean promptSkipped = updateResult != null
                    && updateResult.hasUpdates()
                    && DlcUpdateService.isPromptSkippedForUpdate(updateResult);
                hasUpdate = updateResult != null && updateResult.hasUpdates() && !promptSkipped;
                int updateCount = updateResult == null || updateResult.getUpdates() == null ? 0 : updateResult.getUpdates().size();
                LOGGER.info("[dlc-manager] update check done: hasUpdate={}, count={}, promptSkipped={}, took={}ms",
                    hasUpdate, updateCount, promptSkipped, System.currentTimeMillis() - updateAt);
            }
            if (selfUpdateAutoCheckOn && !selfUpdatePrompted) {
                long selfAt = System.currentTimeMillis();
                selfUpdateResult = DlcSelfUpdateService.checkForUpdate();
                boolean promptSkipped = selfUpdateResult != null
                    && selfUpdateResult.hasUpdate
                    && DlcSelfUpdateService.isPromptSkippedForUpdate(selfUpdateResult);
                hasSelfUpdate = selfUpdateResult != null && selfUpdateResult.hasUpdate && !promptSkipped;
                LOGGER.info("[dlc-manager] self update check done: hasUpdate={}, promptSkipped={}, took={}ms",
                    hasSelfUpdate, promptSkipped, System.currentTimeMillis() - selfAt);
            }
            if (!hasUpdate) {
                if (!DlcRequiredManager.hasSkipMarker()) {
                    long requiredAt = System.currentTimeMillis();
                    hasRequired = DlcRequiredManager.hasMissingDownloadsForPrompt();
                    if (hasRequired) {
                        missing = DlcRequiredManager.collectMissingDownloadsForPrompt();
                    }
                    LOGGER.info("[dlc-manager] required check done: hasRequired={}, missingCount={}, took={}ms", hasRequired, missing.size(), System.currentTimeMillis() - requiredAt);
                }
            }
            boolean finalHasUpdate = hasUpdate;
            UpdateCheckResult finalUpdateResult = updateResult;
            boolean finalHasPackUpdate = hasPackUpdate;
            DlcPackReleaseUpdateService.CheckResult finalPackUpdateResult = packUpdateResult;
            boolean finalHasSelfUpdate = hasSelfUpdate;
            DlcSelfUpdateService.SelfUpdateResult finalSelfUpdateResult = selfUpdateResult;
            boolean finalHasRequired = hasRequired;
            java.util.List<DlcRequiredEntry> finalMissing = missing;
            String finalChangelog = changelogText;
            String finalChangelogVersion = changelogVersion;
            Minecraft.getInstance().execute(() -> {
                startupCheckRunning = false;
                LOGGER.info("[dlc-manager] startup check finished in {}ms", System.currentTimeMillis() - startAt);
                if (!(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                    return;
                }
                Runnable flow = () -> {
                    DlcNoticeManager.showFromTitle(screen);
                };
                if (finalHasRequired && !finalMissing.isEmpty()) {
                    flow = wrapRequiredPrompt(screen, finalMissing, flow);
                }
                if (finalHasUpdate && finalUpdateResult != null) {
                    flow = wrapUpdatePrompt(screen, finalUpdateResult, flow);
                }
                if (finalChangelog != null && !finalChangelog.isBlank() && finalChangelogVersion != null && !finalChangelogVersion.isBlank()) {
                    flow = wrapChangelogPrompt(screen, finalChangelog, finalChangelogVersion, flow);
                }
                if (finalHasPackUpdate && finalPackUpdateResult != null) {
                    flow = wrapPackUpdatePrompt(screen, finalPackUpdateResult, flow);
                }
                if (finalHasSelfUpdate && finalSelfUpdateResult != null && !selfUpdatePrompted) {
                    flow = wrapSelfUpdatePrompt(screen, finalSelfUpdateResult, flow);
                }
                flow.run();
            });
        }, "dlc-startup-check").start();
    }

    private static Runnable wrapSelfUpdatePrompt(Screen fallback, DlcSelfUpdateService.SelfUpdateResult result, Runnable next) {
        return () -> {
            if (result == null || !result.hasUpdate) {
                next.run();
                return;
            }
            selfUpdatePrompted = true;
            Screen continuation = createContinuationScreen(fallback, next);
            Minecraft.getInstance().setScreen(new DlcSelfUpdateScreen(continuation, result, true));
        };
    }

    private static Runnable wrapPackUpdatePrompt(Screen fallback, DlcPackReleaseUpdateService.CheckResult result, Runnable next) {
        return () -> {
            if (result == null || !result.hasUpdate()) {
                next.run();
                return;
            }
            Screen continuation = createContinuationScreen(fallback, next);
            Minecraft.getInstance().setScreen(new DlcPackReleaseUpdateScreen(continuation, result, true));
        };
    }

    private static Runnable wrapChangelogPrompt(Screen fallback, String changelog, String changelogVersion, Runnable next) {
        return () -> {
            if (changelog == null || changelog.isBlank() || changelogVersion == null || changelogVersion.isBlank()) {
                next.run();
                return;
            }
            Screen continuation = createContinuationScreen(fallback, next);
            Minecraft.getInstance().setScreen(new DlcUpdateChangelogScreen(continuation, changelog, () -> {
                Path root = DlcPaths.dlcRoot();
                if (root != null) {
                    DlcUpdateVersion version = DlcUpdateVersion.load(root);
                    version.setChangelogShown(changelogVersion);
                    version.save(root);
                }
                Minecraft.getInstance().setScreen(continuation);
            }));
        };
    }

    private static Runnable wrapUpdatePrompt(Screen fallback, UpdateCheckResult result, Runnable next) {
        return () -> {
            if (result == null || !result.hasUpdates()) {
                next.run();
                return;
            }
            DlcUpdateClient.markPrompted();
            Screen continuation = createContinuationScreen(fallback, next);
            Minecraft.getInstance().setScreen(new DlcUpdatePromptScreen(continuation, result, true));
        };
    }

    private static Runnable wrapRequiredPrompt(Screen fallback, List<DlcRequiredEntry> missing, Runnable next) {
        return () -> {
            if (missing == null || missing.isEmpty()) {
                next.run();
                return;
            }
            DlcRequiredManager.markPrompted();
            Screen continuation = createContinuationScreen(fallback, next);
            Minecraft.getInstance().setScreen(new DlcRequiredDownloadScreen(continuation, missing));
        };
    }

    private static Screen createContinuationScreen(Screen fallback, Runnable next) {
        return new Screen(Component.empty()) {
            private boolean started;

            @Override
            protected void init() {
                if (started) {
                    return;
                }
                started = true;
                Minecraft minecraft = Minecraft.getInstance();
                next.run();
                if (minecraft.screen == this) {
                    minecraft.setScreen(fallback);
                }
            }

            @Override
            public boolean shouldCloseOnEsc() {
                return false;
            }
        };
    }

    private static List<DlcUpdateMeta> collectPendingChangelogMeta(String shownMarker, String currentMarker, String currentBaseVersion, String currentDlcVersion) {
        if (shownMarker == null || shownMarker.isBlank()
            || currentMarker == null || currentMarker.isBlank()
            || currentBaseVersion == null || currentBaseVersion.isBlank()
            || currentDlcVersion == null || currentDlcVersion.isBlank()) {
            return List.of();
        }
        String shown = shownMarker.trim();
        String currentBase = safeVersion(currentBaseVersion);
        String currentDlc = safeVersion(currentDlcVersion);
        String shownBase = extractBaseFromMarker(shown);
        String shownDlc = extractDlcFromMarker(shown);
        if (!isPairMarker(shown) && shownDlc.equals(currentDlc)) {
            return List.of();
        }
        if (isPairMarker(shown) && shownBase.equals(currentBase) && shownDlc.equals(currentDlc)) {
            DlcUpdateMeta latest = DlcUpdateService.loadMetaForVersion(currentBase, currentDlc);
            if (latest == null) {
                return List.of();
            }
            String fromMarker = composeChangelogMarker(latest.getBaseOld(), latest.getDlcOld());
            String toMarker = composeChangelogMarker(resolveMetaTargetBase(latest), resolveMetaTargetDlc(latest));
            if (fromMarker.equals(shown) && toMarker.equals(currentMarker.trim())) {
                return List.of(latest);
            }
            return List.of();
        }
        List<DlcUpdateMeta> reverse = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String cursorBase = currentBase;
        String cursorDlc = currentDlc;
        boolean pairShown = isPairMarker(shown);
        while (cursorDlc != null
            && !cursorDlc.isBlank()
            && !(pairShown
                ? (cursorBase.equals(shownBase) && cursorDlc.equals(shownDlc))
                : cursorDlc.equals(shownDlc))
            && visited.add(composeChangelogMarker(cursorBase, cursorDlc))) {
            DlcUpdateMeta meta = DlcUpdateService.loadMetaForVersion(cursorBase, cursorDlc);
            if (meta == null) {
                break;
            }
            String oldBase = resolveMetaSourceBase(meta);
            String oldDlc = resolveMetaSourceDlc(meta);
            String newBase = resolveMetaTargetBase(meta);
            String newDlc = resolveMetaTargetDlc(meta);
            if (oldBase == null || oldBase.isBlank() || oldDlc == null || oldDlc.isBlank()
                || newBase == null || newBase.isBlank() || newDlc == null || newDlc.isBlank()) {
                break;
            }
            if (!cursorBase.equals(newBase) || !cursorDlc.equals(newDlc)) {
                break;
            }
            reverse.add(meta);
            cursorBase = oldBase;
            cursorDlc = oldDlc;
        }
        if (reverse.isEmpty()) {
            return List.of();
        }
        boolean reached = pairShown
            ? shownBase.equals(cursorBase) && shownDlc.equals(cursorDlc)
            : shownDlc.equals(cursorDlc);
        if (!reached) {
            return List.of();
        }
        Collections.reverse(reverse);
        return reverse;
    }

    private static String buildPendingChangelogText(List<DlcUpdateMeta> metas, String lang) {
        if (metas == null || metas.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (DlcUpdateMeta meta : metas) {
            if (meta == null) {
                continue;
            }
            String log = meta.resolveChangelog(lang);
            if (log == null || log.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            String fromMarker = composeChangelogMarker(meta.getBaseOld(), meta.getDlcOld());
            String toMarker = composeChangelogMarker(resolveMetaTargetBase(meta), resolveMetaTargetDlc(meta));
            builder
                .append(fromMarker);
            if (!fromMarker.equals(toMarker)) {
                builder.append(" -> ").append(toMarker);
            }
            builder
                .append("\n")
                .append(log.trim());
        }
        return builder.toString();
    }

    private static boolean isSameChangelogMarker(String shown, String currentMarker, String currentDlc) {
        if (shown == null || shown.isBlank()) {
            return false;
        }
        String normalizedShown = shown.trim();
        if (isPairMarker(normalizedShown)) {
            return normalizedShown.equals(currentMarker == null ? "" : currentMarker.trim());
        }
        return safeVersion(normalizedShown).equals(safeVersion(currentDlc));
    }

    private static boolean isPairMarker(String marker) {
        return marker != null && marker.indexOf('+') > 0;
    }

    private static String extractDlcFromMarker(String marker) {
        if (marker == null || marker.isBlank()) {
            return safeVersion(null);
        }
        int plus = marker.lastIndexOf('+');
        if (plus >= 0 && plus + 1 < marker.length()) {
            return safeVersion(marker.substring(plus + 1));
        }
        return safeVersion(marker);
    }

    private static String extractBaseFromMarker(String marker) {
        if (marker == null || marker.isBlank()) {
            return safeVersion(null);
        }
        int plus = marker.indexOf('+');
        if (plus > 0) {
            return safeVersion(marker.substring(0, plus));
        }
        return safeVersion(null);
    }

    private static String composeChangelogMarker(String base, String dlc) {
        return safeVersion(base) + "+" + safeVersion(dlc);
    }

    private static String resolveMetaSourceDlc(DlcUpdateMeta meta) {
        return safeVersion(meta == null ? null : meta.getDlcOld());
    }

    private static String resolveMetaTargetDlc(DlcUpdateMeta meta) {
        if (meta == null) {
            return safeVersion(null);
        }
        String dlcNew = meta.getDlcNew();
        if (dlcNew == null || dlcNew.isBlank()) {
            return resolveMetaSourceDlc(meta);
        }
        return safeVersion(dlcNew);
    }

    private static String resolveMetaSourceBase(DlcUpdateMeta meta) {
        return safeVersion(meta == null ? null : meta.getBaseOld());
    }

    private static String resolveMetaTargetBase(DlcUpdateMeta meta) {
        if (meta == null) {
            return safeVersion(null);
        }
        String baseNew = meta.getBaseNew();
        if (baseNew == null || baseNew.isBlank()) {
            return resolveMetaSourceBase(meta);
        }
        return safeVersion(baseNew);
    }

    private static String safeVersion(String value) {
        return value == null || value.isBlank() ? "1.0.0" : value.trim();
    }

    public static void syncEnabledResourcePacks() {
        Minecraft minecraft = Minecraft.getInstance();
        PackRepository repo = minecraft.getResourcePackRepository();
        try {
            repo.reload();
            java.util.Set<String> selected = new java.util.LinkedHashSet<>(repo.getSelectedIds());
            java.util.Set<String> originalSelected = new java.util.LinkedHashSet<>(selected);
            boolean modified = false;
            var grouped = new DlcScanner().scan();
            for (DlcItem item : grouped.getOrDefault(DlcType.RESOURCE_PACK, java.util.List.of())) {
                if (!item.isEnabled()) {
                    continue;
                }
                String fileName = item.getSourcePath().getFileName().toString();
                String packId = resolvePackId(repo, fileName);
                if (packId == null) {
                    java.nio.file.Path target = minecraft.getResourcePackDirectory().resolve(fileName);
                    copyRecursive(item.getSourcePath(), target);
                    repo.reload();
                    packId = resolvePackId(repo, fileName);
                    modified = true;
                }
                if (packId != null && !isPackSelected(repo, selected, fileName)) {
                    if (selected.add(packId)) {
                        modified = true;
                    }
                }
            }
            if (modified || !selected.equals(originalSelected)) {
                repo.setSelected(selected);
                minecraft.options.resourcePacks = new java.util.ArrayList<>(selected);
                minecraft.options.save();
                minecraft.reloadResourcePacks();
            }
        } catch (Throwable ignored) {
        }
    }

    private static DlcServerEntry createEntryFromServer(ServerData data) {
        DlcServerEntry entry = new DlcServerEntry();
        entry.name = data.name;
        String ip = data.ip == null ? "" : data.ip;
        String host = ip;
        int port = DlcPathConfig.getServerPort();
        int index = ip.lastIndexOf(':');
        if (index > 0 && index < ip.length() - 1) {
            if (ip.startsWith("[") && ip.contains("]")) {
                int end = ip.indexOf(']');
                if (end > 1) {
                    host = ip.substring(1, end);
                }
            } else {
                host = ip.substring(0, index);
            }
        }
        entry.host = host;
        entry.port = port;
        entry.useTcp = true;
        return entry;
    }

    private static DlcServerInfo fetchServerInfo(DlcServerEntry entry) {
        try {
            return DlcServerConnection.fetchServerInfo(
                entry,
                5000,
                currentPlayerName(),
                currentPlayerUuid()
            );
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void updateTitleNoticeText(DlcNoticeConfig config, String lang) {
        String langKey = lang == null ? "" : lang;
        if (!langKey.equals(titleNoticeLang)) {
            titleNoticeLang = langKey;
            titleNoticeTitle = config.getTitle().resolve(langKey);
            titleNoticeSubheadEnabled = config.getSubhead().enabled;
            titleNoticeSubhead = config.getSubhead().text.resolve(langKey);
            titleNoticeText = "";
            titleNoticeLoading = false;
            titleNoticeFuture = null;
            titleNoticeScrollOffset = 0;
            titleNoticeDraggingScrollbar = false;
            String url = config.getContentUrl(langKey);
            if (url == null || url.isBlank()) {
                titleNoticeText = config.getContents().resolve(langKey);
            } else {
                titleNoticeLoading = true;
                String fallback = config.getContents().resolve(langKey);
                titleNoticeFuture = CompletableFuture.supplyAsync(() -> {
                    String remote = config.resolveContents(langKey);
                    return remote == null || remote.isBlank() ? fallback : remote;
                });
            }
        }
        if (titleNoticeLoading && titleNoticeFuture != null && titleNoticeFuture.isDone()) {
            String fallback = config.getContents().resolve(titleNoticeLang);
            try {
                String resolved = titleNoticeFuture.getNow(fallback);
                titleNoticeText = resolved == null || resolved.isBlank() ? fallback : resolved;
            } catch (Exception ignored) {
                titleNoticeText = fallback;
            }
            titleNoticeLoading = false;
            titleNoticeFuture = null;
        }
    }

    private static String currentPlayerName() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getGameProfile() != null) {
            String name = minecraft.player.getGameProfile().getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return CrossPlatformRuntime.playerName();
    }

    private static String currentPlayerUuid() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getGameProfile() != null && minecraft.player.getGameProfile().getId() != null) {
            return minecraft.player.getGameProfile().getId().toString();
        }
        return CrossPlatformRuntime.playerUuid();
    }

    private static DlcServerEntry findEntryByKey(String key) {
        if (key == null) {
            return null;
        }
        for (DlcServerEntry entry : DlcServerList.getEntries()) {
            if (entry != null && key.equals(entry.getKey())) {
                return entry;
            }
        }
        return null;
    }

    private static void saveEntryToList(DlcServerEntry entry) {
        DlcServerList.load();
        java.util.List<DlcServerEntry> list = new java.util.ArrayList<>(DlcServerList.getEntries());
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            DlcServerEntry existing = list.get(i);
            if (existing != null && entry.getKey().equals(existing.getKey())) {
                existing.password = entry.password;
                if (entry.name != null && !entry.name.isBlank()) {
                    existing.name = entry.name;
                }
                found = true;
                break;
            }
        }
        if (!found) {
            list.add(entry);
        }
        DlcServerList.setEntries(list);
        DlcServerList.setLastSelectedKey(entry.getKey());
        DlcServerList.save();
    }

    private static String resolvePackId(PackRepository repo, String fileName) {
        String fileId = "file/" + fileName;
        if (repo.isAvailable(fileId)) {
            return fileId;
        }
        if (repo.isAvailable(fileName)) {
            return fileName;
        }
        for (String id : repo.getAvailableIds()) {
            if (id.endsWith("/" + fileName) || id.endsWith(fileName)) {
                return id;
            }
        }
        return null;
    }

    private static boolean isPackSelected(PackRepository repo, java.util.Set<String> selected, String fileName) {
        String fileId = "file/" + fileName;
        for (String id : selected) {
            if (!repo.isAvailable(id)) {
                continue;
            }
            if (id.equals(fileId) || id.equals(fileName) || id.endsWith("/" + fileName) || id.endsWith(fileName)) {
                return true;
            }
        }
        return false;
    }

    private static void copyRecursive(java.nio.file.Path source, java.nio.file.Path target) throws java.io.IOException {
        if (java.nio.file.Files.isDirectory(source)) {
            java.nio.file.Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(
                    java.nio.file.Path dir,
                    java.nio.file.attribute.BasicFileAttributes attrs
                ) throws java.io.IOException {
                    java.nio.file.Path relative = source.relativize(dir);
                    java.nio.file.Files.createDirectories(target.resolve(relative));
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(
                    java.nio.file.Path file,
                    java.nio.file.attribute.BasicFileAttributes attrs
                ) throws java.io.IOException {
                    java.nio.file.Path relative = source.relativize(file);
                    java.nio.file.Files.copy(
                        file,
                        target.resolve(relative),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    );
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } else {
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
