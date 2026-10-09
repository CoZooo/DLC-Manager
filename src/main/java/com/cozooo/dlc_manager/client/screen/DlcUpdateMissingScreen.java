package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator;
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.core.DlcPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public class DlcUpdateMissingScreen extends DlcPageScreen {
    private final Screen parent;
    private final UpdateCheckResult updateResult;
    private final List<String> missingPaths;
    private Button forceButton;
    private Button cancelButton;
    private int scrollIndex;
    private boolean applying;

    public DlcUpdateMissingScreen(Screen parent, UpdateCheckResult updateResult, List<String> missingPaths) {
        super(DlcUiPage.UPDATE_MISSING, parent, Component.translatable("dlc_manager.update.missing_title"));
        this.parent = parent;
        this.updateResult = updateResult;
        this.missingPaths = missingPaths == null ? List.of() : missingPaths;
    }

    private void initControls() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int y = this.height - 40;
        forceButton = Button.builder(Component.translatable("dlc_manager.update.force"), button -> confirmApply())
            .bounds(this.width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build();
        cancelButton = Button.builder(Component.translatable("dlc_manager.update.cancel"), button -> Minecraft.getInstance().setScreen(parent))
            .bounds(this.width / 2 + 6, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(forceButton);
        addRenderableWidget(cancelButton);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !applying;
    }

    @Override
    public void onClose() {
        if (!applying) {
            super.onClose();
        }
    }

    private void confirmApply() {
        DlcUpdatePackGuard.confirmBeforeUpdate(this, updateResult.getUpdates(),
            (packCheck, uninstallIncompatible) -> startApply(packCheck, uninstallIncompatible));
    }

    private void startApply(DlcPackUpdateGuard.CheckResult packCheck, boolean uninstallIncompatible) {
        if (applying) {
            return;
        }
        applying = true;
        if (forceButton != null) {
            forceButton.active = false;
        }
        if (cancelButton != null) {
            cancelButton.active = false;
        }
        new Thread(() -> {
            DlcPackUpdateCoordinator.UpdateSession packSession = null;
            try {
                DlcPackUpdateCoordinator coordinator = new DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot());
                packSession = coordinator.prepareForUpdate(packCheck, uninstallIncompatible);
                UpdateApplyResult result = DlcUpdateService.applyUpdates(updateResult.getUpdates(), true);
                if (result != null && result.isSuccess()) {
                    packSession.finishAfterSuccessfulUpdate();
                } else {
                    packSession.restoreAfterFailedUpdate();
                }
                UpdateApplyResult finalResult = result;
                Minecraft.getInstance().execute(() -> handleResult(finalResult));
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
                    applying = false;
                    Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(parent, false, Component.literal(message)));
                });
            }
        }, "dlc-update-force").start();
    }

    private void handleResult(UpdateApplyResult result) {
        if (result == null || !result.isSuccess()) {
            Component component = DlcMessageAdapter.toFailureComponent(result);
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(parent, false, component));
            return;
        }
        if (tryShowPostUpdateDownloads(result)) {
            return;
        }
        Component message = result != null && result.isHelperScheduled()
            ? Component.translatable("dlc_manager.update.deferred_restart")
            : Component.translatable("dlc_manager.update.success");
        Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(parent, true, message));
    }

    private boolean tryShowPostUpdateDownloads(UpdateApplyResult result) {
        if (result == null || !result.isSuccess()) {
            return false;
        }
        if (result.hasRequiredAdd()) {
            List<DlcRequiredEntry> missing = DlcRequiredManager.collectMissingDownloadsForPrompt();
            if (!missing.isEmpty()) {
                Minecraft.getInstance().setScreen(new DlcRequiredDownloadScreen(parent, missing, result));
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
        return new DlcUpdateResultScreen(parent, true, message);
    }

    @Override protected void buildPage() {
        initControls();
        var list = section("", tr("dlc_manager.update.missing_desc"));
        for (String path : missingPaths) row(list, path, "");
        mount(footer, cancelButton);
        mount(footer, forceButton).getElement().setAttribute("class", "button primary");
    }

}
