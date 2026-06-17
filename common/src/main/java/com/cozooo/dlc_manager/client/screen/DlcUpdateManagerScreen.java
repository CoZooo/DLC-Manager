package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.core.DlcFilePicker;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.client.update.DlcUpdateRowPlanner;
import com.cozooo.dlc_manager.client.update.DlcUpdateRuntime;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator;
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateRollbackResult;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.platform.PlatformServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DlcUpdateManagerScreen extends DlcScreenCompat.MouseScroll {
    private final Screen parent;
    private Button importButton;
    private Button urlButton;
    private Button selfUpdateButton;
    private Button backButton;
    private int scrollIndex;
    private List<DlcUpdateMeta> history = List.of();
    private List<Button> rollbackButtons = new ArrayList<>();
    private List<String> rollbackTargets = List.of();
    private List<DlcUpdateRowPlanner.Action> rowActions = List.of();
    private List<DlcUpdateRowPlanner.Type> rowTypes = List.of();
    private List<String> rowDisplayOld = List.of();
    private List<String> rowDisplayNew = List.of();
    private String currentVersion;
    private String baseVersion;
    private String rollbackFrom;
    private boolean rolledBackMode;
    private UpdateCheckResult updateCheck;
    private boolean hasVisibleUpdates;
    private boolean applyingUpdates;

    public DlcUpdateManagerScreen(Screen parent) {
        super(Component.translatable("dlc_manager.update.manager_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshData();
    }

    public void refreshData() {
        DlcUpdateRuntime.ClientUpdateSnapshot snapshot = DlcUpdateRuntime.loadClientSnapshot(DlcPaths.dlcRoot());
        currentVersion = snapshot.currentVersion;
        baseVersion = snapshot.baseVersion;
        rollbackFrom = snapshot.rollbackFrom;
        rolledBackMode = snapshot.rolledBackMode;
        updateCheck = snapshot.updateCheck;
        hasVisibleUpdates = false;
        DlcSelfUpdateService.SelfUpdateHistory selfHistory = DlcSelfUpdateService.loadHistory();
        DlcUpdateRowPlanner.PlanResult plan = DlcUpdateRowPlanner.planClientRows(
            snapshot.history, selfHistory, updateCheck, rolledBackMode, baseVersion, currentVersion
        );
        List<DlcUpdateMeta> rows = new ArrayList<>(plan.rows.size());
        List<String> targets = new ArrayList<>(plan.rows.size());
        List<DlcUpdateRowPlanner.Action> actions = new ArrayList<>(plan.rows.size());
        List<DlcUpdateRowPlanner.Type> types = new ArrayList<>(plan.rows.size());
        List<String> displayOld = new ArrayList<>(plan.rows.size());
        List<String> displayNew = new ArrayList<>(plan.rows.size());
        for (DlcUpdateRowPlanner.UpdateRow row : plan.rows) {
            rows.add(row.meta);
            targets.add(row.rollbackTarget);
            actions.add(row.action);
            types.add(row.type);
            displayOld.add(row.displayOldVersion);
            displayNew.add(row.displayNewVersion);
        }
        hasVisibleUpdates = plan.hasVisibleUpdates;
        history = rows;
        rollbackTargets = targets;
        rowActions = actions;
        rowTypes = types;
        rowDisplayOld = displayOld;
        rowDisplayNew = displayNew;
        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();
        int buttonWidth = 120;
        int buttonHeight = 20;
        int y = this.height - 40;
        int spacing = 10;
        int maxWidth = this.width - 40;
        int idealWidth = (maxWidth - spacing * 3) / 4;
        if (idealWidth > 80 && idealWidth < buttonWidth) {
            buttonWidth = idealWidth;
        }
        int totalWidth = buttonWidth * 4 + spacing * 3;
        int startX = Math.max(20, (this.width - totalWidth) / 2);
        importButton = Button.builder(Component.translatable("dlc_manager.update.import"), button -> importUpdate())
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build();
        int urlX = startX + buttonWidth + spacing;
        urlButton = Button.builder(Component.translatable("dlc_manager.update.import_url"), button -> {
            Minecraft.getInstance().setScreen(new DlcUpdateUrlScreen(this));
        }).bounds(urlX, y, buttonWidth, buttonHeight).build();
        int selfX = urlX + buttonWidth + spacing;
        selfUpdateButton = Button.builder(Component.translatable("dlc_manager.pack_update.check_button"), button -> checkPackUpdate())
            .bounds(selfX, y, buttonWidth, buttonHeight).build();
        selfUpdateButton.active = hasPackUpdateRepoConfigured();
        int backX = selfX + buttonWidth + spacing;
        backButton = Button.builder(Component.translatable("gui.back"), button -> Minecraft.getInstance().setScreen(parent))
            .bounds(backX, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(importButton);
        addRenderableWidget(urlButton);
        addRenderableWidget(selfUpdateButton);
        addRenderableWidget(backButton);
        rollbackButtons.clear();
        for (int i = 0; i < history.size(); i++) {
            DlcUpdateRowPlanner.Action action = i < rowActions.size() ? rowActions.get(i) : DlcUpdateRowPlanner.Action.NONE;
            Component label = switch (action) {
                case MANAGER_ROLLBACK -> Component.translatable("dlc_manager.update.rollback");
                case REUPDATE -> Component.translatable("dlc_manager.update.reapply");
                case UPDATE -> Component.translatable("dlc_manager.update.apply");
                default -> Component.translatable("dlc_manager.update.rollback");
            };
            int buttonW = (action == DlcUpdateRowPlanner.Action.REUPDATE
                || action == DlcUpdateRowPlanner.Action.UPDATE) ? 80 : 50;
            Button rollback = Button.builder(label, btn -> {
                int idx = rollbackButtons.indexOf(btn);
                if (idx >= 0 && idx < rowActions.size()) {
                    DlcUpdateRowPlanner.Action chosen = rowActions.get(idx);
                    if (chosen == DlcUpdateRowPlanner.Action.MANAGER_ROLLBACK) {
                        String target = rollbackTargets.get(idx);
                        if (target != null && !target.isBlank()) {
                            confirmManagerRollback(target);
                        }
                    } else if (chosen == DlcUpdateRowPlanner.Action.ROLLBACK) {
                        String target = rollbackTargets.get(idx);
                        if (target != null && !target.isBlank()) {
                            confirmRollback(target);
                        }
                    } else if (chosen == DlcUpdateRowPlanner.Action.REUPDATE) {
                        DlcUpdateMeta meta = idx < history.size() ? history.get(idx) : null;
                        if (meta != null) {
                            confirmReupdate(meta.getBaseOld(), meta.getDlcOld(), meta.getBaseNew(), meta.getDlcNew());
                        }
                    } else if (chosen == DlcUpdateRowPlanner.Action.UPDATE) {
                        DlcUpdateMeta meta = idx < history.size() ? history.get(idx) : null;
                        if (meta != null) {
                            confirmApply(meta.getBaseOld(), meta.getDlcOld(), meta.getBaseNew(), meta.getDlcNew());
                        }
                    }
                }
            }).bounds(-10000, -10000, buttonW, 16).build();
            rollback.visible = false;
            addRenderableWidget(rollback);
            rollbackButtons.add(rollback);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
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
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawHistoryList(GuiGraphics guiGraphics, int startY, int mouseX, int mouseY) {
        int listTop = startY;
        int listBottom = this.height - 60;
        int lineHeight = 20;
        int totalLines = history.size();
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        int y = listTop;
        int visibleEnd = Math.min(totalLines, scrollIndex + maxVisible);
        for (int i = scrollIndex; i < visibleEnd; i++) {
            DlcUpdateMeta meta = history.get(i);
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
            String oldDisplay = i < rowDisplayOld.size() ? rowDisplayOld.get(i) : normalizeVersion(sourceBase) + "+" + normalizeVersion(sourceDlc);
            String newDisplay = i < rowDisplayNew.size() ? rowDisplayNew.get(i) : normalizeVersion(targetBase) + "+" + normalizeVersion(targetDlc);
            String label = formatUpdateRowLabel(sourceBase, sourceDlc, targetBase, targetDlc, oldDisplay, newDisplay);
            DlcUpdateRowPlanner.Type type = i < rowTypes.size() ? rowTypes.get(i) : DlcUpdateRowPlanner.Type.UPDATE;
            if (type == DlcUpdateRowPlanner.Type.MANAGER) {
                String dlcPrefix = Component.translatable("dlc_manager.update.dlc_prefix").getString();
                if (label.startsWith(dlcPrefix)) {
                    label = label.substring(dlcPrefix.length());
                }
                label = Component.translatable("dlc_manager.update.manager_prefix").getString() + label;
            }
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
            if (buttonIndex >= 0 && buttonIndex < rollbackButtons.size()) {
                Button rollback = rollbackButtons.get(buttonIndex);
                DlcUpdateRowPlanner.Action action = buttonIndex < rowActions.size()
                    ? rowActions.get(buttonIndex)
                    : DlcUpdateRowPlanner.Action.NONE;
                boolean enabled = action != DlcUpdateRowPlanner.Action.NONE;
                rollback.visible = enabled;
                rollback.active = enabled;
                if (enabled) {
                    rollback.setX(buttonX);
                    rollback.setY(buttonY);
                } else {
                    rollback.setX(-10000);
                    rollback.setY(-10000);
                }
            }
            y += lineHeight;
        }
        for (int i = 0; i < rollbackButtons.size(); i++) {
            if (i < scrollIndex || i >= visibleEnd) {
                Button rollback = rollbackButtons.get(i);
                rollback.visible = false;
                rollback.setX(-10000);
                rollback.setY(-10000);
            }
        }
        renderChangelogTooltip(guiGraphics, listTop, listBottom, lineHeight, totalLines, maxVisible, maxScroll, mouseX, mouseY, history);
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);
    }

    private String formatUpdateRowLabel(String sourceBase, String sourceDlc, String targetBase, String targetDlc,
                                        String oldDisplay, String newDisplay) {
        boolean baseChanged = !normalizeVersion(sourceBase).equals(normalizeVersion(targetBase));
        boolean dlcChanged = !normalizeVersion(sourceDlc).equals(normalizeVersion(targetDlc));
        if (baseChanged && !dlcChanged) {
            return Component.translatable("dlc_manager.update.base_prefix").getString()
                + normalizeVersion(sourceBase) + " -> " + normalizeVersion(targetBase);
        }
        if (!baseChanged && dlcChanged) {
            return Component.translatable("dlc_manager.update.dlc_prefix").getString()
                + normalizeVersion(sourceDlc) + " -> " + normalizeVersion(targetDlc);
        }
        return oldDisplay + " -> " + newDisplay;
    }

    private void renderChangelogTooltip(GuiGraphics guiGraphics, int listTop, int listBottom, int lineHeight,
                                        int totalLines, int maxVisible, int maxScroll,
                                        int mouseX, int mouseY, List<DlcUpdateMeta> rows) {
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
        lines.addAll(DlcMarkdownRenderer.wrapMarkdown(this.font, changelog, maxWidth));
        guiGraphics.renderTooltip(this.font, lines, mouseX, mouseY);
    }

    @Override
    protected boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        return handleMouseScrolled(deltaY);
    }

    private boolean handleMouseScrolled(double vertical) {
        int listTop = 20 + 16 + 14
            + (rolledBackMode ? 12 : 0)
            + (hasVisibleUpdates ? 12 : 0);
        int listBottom = this.height - 60;
        int lineHeight = 20;
        int totalLines = history.size();
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = vertical > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void startRollback(String targetVersion) {
        if (targetVersion == null || targetVersion.isBlank()) {
            return;
        }
        if (importButton != null) {
            importButton.active = false;
        }
        if (backButton != null) {
            backButton.active = false;
        }
        new Thread(() -> {
            UpdateRollbackResult result = DlcUpdateService.rollbackTo(targetVersion);
            Minecraft.getInstance().execute(() -> {
                if (!result.isSuccess()) {
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.rollback_failed")));
                } else {
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, true, Component.translatable("dlc_manager.update.rollback_success")));
                }
            });
        }, "dlc-update-rollback").start();
    }

    private void startManagerRollback(String targetVersion) {
        if (targetVersion == null || targetVersion.isBlank()) {
            return;
        }
        setApplyingUpdates(true);
        new Thread(() -> {
            boolean ok = DlcSelfUpdateService.rollbackToPrevious();
            Minecraft.getInstance().execute(() -> {
                setApplyingUpdates(false);
                if (!ok) {
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.rollback_failed")));
                } else {
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, true, Component.translatable("dlc_manager.update.rollback_success")));
                }
            });
        }, "dlc-selfupdate-rollback").start();
    }

    private void confirmRollback(String targetVersion) {
        Component title = DlcConfirmUtil.buildUpdateManagerTitle();
        Component message = DlcConfirmUtil.buildUpdateConfirmRollbackMessage(targetVersion);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                startRollback(targetVersion);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void confirmManagerRollback(String targetVersion) {
        Component title = DlcConfirmUtil.buildUpdateManagerTitle();
        Component message = DlcConfirmUtil.buildUpdateConfirmRollbackMessage(targetVersion);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                startManagerRollback(targetVersion);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void startReupdate(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        var chain = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true);
        startReupdate(baseOld, dlcOld, baseNew, dlcNew, chain, null, false);
    }

    private void startReupdate(String baseOld, String dlcOld, String baseNew, String dlcNew, List<DlcUpdatePackage> chain,
                               DlcPackUpdateGuard.CheckResult packCheck, boolean uninstallIncompatible) {
        if (chain == null || chain.isEmpty()) {
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.failed")));
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
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.literal(message)));
                });
            }
        }, "dlc-update-reapply").start();
    }

    private void startApply(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        var chain = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true);
        startApply(baseOld, dlcOld, baseNew, dlcNew, chain, null, false);
    }

    private void startApply(String baseOld, String dlcOld, String baseNew, String dlcNew, List<DlcUpdatePackage> chain,
                            DlcPackUpdateGuard.CheckResult packCheck, boolean uninstallIncompatible) {
        if (chain == null || chain.isEmpty()) {
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.failed")));
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
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.literal(message)));
                });
            }
        }, "dlc-update-apply").start();
    }

    private void confirmReupdate(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        String changelog = DlcUpdateService.buildChangelogText(
            DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true),
            CrossPlatformRuntime.languageCode());
        confirmReupdateNow(baseOld, dlcOld, baseNew, dlcNew, changelog);
    }

    private void confirmApply(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        Component title = DlcConfirmUtil.buildUpdateManagerTitle();
        Component message = DlcConfirmUtil.buildUpdateConfirmApplyMessage();
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                List<DlcUpdatePackage> chain = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true);
                DlcUpdatePackGuard.confirmBeforeUpdate(this, chain,
                    (packCheck, uninstallIncompatible) -> startApply(baseOld, dlcOld, baseNew, dlcNew, chain, packCheck, uninstallIncompatible));
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void confirmReupdateNow(String baseOld, String dlcOld, String baseNew, String dlcNew, String changelog) {
        Component title = DlcConfirmUtil.buildUpdateManagerTitle();
        Component message = DlcConfirmUtil.buildUpdateConfirmReapplyMessage(baseOld, dlcOld, baseNew, dlcNew, changelog);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                List<DlcUpdatePackage> chain = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true);
                DlcUpdatePackGuard.confirmBeforeUpdate(this, chain,
                    (packCheck, uninstallIncompatible) -> startReupdate(baseOld, dlcOld, baseNew, dlcNew, chain, packCheck, uninstallIncompatible));
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void handleReupdateResult(UpdateApplyResult result, UpdateCheckResult check) {
        setApplyingUpdates(false);
        if (result != null && result.isMissing()) {
            UpdateCheckResult missingCheck = check == null ? DlcUpdateService.checkForUpdates(true) : check;
            Minecraft.getInstance().setScreen(new DlcUpdateMissingScreen(this, missingCheck, result.getMissingPaths()));
            return;
        }
        if (result == null || !result.isSuccess()) {
            Component component = DlcUpdateService.toFailureComponent(result);
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, component));
            return;
        }
        if (tryShowPostUpdateDownloads(result)) {
            return;
        }
        Component message = result != null && result.isHelperScheduled()
            ? Component.translatable("dlc_manager.update.deferred_restart")
            : Component.translatable("dlc_manager.update.success");
        Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, true, message));
    }

    private boolean tryShowPostUpdateDownloads(UpdateApplyResult result) {
        if (result == null || !result.isSuccess()) {
            return false;
        }
        if (result.hasRequiredAdd()) {
            List<DlcRequiredEntry> missing = DlcRequiredManager.collectMissingDownloadsForPrompt();
            if (!missing.isEmpty()) {
                Minecraft.getInstance().setScreen(new DlcRequiredDownloadScreen(this, missing, result));
                return true;
            }
        }
        if (result.hasRemoteAdd()) {
            List<DlcRemoteEntry> missing = DlcRemoteManager.collectMissing();
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
        return new DlcUpdateResultScreen(this, true, message);
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

    private void setApplyingUpdates(boolean applying) {
        applyingUpdates = applying;
        if (importButton != null) {
            importButton.active = !applying;
        }
        if (urlButton != null) {
            urlButton.active = !applying;
        }
        if (backButton != null) {
            backButton.active = !applying;
        }
        for (Button button : rollbackButtons) {
            button.active = !applying;
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

    private void importUpdate() {
        Path picked = DlcFilePicker.pickUpdateZip("Import Update");
        if (picked == null) {
            return;
        }
        DlcUpdateService.ImportValidationResult validation = DlcUpdateService.validateImportPackage(picked);
        if (!validation.isValid()) {
            showToast(Component.translatable("dlc_manager.update.manager_title"),
                DlcUpdateService.toImportValidationComponent(validation));
            return;
        }
        if (!DlcUpdateService.importUpdatePackage(picked)) {
            showToast(Component.translatable("dlc_manager.update.manager_title"),
                Component.translatable("dlc_manager.update.dlc_root_missing"));
            return;
        }
        promptImportedUpdate();
    }

    private void promptImportedUpdate() {
        UpdateCheckResult check = DlcUpdateService.checkForUpdates(true);
        if (check != null && check.hasUpdates()) {
            Minecraft.getInstance().setScreen(new DlcUpdatePromptScreen(this, check));
            return;
        }
        showToast(Component.translatable("dlc_manager.update.manager_title"),
            Component.translatable("dlc_manager.update.no_updates"));
        refreshData();
    }

    private void checkPackUpdate() {
        String repoUrl = DlcPathConfig.getUpdateGithubRepoUrl();
        if (repoUrl == null || repoUrl.isBlank()) {
            return;
        }
        if (selfUpdateButton != null) {
            selfUpdateButton.active = false;
        }
        String base = (baseVersion == null || baseVersion.isBlank()) ? "1.0.0" : baseVersion;
        String dlc = (currentVersion == null || currentVersion.isBlank()) ? "1.0.0" : currentVersion;
        new Thread(() -> {
            DlcPackReleaseUpdateService.CheckResult result;
            try {
                result = DlcPackReleaseUpdateService.checkForUpdates(
                    DlcPathConfig.getModpackUrl(),
                    repoUrl,
                    base,
                    dlc,
                    DlcPackReleaseUpdateService.Side.CLIENT
                );
            } catch (Exception ex) {
                result = null;
            }
            DlcPackReleaseUpdateService.CheckResult finalResult = result;
            Minecraft.getInstance().execute(() -> {
                if (selfUpdateButton != null) {
                    selfUpdateButton.active = true;
                }
                if (finalResult != null && finalResult.hasUpdate()) {
                    Minecraft.getInstance().setScreen(new DlcModpackUpdateScreen(this, finalResult));
                    return;
                }
                if (finalResult != null && finalResult.error != null && !finalResult.error.isBlank()) {
                    showToast(Component.translatable("dlc_manager.update.manager_title"),
                        DlcPackReleaseUpdateService.toCheckFailureComponent(finalResult.error));
                    return;
                }
                showToast(Component.translatable("dlc_manager.update.manager_title"),
                    Component.translatable("dlc_manager.pack_update.no_updates"));
            });
        }, "dlc-pack-update-check-manual").start();
    }

    private boolean hasPackUpdateRepoConfigured() {
        String repoUrl = DlcPathConfig.getUpdateGithubRepoUrl();
        return repoUrl != null && !repoUrl.isBlank();
    }

    private void showToast(Component title, Component desc) {
        Minecraft minecraft = Minecraft.getInstance();
        DlcScreenCompat.addOrUpdatePeriodicNotification(minecraft.getToasts(), title, desc);
    }

    private void drawScrollBar(GuiGraphics guiGraphics, int top, int bottom, int totalLines, int maxVisible, int maxScroll) {
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return;
        }
        int barX = this.width - 12;
        int barWidth = 4;
        int barHeight = Math.max(12, (bottom - top) * maxVisible / totalLines);
        int trackHeight = bottom - top;
        int maxTrack = Math.max(1, trackHeight - barHeight);
        int barY = top + (int) ((scrollIndex / (float) maxScroll) * maxTrack);
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF2B2B2B);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF6B6B6B);
    }

}
