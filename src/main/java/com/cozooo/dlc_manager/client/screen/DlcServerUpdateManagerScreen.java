package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerUpdateState;
import com.cozooo.dlc_manager.client.server.DlcServerConnection.UpdateResponse;
import com.cozooo.dlc_manager.client.core.DlcFilePicker;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateRowPlanner;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DlcServerUpdateManagerScreen extends DlcUpdatePageScreen {
    private static final AtomicBoolean PACK_RELEASE_CHECKED_ONCE = new AtomicBoolean(false);
    private final Screen parent;
    private final DlcServerEntry entry;
    private Button importButton;
    private Button pushUpdateButton;
    private Button urlButton;
    private Button packUpdateCheckButton;
    private Button backButton;
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
        super(parent, Component.translatable("dlc_manager.update.manager_title"));
        this.parent = parent;
        this.entry = entry;
    }

    private void initControls() {
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
                && state.updateGitRepo != null && !state.updateGitRepo.isBlank()) {
                packUpdateResult = DlcPackReleaseUpdateService.checkForUpdates(
                    state.modpackUrl,
                    state.updateGitRepo,
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
                packUpdateRepoUrl = finalState.updateGitRepo == null ? "" : finalState.updateGitRepo.trim();
                packModpackUrl = finalState.modpackUrl == null ? "" : finalState.modpackUrl.trim();
                rollbackFrom = finalState.rollbackFrom;
                rolledBackMode = DlcUpdateService.isRollbackMarkerActive(rollbackFrom, baseVersion, currentVersion);
                buildRows(finalState);
                buildWidgets();
                if (finalShouldCheckPackUpdate
                    && finalPackUpdateResult != null
                    && finalPackUpdateResult.hasUpdate()) {
                    Minecraft.getInstance().setScreen(new DlcModpackUpdateScreen(this, finalPackUpdateResult, entry));
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

    private String formatUpdateRowLabel(String sourceBase, String sourceDlc, String targetBase, String targetDlc,
                                        String oldText, String newText) {
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
        return oldText + " -> " + newText;
    }

    private void confirmRollback(String targetVersion) {
        if (targetVersion == null || targetVersion.isBlank()) {
            return;
        }
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

    private void confirmReupdate(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        Component title = DlcConfirmUtil.buildUpdateManagerTitle();
        Component message = DlcConfirmUtil.buildUpdateConfirmReapplyMessage(baseOld, dlcOld, baseNew, dlcNew);
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            if (accepted) {
                startApply(baseOld, dlcOld, baseNew, dlcNew);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void confirmApply(String baseOld, String dlcOld, String baseNew, String dlcNew) {
        Component title = DlcConfirmUtil.buildUpdateManagerTitle();
        Component message = DlcConfirmUtil.buildUpdateConfirmApplyMessage();
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            if (accepted) {
                startApply(baseOld, dlcOld, baseNew, dlcNew);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
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
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
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
        Path zip = DlcFilePicker.pickAnyFile(Component.translatable("dlc_manager.update.import").getString());
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
                    Minecraft.getInstance().setScreen(new DlcModpackUpdateScreen(this, finalResult, entry));
                    return;
                }
                if (finalResult != null && finalResult.error != null && !finalResult.error.isBlank()) {
                    showToast(Component.translatable("dlc_manager.update.manager_title"),
                        DlcMessageAdapter.toCheckFailureComponent(finalResult.error));
                    return;
                }
                showToast(Component.translatable("dlc_manager.update.manager_title"),
                    Component.translatable("dlc_manager.pack_update.no_updates"));
            });
        }, "dlc-server-pack-update-check-manual").start();
    }

    private void showToast(Component title, Component desc) {
        Minecraft minecraft = Minecraft.getInstance();
        DlcScreenCompat.addOrUpdatePeriodicNotification(minecraft.getToasts(), title, desc);
    }

    private void pushUpdates() {
        if (applying || loading) {
            return;
        }
        Path updateDir = DlcConfig.getDlcRoot().resolve("update");
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

    private static String normalizeVersion(String value) {
        return value == null || value.isBlank() ? "1.0.0" : value.trim();
    }

    private boolean controlsReady;
    private int pageRevision;
    @Override protected void buildPage() {
        if (!controlsReady) { controlsReady = true; initControls(); }
        pageRevision = java.util.Objects.hash(rows, actionButtons, loading, loadFailed);

        var list = updateSection(baseVersion, currentVersion);
        var updates = new java.util.ArrayList<UpdateRow>();
        for (int i = 0; i < rows.size(); i++) {
            var meta = rows.get(i); if (meta == null) continue;
            String label = formatUpdateRowLabel(meta.getBaseOld(), meta.getDlcOld(), meta.getBaseNew(), meta.getDlcNew(), displayOld.get(i), displayNew.get(i));
            var model = i < actionButtons.size() ? actionButtons.get(i) : null;
            if (model != null) { model.visible = rowActions.get(i) != DlcUpdateRowPlanner.Action.NONE; model.active = model.visible && !applying && !loading; }
            updates.add(new UpdateRow(label, rowActions.get(i) == DlcUpdateRowPlanner.Action.REUPDATE ? tr("dlc_manager.update.rolled_back") : null,
                    meta.resolveChangelog(DlcPlatformServices.languageCode()), model));
        }
        renderUpdates(list, updates, !loading && !loadFailed);
        renderUpdateActions(importButton, urlButton, pushUpdateButton, packUpdateCheckButton);
        if (loadFailed) action(content, tr("dlc_manager.server.files_refresh"), this::refreshData);
        status(loading ? tr("dlc_manager.server.update_checking") : loadFailed ? tr("dlc_manager.server.update_failed") : rows.isEmpty() ? tr("dlc_manager.server.update_empty") : "");

    }
    @Override protected void updatePage() {
        if (loading || applying) { for (var model : java.util.List.of(importButton, urlButton, pushUpdateButton, backButton, packUpdateCheckButton)) model.active = false; }
        if (pageRevision != java.util.Objects.hash(rows, actionButtons, loading, loadFailed)) refreshPage();
    }

    @Override public boolean shouldCloseOnEsc() { return !applying && !loading; }
}
