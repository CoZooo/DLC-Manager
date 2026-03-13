package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerUpdateInfo;
import com.cozooo.dlc_manager.client.server.DlcServerUpdateState;
import com.cozooo.dlc_manager.client.server.DlcServerConnection.UpdateResponse;
import com.cozooo.dlc_manager.client.update.DlcUpdateFilePicker;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateRowPlanner;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.text.DlcTextFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DlcServerUpdateManagerScreen extends Screen {
    private static final AtomicBoolean PACK_RELEASE_CHECKED_ONCE = new AtomicBoolean(false);
    private final Screen parent;
    private final DlcServerEntry entry;
    private Button importButton;
    private Button pushUpdateButton;
    private Button urlButton;
    private Button packUpdateCheckButton;
    private Button backButton;
    private int scrollIndex;
    private List<DlcUpdateMeta> rows = List.of();
    private List<DlcUpdateRowPlanner.Action> rowActions = List.of();
    private List<String> rollbackTargets = List.of();
    private List<String> actionOld = List.of();
    private List<String> actionNew = List.of();
    private List<String> displayOld = List.of();
    private List<String> displayNew = List.of();
    private final List<Button> actionButtons = new ArrayList<>();
    private String currentVersion = "";
    private String baseVersion = "";
    private String rollbackFrom;
    private boolean rolledBackMode;
    private boolean hasVisibleUpdates;
    private boolean loading;
    private boolean loadFailed;
    private boolean applying;
    private String packUpdateRepoUrl = "";
    private String packModpackUrl = "";

    public DlcServerUpdateManagerScreen(Screen parent, DlcServerEntry entry) {
        super(Component.translatable("dlc_manager.update.manager_title"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        refreshData();
    }

    void refreshData() {
        loading = true;
        loadFailed = false;
        applying = false;
        rows = List.of();
        rowActions = List.of();
        rollbackTargets = List.of();
        actionOld = List.of();
        actionNew = List.of();
        displayOld = List.of();
        displayNew = List.of();
        buildWidgets();
        new Thread(() -> {
            DlcServerUpdateState state;
            boolean shouldCheckPackUpdate = false;
            DlcPackReleaseUpdateService.CheckResult packUpdateResult = null;
            try {
                state = DlcServerConnection.fetchUpdateState(entry, 8000);
            } catch (Exception ex) {
                state = null;
            }
            if (state != null) {
                shouldCheckPackUpdate = PACK_RELEASE_CHECKED_ONCE.compareAndSet(false, true);
            }
            if (state != null && shouldCheckPackUpdate
                && state.updateGithubRepo != null && !state.updateGithubRepo.isBlank()) {
                packUpdateResult = DlcPackReleaseUpdateService.checkForUpdates(
                    state.modpackUrl,
                    state.updateGithubRepo,
                    state.baseVersion,
                    state.dlcVersion,
                    DlcPackReleaseUpdateService.Side.SERVER
                );
            }
            DlcServerUpdateState finalState = state;
            boolean finalShouldCheckPackUpdate = shouldCheckPackUpdate;
            DlcPackReleaseUpdateService.CheckResult finalPackUpdateResult = packUpdateResult;
            Minecraft.getInstance().execute(() -> {
                loading = false;
                if (finalState == null) {
                    loadFailed = true;
                    buildWidgets();
                    return;
                }
                loadFailed = false;
                baseVersion = finalState.baseVersion == null ? "" : finalState.baseVersion;
                currentVersion = finalState.dlcVersion == null ? "" : finalState.dlcVersion;
                packUpdateRepoUrl = finalState.updateGithubRepo == null ? "" : finalState.updateGithubRepo.trim();
                packModpackUrl = finalState.modpackUrl == null ? "" : finalState.modpackUrl.trim();
                rollbackFrom = finalState.rollbackFrom;
                rolledBackMode = DlcUpdateService.isRollbackMarkerActive(rollbackFrom, baseVersion, currentVersion);
                buildRows(finalState);
                buildWidgets();
                if (finalShouldCheckPackUpdate
                    && finalPackUpdateResult != null
                    && finalPackUpdateResult.hasUpdate()) {
                    Minecraft.getInstance().setScreen(new DlcPackReleaseUpdateScreen(this, finalPackUpdateResult, entry));
                }
            });
        }, "dlc-server-update-refresh").start();
    }

    private void buildRows(DlcServerUpdateState state) {
        DlcUpdateRowPlanner.PlanResult plan = DlcUpdateRowPlanner.planServerRows(
            state.history, state.updates, rolledBackMode, baseVersion, currentVersion
        );
        List<DlcUpdateMeta> list = new ArrayList<>(plan.rows.size());
        List<DlcUpdateRowPlanner.Action> actions = new ArrayList<>(plan.rows.size());
        List<String> targets = new ArrayList<>(plan.rows.size());
        List<String> olds = new ArrayList<>(plan.rows.size());
        List<String> news = new ArrayList<>(plan.rows.size());
        List<String> displayOlds = new ArrayList<>(plan.rows.size());
        List<String> displayNews = new ArrayList<>(plan.rows.size());
        for (DlcUpdateRowPlanner.UpdateRow row : plan.rows) {
            list.add(row.meta);
            actions.add(row.action);
            targets.add(row.rollbackTarget);
            olds.add(row.oldVersion);
            news.add(row.newVersion);
            displayOlds.add(row.displayOldVersion);
            displayNews.add(row.displayNewVersion);
        }
        hasVisibleUpdates = plan.hasVisibleUpdates;
        rows = list;
        rowActions = actions;
        rollbackTargets = targets;
        actionOld = olds;
        actionNew = news;
        displayOld = displayOlds;
        displayNew = displayNews;
    }

    private void buildWidgets() {
        clearWidgets();
        int buttonCount = 5;
        int margin = 12;
        int spacing = 8;
        int availableWidth = Math.max(200, this.width - margin * 2);
        int buttonWidth = Math.min(110, Math.max(40, (availableWidth - spacing * (buttonCount - 1)) / buttonCount));
        int buttonHeight = 20;
        int y = this.height - 40;
        int totalWidth = buttonWidth * buttonCount + spacing * (buttonCount - 1);
        int startX = Math.max(margin, (this.width - totalWidth) / 2);
        importButton = Button.builder(Component.translatable("dlc_manager.update.import"), btn -> importUpdate())
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build();
        int pushX = startX + buttonWidth + spacing;
        pushUpdateButton = Button.builder(Component.translatable("dlc_manager.server.update_push"), btn -> pushUpdates())
            .bounds(pushX, y, buttonWidth, buttonHeight)
            .build();
        int urlX = pushX + buttonWidth + spacing;
        urlButton = Button.builder(Component.translatable("dlc_manager.update.import_url"),
                btn -> Minecraft.getInstance().setScreen(new DlcServerUpdateUrlScreen(this, entry)))
            .bounds(urlX, y, buttonWidth, buttonHeight)
            .build();
        int packCheckX = urlX + buttonWidth + spacing;
        packUpdateCheckButton = Button.builder(Component.translatable("dlc_manager.pack_update.check_button"), btn -> checkPackUpdate())
            .bounds(packCheckX, y, buttonWidth, buttonHeight)
            .build();
        packUpdateCheckButton.active = !packUpdateRepoUrl.isBlank();
        int backX = packCheckX + buttonWidth + spacing;
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(backX, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(importButton);
        addRenderableWidget(pushUpdateButton);
        addRenderableWidget(urlButton);
        addRenderableWidget(packUpdateCheckButton);
        addRenderableWidget(backButton);

        actionButtons.clear();
        for (int i = 0; i < rows.size(); i++) {
            DlcUpdateRowPlanner.Action action =
                i < rowActions.size() ? rowActions.get(i) : DlcUpdateRowPlanner.Action.NONE;
            Component label = switch (action) {
                case REUPDATE -> Component.translatable("dlc_manager.update.reapply");
                case UPDATE -> Component.translatable("dlc_manager.update.apply");
                default -> Component.translatable("dlc_manager.update.rollback");
            };
            int buttonW = (action == DlcUpdateRowPlanner.Action.REUPDATE
                || action == DlcUpdateRowPlanner.Action.UPDATE) ? 80 : 50;
            Button actionButton = Button.builder(label, btn -> {
                int idx = actionButtons.indexOf(btn);
                if (idx < 0 || idx >= rowActions.size()) {
                    return;
                }
                DlcUpdateRowPlanner.Action chosen = rowActions.get(idx);
                String oldVersion = idx < actionOld.size() ? actionOld.get(idx) : null;
                String newVersion = idx < actionNew.size() ? actionNew.get(idx) : null;
                String rollbackTarget = idx < rollbackTargets.size() ? rollbackTargets.get(idx) : null;
                DlcUpdateMeta meta = idx < rows.size() ? rows.get(idx) : null;
                String baseOld = meta == null ? null : meta.getBaseOld();
                String baseNew = meta == null ? null : meta.getBaseNew();
                if (chosen == DlcUpdateRowPlanner.Action.ROLLBACK) {
                    confirmRollback(rollbackTarget);
                } else if (chosen == DlcUpdateRowPlanner.Action.REUPDATE) {
                    confirmReupdate(baseOld, oldVersion, baseNew, newVersion);
                } else if (chosen == DlcUpdateRowPlanner.Action.UPDATE) {
                    confirmApply(baseOld, oldVersion, baseNew, newVersion);
                }
            }).bounds(-10000, -10000, buttonW, 16).build();
            actionButton.visible = false;
            addRenderableWidget(actionButton);
            actionButtons.add(actionButton);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int y = 20;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, y, 0xFFFFFF);
        y += 16;
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.base", baseVersion), centerX, y, 0xC8C8C8);
        y += 14;
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.current", currentVersion), centerX, y, 0xC8C8C8);
        y += 14;
        if (rolledBackMode) {
            String currentPair = normalizeVersion(baseVersion) + "+" + normalizeVersion(currentVersion);
            guiGraphics.drawCenteredString(this.font,
                Component.translatable("dlc_manager.update.rollback_info", rollbackFrom, currentPair),
                centerX, y, 0xE6E6E6);
            y += 12;
        }
        if (hasVisibleUpdates) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.desc"), centerX, y, 0xE6E6E6);
            y += 12;
        }
        drawHistoryList(guiGraphics, y, mouseX, mouseY);
    }

    private void drawHistoryList(GuiGraphics guiGraphics, int startY, int mouseX, int mouseY) {
        int listTop = startY;
        int listBottom = this.height - 60;
        int lineHeight = 20;
        int totalLines = rows.size();
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        if (loading) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.update_checking"),
                this.width / 2, (listTop + listBottom) / 2, 0xC8C8C8);
            return;
        }
        if (loadFailed) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.update_failed"),
                this.width / 2, (listTop + listBottom) / 2, 0xFF7777);
            return;
        }
        if (rows.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.server.update_empty"),
                this.width / 2, (listTop + listBottom) / 2, 0xC8C8C8);
            return;
        }
        int y = listTop;
        int visibleEnd = Math.min(totalLines, scrollIndex + maxVisible);
        for (int i = scrollIndex; i < visibleEnd; i++) {
            DlcUpdateMeta meta = rows.get(i);
            String sourceBase = meta == null ? null : meta.getBaseOld();
            String sourceDlc = meta == null ? null : meta.getDlcOld();
            String targetBase = meta == null ? null : meta.getBaseNew();
            String targetDlc = meta == null ? null : meta.getDlcNew();
            if (targetBase == null || targetBase.isBlank()) {
                targetBase = sourceBase;
            }
            if (targetDlc == null || targetDlc.isBlank()) {
                targetDlc = sourceDlc;
            }
            String oldText = i < displayOld.size() ? displayOld.get(i) : normalizeVersion(sourceBase) + "+" + normalizeVersion(sourceDlc);
            String newText = i < displayNew.size() ? displayNew.get(i) : normalizeVersion(targetBase) + "+" + normalizeVersion(targetDlc);
            String label = oldText + " -> " + newText;
            if (i < rowActions.size() && rowActions.get(i) == DlcUpdateRowPlanner.Action.REUPDATE) {
                label = label + " " + Component.translatable("dlc_manager.update.rolled_back").getString();
            }
            guiGraphics.drawString(this.font, label, 24, y + 6, 0xE6E6E6, false);
            int buttonIndex = i;
            int buttonWidth = buttonIndex < rowActions.size()
                && (rowActions.get(buttonIndex) == DlcUpdateRowPlanner.Action.REUPDATE
                    || rowActions.get(buttonIndex) == DlcUpdateRowPlanner.Action.UPDATE)
                ? 80
                : 50;
            int buttonHeight = 16;
            int buttonX = this.width - 24 - buttonWidth;
            int buttonY = y + 2;
            if (buttonIndex >= 0 && buttonIndex < actionButtons.size()) {
                Button actionButton = actionButtons.get(buttonIndex);
                DlcUpdateRowPlanner.Action action = buttonIndex < rowActions.size()
                    ? rowActions.get(buttonIndex)
                    : DlcUpdateRowPlanner.Action.NONE;
                boolean enabled = action != DlcUpdateRowPlanner.Action.NONE && !applying;
                actionButton.visible = enabled;
                actionButton.active = enabled;
                if (enabled) {
                    actionButton.setX(buttonX);
                    actionButton.setY(buttonY);
                } else {
                    actionButton.setX(-10000);
                    actionButton.setY(-10000);
                }
            }
            y += lineHeight;
        }
        for (int i = 0; i < actionButtons.size(); i++) {
            if (i < scrollIndex || i >= visibleEnd) {
                Button actionButton = actionButtons.get(i);
                actionButton.visible = false;
                actionButton.setX(-10000);
                actionButton.setY(-10000);
            }
        }
        renderChangelogTooltip(guiGraphics, listTop, listBottom, lineHeight, totalLines, maxVisible, mouseX, mouseY, rows);
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);
    }

    private void renderChangelogTooltip(GuiGraphics guiGraphics, int listTop, int listBottom, int lineHeight,
                                        int totalLines, int maxVisible, int mouseX, int mouseY,
                                        List<DlcUpdateMeta> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        if (mouseY < listTop || mouseY > listBottom) {
            return;
        }
        int hoverIndex = scrollIndex + Math.max(0, (mouseY - listTop) / lineHeight);
        if (hoverIndex < scrollIndex || hoverIndex >= rows.size() || hoverIndex >= scrollIndex + maxVisible) {
            return;
        }
        DlcUpdateMeta meta = rows.get(hoverIndex);
        String lang = CrossPlatformRuntime.languageCode();
        String changelog = meta == null ? "" : meta.resolveChangelog(lang);
        if (changelog == null || changelog.isBlank()) {
            return;
        }
        int maxWidth = Math.max(120, this.width - 60);
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(this.font.split(Component.translatable("dlc_manager.update.changelog_tooltip_title"), maxWidth));
        lines.addAll(DlcTextFormatter.splitMultiline(this.font, changelog, maxWidth));
        guiGraphics.renderTooltip(this.font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int listTop = 20 + 16 + 14
            + (rolledBackMode ? 12 : 0)
            + (hasVisibleUpdates ? 12 : 0);
        int listBottom = this.height - 60;
        int lineHeight = 20;
        int totalLines = rows.size();
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = vertical > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private void confirmRollback(String targetVersion) {
        if (targetVersion == null || targetVersion.isBlank()) {
            return;
        }
        Component title = Component.translatable("dlc_manager.update.manager_title");
        Component message = Component.translatable("dlc_manager.update.confirm_rollback", targetVersion);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                startRollback(targetVersion);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")));
    }

    private void confirmReupdate(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        String[] compact = compactTransition(baseOld, dlcOld, baseNew, dlcNew);
        Component title = Component.translatable("dlc_manager.update.manager_title");
        Component message = Component.translatable("dlc_manager.update.confirm_reapply", compact[0], compact[1]);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                startApply(baseOld, dlcOld, baseNew, dlcNew);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")));
    }

    private void confirmApply(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        Component title = Component.translatable("dlc_manager.update.manager_title");
        Component message = Component.translatable("dlc_manager.update.confirm_apply");
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                startApply(baseOld, dlcOld, baseNew, dlcNew);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")));
    }

    private void startRollback(String targetVersion) {
        setApplying(true);
        new Thread(() -> {
            UpdateResponse result;
            try {
                result = DlcServerConnection.rollbackUpdate(entry, 8000, targetVersion);
            } catch (Exception ex) {
                result = null;
            }
            UpdateResponse finalResult = result;
            Minecraft.getInstance().execute(() -> handleUpdateResult(finalResult));
        }, "dlc-server-update-rollback").start();
    }

    private void startApply(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        setApplying(true);
        new Thread(() -> {
            UpdateResponse result;
            try {
                result = DlcServerConnection.applyUpdate(entry, 8000, baseOld, dlcOld, baseNew, dlcNew);
            } catch (Exception ex) {
                result = null;
            }
            UpdateResponse finalResult = result;
            Minecraft.getInstance().execute(() -> handleUpdateResult(finalResult));
        }, "dlc-server-update-apply").start();
    }

    private void handleUpdateResult(UpdateResponse result) {
        setApplying(false);
        if (result == null || !result.ok) {
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.server.update_push_failed")));
            return;
        }
        Component title = Component.translatable("dlc_manager.update.manager_title");
        Component message = Component.translatable("dlc_manager.server.update.restart");
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                restartServer();
            } else {
                refreshData();
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, Component.translatable("dlc_manager.restart_now"), Component.translatable("dlc_manager.restart_later")));
    }

    private void restartServer() {
        new Thread(() -> {
            try {
                DlcServerConnection.restartServer(entry, 8000);
            } catch (Exception ignored) {
            }
            Minecraft.getInstance().execute(() -> {
                refreshData();
                Minecraft.getInstance().setScreen(this);
            });
        }, "dlc-server-restart").start();
    }

    private void setApplying(boolean value) {
        applying = value;
        if (importButton != null) {
            importButton.active = !value;
        }
        if (urlButton != null) {
            urlButton.active = !value;
        }
        if (pushUpdateButton != null) {
            pushUpdateButton.active = !value;
        }
        if (backButton != null) {
            backButton.active = !value;
        }
        for (Button button : actionButtons) {
            button.active = !value;
        }
    }

    private void importUpdate() {
        if (applying || loading) {
            return;
        }
        Path zip = DlcUpdateFilePicker.pickAnyFile(Component.translatable("dlc_manager.update.import").getString());
        if (zip == null) {
            return;
        }
        setApplying(true);
        new Thread(() -> {
            boolean ok;
            try {
                ok = DlcServerConnection.importUpdatePackage(entry, 8000, zip);
            } catch (Exception ex) {
                ok = false;
            }
            boolean success = ok;
            Minecraft.getInstance().execute(() -> {
                setApplying(false);
                if (!success) {
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.failed")));
                } else {
                    refreshData();
                    Minecraft.getInstance().setScreen(this);
                }
            });
        }, "dlc-server-update-import").start();
    }

    private void checkPackUpdate() {
        if (packUpdateRepoUrl == null || packUpdateRepoUrl.isBlank()) {
            return;
        }
        if (packUpdateCheckButton != null) {
            packUpdateCheckButton.active = false;
        }
        String base = (baseVersion == null || baseVersion.isBlank()) ? "1.0.0" : baseVersion;
        String dlc = (currentVersion == null || currentVersion.isBlank()) ? "1.0.0" : currentVersion;
        new Thread(() -> {
            DlcPackReleaseUpdateService.CheckResult result;
            try {
                result = DlcPackReleaseUpdateService.checkForUpdates(
                    packModpackUrl,
                    packUpdateRepoUrl,
                    base,
                    dlc,
                    DlcPackReleaseUpdateService.Side.SERVER
                );
            } catch (Exception ex) {
                result = null;
            }
            DlcPackReleaseUpdateService.CheckResult finalResult = result;
            Minecraft.getInstance().execute(() -> {
                if (packUpdateCheckButton != null) {
                    packUpdateCheckButton.active = !packUpdateRepoUrl.isBlank();
                }
                if (finalResult != null && finalResult.hasUpdate()) {
                    Minecraft.getInstance().setScreen(new DlcPackReleaseUpdateScreen(this, finalResult, entry));
                    return;
                }
                if (finalResult != null && finalResult.error != null && !finalResult.error.isBlank()) {
                    showToast(Component.translatable("dlc_manager.update.manager_title"),
                        Component.translatable("dlc_manager.pack_update.check_failed", finalResult.error));
                    return;
                }
                showToast(Component.translatable("dlc_manager.update.manager_title"),
                    Component.translatable("dlc_manager.pack_update.no_updates"));
            });
        }, "dlc-server-pack-update-check-manual").start();
    }

    private void showToast(Component title, Component desc) {
        Minecraft minecraft = Minecraft.getInstance();
        SystemToast.addOrUpdate(minecraft.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, desc);
    }

    private void pushUpdates() {
        if (applying || loading) {
            return;
        }
        Path updateDir = DlcPathConfig.getDlcRoot().resolve("update");
        if (!Files.isDirectory(updateDir)) {
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.server.update_push_failed")));
            return;
        }
        Path gameDir = DlcPaths.gameDir();
        if (!updateDir.startsWith(gameDir)) {
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.server.update_push_failed")));
            return;
        }
        String relative = gameDir.relativize(updateDir).toString().replace('\\', '/');
        if (relative.isBlank()) {
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.failed")));
            return;
        }

        setApplying(true);
        new Thread(() -> {
            boolean ok;
            try {
                ok = DlcServerConnection.push(entry, 20000, List.of(), List.of(relative), "overwrite");
            } catch (IOException ex) {
                ok = false;
            }
            boolean success = ok;
            Minecraft.getInstance().execute(() -> {
                setApplying(false);
                if (!success) {
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.server.update_push_failed")));
                } else {
                    refreshData();
                    Minecraft.getInstance().setScreen(this);
                }
            });
        }, "dlc-server-update-push").start();
    }

    private void drawScrollBar(GuiGraphics guiGraphics, int top, int bottom, int totalLines, int maxVisible, int maxScroll) {
        if (totalLines <= maxVisible) {
            return;
        }
        int barX = this.width - 18;
        int barWidth = 6;
        int barHeight = Math.max(20, (bottom - top) * maxVisible / Math.max(1, totalLines));
        int trackHeight = bottom - top;
        int barY = top + (trackHeight - barHeight) * scrollIndex / Math.max(1, maxScroll);
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF3B3B3B);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B);
    }

    private static String normalizeVersion(String value) {
        return value == null || value.isBlank() ? "1.0.0" : value.trim();
    }

    private static String[] compactTransition(String oldBase, String oldDlc, String newBase, String newDlc) {
        String sourceBase = normalizeVersion(oldBase);
        String sourceDlc = normalizeVersion(oldDlc);
        String targetBase = normalizeVersion(newBase == null || newBase.isBlank() ? sourceBase : newBase);
        String targetDlc = normalizeVersion(newDlc == null || newDlc.isBlank() ? sourceDlc : newDlc);
        boolean baseChanged = !sourceBase.equals(targetBase);
        boolean dlcChanged = !sourceDlc.equals(targetDlc);
        if (baseChanged && dlcChanged) {
            return new String[] {sourceBase + "+" + sourceDlc, targetBase + "+" + targetDlc};
        }
        if (baseChanged) {
            return new String[] {sourceBase, targetBase};
        }
        if (dlcChanged) {
            return new String[] {sourceDlc, targetDlc};
        }
        return new String[] {sourceBase + "+" + sourceDlc, targetBase + "+" + targetDlc};
    }
}
