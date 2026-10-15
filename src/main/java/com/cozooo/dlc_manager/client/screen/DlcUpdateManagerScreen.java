package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.cozooo.dlc_manager.platform.DlcPlatformServices;
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
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DlcUpdateManagerScreen extends DlcUpdatePageScreen {
    private final Screen parent;
    private Button importButton;
    private Button urlButton;
    private Button selfUpdateButton;
    private Button backButton;
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
    private String updateStatus = "";
    private boolean applyingUpdates;

    public DlcUpdateManagerScreen(Screen parent) {
        super(DlcUiPage.LOCAL_UPDATES, parent, DlcTextCompat.translatable("dlc_manager.update.manager_title"));
        this.parent = parent;
    }

    private void initControls() {
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
        importButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.update.import"), button -> importUpdate())
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build();
        int urlX = startX + buttonWidth + spacing;
        urlButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.update.import_url"), button -> {
            Minecraft.getInstance().setScreen(new DlcUpdateUrlScreen(this));
        }).bounds(urlX, y, buttonWidth, buttonHeight).build();
        int selfX = urlX + buttonWidth + spacing;
        selfUpdateButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.pack_update.check_button"), button -> checkPackUpdate())
            .bounds(selfX, y, buttonWidth, buttonHeight).build();
        selfUpdateButton.active = hasPackUpdateRepoConfigured();
        int backX = selfX + buttonWidth + spacing;
        backButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("gui.back"), button -> Minecraft.getInstance().setScreen(parent))
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
                case MANAGER_ROLLBACK -> DlcTextCompat.translatable("dlc_manager.update.rollback");
                case REUPDATE -> DlcTextCompat.translatable("dlc_manager.update.reapply");
                case UPDATE -> DlcTextCompat.translatable("dlc_manager.update.apply");
                default -> DlcTextCompat.translatable("dlc_manager.update.rollback");
            };
            int buttonW = (action == DlcUpdateRowPlanner.Action.REUPDATE
                || action == DlcUpdateRowPlanner.Action.UPDATE) ? 80 : 50;
            Button rollback = DlcScreenCompat.buttonBuilder(label, btn -> {
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

    private String formatUpdateRowLabel(String sourceBase, String sourceDlc, String targetBase, String targetDlc,
                                        String oldDisplay, String newDisplay) {
        boolean baseChanged = !normalizeVersion(sourceBase).equals(normalizeVersion(targetBase));
        boolean dlcChanged = !normalizeVersion(sourceDlc).equals(normalizeVersion(targetDlc));
        if (baseChanged && !dlcChanged) {
            return DlcTextCompat.translatable("dlc_manager.update.base_prefix").getString()
                + normalizeVersion(sourceBase) + " -> " + normalizeVersion(targetBase);
        }
        if (!baseChanged && dlcChanged) {
            return DlcTextCompat.translatable("dlc_manager.update.dlc_prefix").getString()
                + normalizeVersion(sourceDlc) + " -> " + normalizeVersion(targetDlc);
        }
        return oldDisplay + " -> " + newDisplay;
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
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, DlcTextCompat.translatable("dlc_manager.update.rollback_failed")));
                } else {
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, true, DlcTextCompat.translatable("dlc_manager.update.rollback_success")));
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
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, DlcTextCompat.translatable("dlc_manager.update.rollback_failed")));
                } else {
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, true, DlcTextCompat.translatable("dlc_manager.update.rollback_success")));
                }
            });
        }, "dlc-selfupdate-rollback").start();
    }

    private void confirmRollback(String targetVersion) {
        Component title = DlcConfirmUtil.buildUpdateManagerTitle();
        Component message = DlcConfirmUtil.buildUpdateConfirmRollbackMessage(targetVersion);
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
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
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
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
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, DlcTextCompat.translatable("dlc_manager.update.failed")));
            return;
        }
        setApplyingUpdates(true);
        new Thread(() -> {
            DlcPackUpdateCoordinator.UpdateSession packSession = null;
            try {
                DlcPackUpdateCoordinator coordinator = new DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot());
                packSession = coordinator.prepareForUpdate(packCheck, uninstallIncompatible);
                UpdateApplyResult result = DlcUpdateService.applyUpdates(chain, false, (status, value) ->
                    Minecraft.getInstance().execute(() -> updateStatus = DlcMessageAdapter.toProgressText(status)));
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
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, DlcTextCompat.literal(message)));
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
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, DlcTextCompat.translatable("dlc_manager.update.failed")));
            return;
        }
        setApplyingUpdates(true);
        new Thread(() -> {
            DlcPackUpdateCoordinator.UpdateSession packSession = null;
            try {
                DlcPackUpdateCoordinator coordinator = new DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot());
                packSession = coordinator.prepareForUpdate(packCheck, uninstallIncompatible);
                UpdateApplyResult result = DlcUpdateService.applyUpdates(chain, false, (status, value) ->
                    Minecraft.getInstance().execute(() -> updateStatus = DlcMessageAdapter.toProgressText(status)));
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
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, DlcTextCompat.literal(message)));
                });
            }
        }, "dlc-update-apply").start();
    }

    private void confirmReupdate(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        String changelog = DlcUpdateService.buildChangelogText(
            DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true),
            DlcPlatformServices.languageCode());
        confirmReupdateNow(baseOld, dlcOld, baseNew, dlcNew, changelog);
    }

    private void confirmApply(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        Component title = DlcConfirmUtil.buildUpdateManagerTitle();
        Component message = DlcConfirmUtil.buildUpdateConfirmApplyMessage();
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
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
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
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
            Component component = DlcMessageAdapter.toFailureComponent(result);
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(this, false, component));
            return;
        }
        if (tryShowPostUpdateDownloads(result)) {
            return;
        }
        Component message = result != null && result.isHelperScheduled()
            ? DlcTextCompat.translatable("dlc_manager.update.deferred_restart")
            : DlcTextCompat.translatable("dlc_manager.update.success");
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
            ? DlcTextCompat.translatable("dlc_manager.update.deferred_restart")
            : DlcTextCompat.translatable("dlc_manager.update.success");
        return new DlcUpdateResultScreen(this, true, message);
    }

    private static String normalizeVersion(String value) {
        return value == null || value.isBlank() ? "1.0.0" : value.trim();
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
            showToast(DlcTextCompat.translatable("dlc_manager.update.manager_title"),
                DlcMessageAdapter.toImportValidationComponent(validation));
            return;
        }
        if (!DlcUpdateService.importUpdatePackage(picked)) {
            showToast(DlcTextCompat.translatable("dlc_manager.update.manager_title"),
                DlcTextCompat.translatable("dlc_manager.update.dlc_root_missing"));
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
        showToast(DlcTextCompat.translatable("dlc_manager.update.manager_title"),
            DlcTextCompat.translatable("dlc_manager.update.no_updates"));
        refreshData();
    }

    private void checkPackUpdate() {
        String repoUrl = DlcConfig.getUpdateGitRepoUrl();
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
                    DlcConfig.getModpackUrl(),
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
                    showToast(DlcTextCompat.translatable("dlc_manager.update.manager_title"),
                        DlcMessageAdapter.toCheckFailureComponent(finalResult.error));
                    return;
                }
                showToast(DlcTextCompat.translatable("dlc_manager.update.manager_title"),
                    DlcTextCompat.translatable("dlc_manager.pack_update.no_updates"));
            });
        }, "dlc-pack-update-check-manual").start();
    }

    private boolean hasPackUpdateRepoConfigured() {
        String repoUrl = DlcConfig.getUpdateGitRepoUrl();
        return repoUrl != null && !repoUrl.isBlank();
    }

    private void showToast(Component title, Component desc) {
        Minecraft minecraft = Minecraft.getInstance();
        DlcScreenCompat.addOrUpdatePeriodicNotification(DlcScreenCompat.toastManager(minecraft), title, desc);
    }

    private boolean controlsReady;
    private int pageRevision;
    @Override protected void buildPage() {
        if (!controlsReady) { controlsReady = true; initControls(); }
        pageRevision = java.util.Objects.hash(history, rollbackButtons, applyingUpdates);

        live(content, () -> applyingUpdates ? updateStatus : "", "page-description");
        var list = updateSection(baseVersion, currentVersion);
        if (rolledBackMode) text(list, tr("dlc_manager.update.rollback_info", rollbackFrom, normalizeVersion(baseVersion) + "+" + normalizeVersion(currentVersion)), "page-warning");
        if (hasVisibleUpdates) text(list, tr("dlc_manager.update.desc"), "page-description");
        var updates = new java.util.ArrayList<UpdateRow>();
        for (int i = 0; i < history.size(); i++) {
            var meta = history.get(i); if (meta == null) continue;
            String label = formatUpdateRowLabel(meta.getBaseOld(), meta.getDlcOld(), meta.getBaseNew(), meta.getDlcNew(), rowDisplayOld.get(i), rowDisplayNew.get(i));
            if (rowTypes.get(i) == DlcUpdateRowPlanner.Type.MANAGER) {
                String prefix = tr("dlc_manager.update.dlc_prefix");
                label = tr("dlc_manager.update.manager_prefix") + (label.startsWith(prefix) ? label.substring(prefix.length()) : label);
            }
            var model = i < rollbackButtons.size() ? rollbackButtons.get(i) : null;
            if (model != null) { model.visible = rowActions.get(i) != DlcUpdateRowPlanner.Action.NONE; model.active = model.visible && !applyingUpdates; }
            updates.add(new UpdateRow(label, rowActions.get(i) == DlcUpdateRowPlanner.Action.REUPDATE ? tr("dlc_manager.update.rolled_back") : null,
                    meta.resolveChangelog(DlcPlatformServices.languageCode()), model));
        }
        renderUpdates(list, updates, true);
        renderUpdateActions(importButton, urlButton, selfUpdateButton);

    }
    @Override protected void updatePage() {

        if (pageRevision != java.util.Objects.hash(history, rollbackButtons, applyingUpdates)) refreshPage();
    }

}
