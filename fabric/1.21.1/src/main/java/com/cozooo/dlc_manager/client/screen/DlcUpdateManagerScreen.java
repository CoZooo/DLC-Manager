package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.update.DlcUpdateFilePicker;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateRollbackResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.platform.PlatformServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DlcUpdateManagerScreen extends Screen {
    private final Screen parent;
    private Button importButton;
    private Button urlButton;
    private Button backButton;
    private int scrollIndex;
    private List<DlcUpdateMeta> history = List.of();
    private List<Button> rollbackButtons = new ArrayList<>();
    private List<String> rollbackTargets = List.of();
    private List<RowAction> rowActions = List.of();
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
        List<DlcUpdateMeta> baseHistory = filterVisible(DlcUpdateService.listUpdateHistory());
        DlcUpdateVersion version = DlcUpdateVersion.load(DlcPaths.dlcRoot());
        currentVersion = version == null ? "" : version.getDlc();
        baseVersion = version == null ? "" : version.getBase();
        rollbackFrom = version == null ? null : version.getRollbackFrom();
        updateCheck = DlcUpdateService.checkForUpdates();
        rolledBackMode = rollbackFrom != null && !rollbackFrom.isBlank() && !rollbackFrom.equals(currentVersion);
        hasVisibleUpdates = false;
        if (rolledBackMode) {
            List<DlcUpdateMeta> rolledHistory = filterVisible(DlcUpdateService.listUpdateHistoryFrom(rollbackFrom));
            if (!rolledHistory.isEmpty()) {
                baseHistory = rolledHistory;
            }
        }
        List<DlcUpdateMeta> rows = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        List<RowAction> actions = new ArrayList<>();
        for (DlcUpdateMeta meta : baseHistory) {
            rows.add(meta);
            targets.add(meta == null ? null : meta.getDlcOld());
            if (rolledBackMode && meta != null
                && DlcUpdateService.compareVersions(meta.getDlcNew(), currentVersion) > 0) {
                actions.add(RowAction.REUPDATE);
            } else {
                actions.add(RowAction.ROLLBACK);
            }
        }
        if (!rolledBackMode && updateCheck != null && updateCheck.hasUpdates()) {
            for (var pkg : updateCheck.getUpdates()) {
                if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
                    continue;
                }
                if (pkg.getManifest().isHidden()) {
                    continue;
                }
                var info = pkg.getManifest().getVersion();
                DlcUpdateMeta meta = new DlcUpdateMeta();
                meta.setDlcOld(info.getDlcOld());
                meta.setDlcNew(info.getDlcNew());
                rows.add(meta);
                targets.add(null);
                actions.add(RowAction.UPDATE);
                hasVisibleUpdates = true;
            }
        }
        history = rows;
        rollbackTargets = targets;
        rowActions = actions;
        buildWidgets();
    }

    private List<DlcUpdateMeta> filterVisible(List<DlcUpdateMeta> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<DlcUpdateMeta> out = new ArrayList<>();
        for (DlcUpdateMeta meta : input) {
            if (meta != null && !meta.isHidden()) {
                out.add(meta);
            }
        }
        return out;
    }

    private void buildWidgets() {
        clearWidgets();
        int buttonWidth = 120;
        int buttonHeight = 20;
        int y = this.height - 40;
        importButton = Button.builder(Component.translatable("dlc_manager.update.import"), button -> importUpdate())
            .bounds(20, y, buttonWidth, buttonHeight)
            .build();
        int urlX = this.width / 2 - buttonWidth / 2;
        urlButton = Button.builder(Component.translatable("dlc_manager.update.import_url"), button -> {
            Minecraft.getInstance().setScreen(new DlcUpdateUrlScreen(this));
        }).bounds(urlX, y, buttonWidth, buttonHeight).build();
        int backX = this.width - 20 - buttonWidth;
        backButton = Button.builder(Component.translatable("gui.back"), button -> Minecraft.getInstance().setScreen(parent))
            .bounds(backX, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(importButton);
        addRenderableWidget(urlButton);
        addRenderableWidget(backButton);
        rollbackButtons.clear();
        for (int i = 0; i < history.size(); i++) {
            RowAction action = i < rowActions.size() ? rowActions.get(i) : RowAction.NONE;
            Component label = switch (action) {
                case REUPDATE -> Component.translatable("dlc_manager.update.reapply");
                case UPDATE -> Component.translatable("dlc_manager.update.apply");
                default -> Component.translatable("dlc_manager.update.rollback");
            };
            int buttonW = (action == RowAction.REUPDATE || action == RowAction.UPDATE) ? 80 : 50;
            Button rollback = Button.builder(label, btn -> {
                int idx = rollbackButtons.indexOf(btn);
                if (idx >= 0 && idx < rowActions.size()) {
                    RowAction chosen = rowActions.get(idx);
                    if (chosen == RowAction.ROLLBACK) {
                        String target = rollbackTargets.get(idx);
                        if (target != null && !target.isBlank()) {
                            confirmRollback(target);
                        }
                    } else if (chosen == RowAction.REUPDATE || chosen == RowAction.UPDATE) {
                        DlcUpdateMeta meta = idx < history.size() ? history.get(idx) : null;
                        if (meta != null) {
                            confirmReupdate(meta.getDlcOld(), meta.getDlcNew());
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
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int y = 20;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, y, 0xFFFFFF);
        y += 16;
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.base", baseVersion), centerX, y, 0xC8C8C8);
        y += 14;
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.current", currentVersion), centerX, y, 0xC8C8C8);
        y += 14;
        if (rolledBackMode) {
            guiGraphics.drawCenteredString(this.font,
                Component.translatable("dlc_manager.update.rollback_info", rollbackFrom, currentVersion),
                centerX, y, 0xE6E6E6);
            y += 12;
        }
        if (hasVisibleUpdates) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.desc"), centerX, y, 0xE6E6E6);
            y += 12;
        }
        drawHistoryList(guiGraphics, y, mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
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
            String label = meta.getDlcOld() + " -> " + meta.getDlcNew();
            if (i < rowActions.size() && rowActions.get(i) == RowAction.REUPDATE) {
                label = label + " " + Component.translatable("dlc_manager.update.rolled_back").getString();
            }
            guiGraphics.drawString(this.font, label, 24, y + 6, 0xE6E6E6, false);
            int buttonWidth = i < rowActions.size()
                && (rowActions.get(i) == RowAction.REUPDATE || rowActions.get(i) == RowAction.UPDATE)
                ? 80
                : 50;
            int buttonHeight = 16;
            int buttonX = this.width - 24 - buttonWidth;
            int buttonY = y + 2;
            int buttonIndex = i;
            if (buttonIndex >= 0 && buttonIndex < rollbackButtons.size()) {
                Button rollback = rollbackButtons.get(buttonIndex);
                RowAction action = buttonIndex < rowActions.size() ? rowActions.get(buttonIndex) : RowAction.NONE;
                boolean enabled = action != RowAction.NONE;
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
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int listTop = 20 + 16 + 14
            + (rolledBackMode ? 12 : 0)
            + (hasVisibleUpdates ? 12 : 0);
        int listBottom = this.height - 60;
        int lineHeight = 20;
        int totalLines = history.size();
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = deltaY > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
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

    private void confirmRollback(String targetVersion) {
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

    private void startReupdate(String dlcOld, String dlcNew) {
        if (dlcOld == null || dlcNew == null) {
            return;
        }
        var chain = DlcUpdateService.resolveUpdateChainTo(dlcOld, dlcNew, true);
        if (chain == null || chain.isEmpty()) {
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.failed")));
            return;
        }
        setApplyingUpdates(true);
        new Thread(() -> {
            UpdateApplyResult result = DlcUpdateService.applyUpdates(chain, false);
            Minecraft.getInstance().execute(() -> handleReupdateResult(result, null));
        }, "dlc-update-reapply").start();
    }

    private void confirmReupdate(String dlcOld, String dlcNew) {
        String changelog = DlcUpdateService.buildChangelogText(
            DlcUpdateService.resolveUpdateChainTo(dlcOld, dlcNew, true),
            PlatformServices.get().languageCode());
        if (changelog != null && !changelog.isBlank()) {
            Minecraft.getInstance().setScreen(new DlcUpdateChangelogScreen(this, changelog, () -> confirmReupdateNow(dlcOld, dlcNew)));
            return;
        }
        confirmReupdateNow(dlcOld, dlcNew);
    }

    private void confirmReupdateNow(String dlcOld, String dlcNew) {
        Component title = Component.translatable("dlc_manager.update.manager_title");
        Component message = Component.translatable("dlc_manager.update.confirm_reapply", dlcOld, dlcNew);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                startReupdate(dlcOld, dlcNew);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")));
    }

    private void handleReupdateResult(UpdateApplyResult result, UpdateCheckResult check) {
        setApplyingUpdates(false);
        if (result.isMissing()) {
            UpdateCheckResult missingCheck = check == null ? DlcUpdateService.checkForUpdates(true) : check;
            Minecraft.getInstance().setScreen(new DlcUpdateMissingScreen(this, missingCheck, result.getMissingPaths()));
            return;
        }
        if (!result.isSuccess()) {
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.failed")));
            return;
        }
        if (maybeShowPostUpdateDownloads(result)) {
            return;
        }
        Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, true, Component.translatable("dlc_manager.update.success")));
    }

    private boolean maybeShowPostUpdateDownloads(UpdateApplyResult result) {
        if (result == null || !result.isSuccess()) {
            return false;
        }
        if (result.hasRequiredAdd()) {
            List<DlcRequiredEntry> missing = DlcRequiredManager.collectMissingDownloadsForPrompt();
            if (!missing.isEmpty()) {
                Minecraft.getInstance().setScreen(new DlcRequiredDownloadScreen(this, missing));
                return true;
            }
            return false;
        }
        if (result.hasRemoteAdd()) {
            List<DlcRemoteEntry> missing = DlcRemoteManager.collectMissing();
            if (!missing.isEmpty()) {
                Minecraft.getInstance().setScreen(new DlcRemoteDownloadScreen(this, missing));
                return true;
            }
            refreshData();
            Minecraft.getInstance().setScreen(this);
            return true;
        }
        return false;
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
        Path picked = DlcUpdateFilePicker.pickUpdateZip("Import Update");
        if (picked == null) {
            return;
        }
        DlcUpdatePackage pkg = DlcUpdatePackage.fromZip(picked);
        if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null
            || !DlcUpdateService.isImportPackageValid(picked)) {
            showToast(Component.translatable("dlc_manager.update.manager_title"),
                Component.translatable("dlc_manager.update.import_invalid"));
            return;
        }
        if (!DlcUpdateService.importUpdatePackage(picked)) {
            showToast(Component.translatable("dlc_manager.update.manager_title"),
                Component.translatable("dlc_manager.update.dlc_root_missing"));
            return;
        }
        var info = pkg.getManifest().getVersion();
        if (info != null) {
            confirmReupdate(info.getDlcOld(), info.getDlcNew());
        } else {
            refreshData();
        }
    }

    private void showToast(Component title, Component desc) {
        Minecraft minecraft = Minecraft.getInstance();
        SystemToast.addOrUpdate(minecraft.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, desc);
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

    private enum RowAction {
        NONE,
        ROLLBACK,
        REUPDATE,
        UPDATE
    }

}
