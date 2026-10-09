package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator;
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public class DlcUpdatePromptScreen extends DlcPageScreen {
    private final Screen parent;
    private final UpdateCheckResult result;
    private final boolean forceUpdate;
    private final boolean allowSkipThisUpdate;
    private Button applyButton;
    private Button skipThisUpdateButton;
    private Button cancelButton;
    private int scrollIndex;
    private boolean applying;
    private String statusText;
    private float progress = -1f;

    public DlcUpdatePromptScreen(Screen parent, UpdateCheckResult result) {
        this(parent, result, false);
    }

    public DlcUpdatePromptScreen(Screen parent, UpdateCheckResult result, boolean allowSkipThisUpdate) {
        super(DlcUiPage.UPDATE_PROMPT, parent, Component.translatable("dlc_manager.update.title"));
        this.parent = parent;
        this.result = result;
        this.forceUpdate = hasHiddenUpdates(result);
        this.allowSkipThisUpdate = allowSkipThisUpdate;
    }

    private void initControls() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int y = this.height - 40;
        if (forceUpdate) {
            applyButton = Button.builder(Component.translatable("dlc_manager.update.apply"), button -> confirmApply())
                .bounds(this.width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight)
                .build();
            addRenderableWidget(applyButton);
            skipThisUpdateButton = null;
            cancelButton = null;
        } else if (allowSkipThisUpdate) {
            int spacing = 6;
            int skipButtonWidth = 108;
            int totalWidth = buttonWidth + skipButtonWidth + buttonWidth + spacing * 2;
            int startX = this.width / 2 - totalWidth / 2;
            applyButton = Button.builder(Component.translatable("dlc_manager.update.apply"), button -> confirmApply())
                .bounds(startX, y, buttonWidth, buttonHeight)
                .build();
            skipThisUpdateButton = Button.builder(Component.translatable("dlc_manager.self_update.skip_this_update"), button -> skipThisUpdate())
                .bounds(startX + buttonWidth + spacing, y, skipButtonWidth, buttonHeight)
                .build();
            cancelButton = Button.builder(Component.translatable("dlc_manager.update.cancel"), button -> Minecraft.getInstance().setScreen(parent))
                .bounds(startX + buttonWidth + spacing + skipButtonWidth + spacing, y, buttonWidth, buttonHeight)
                .build();
            addRenderableWidget(applyButton);
            addRenderableWidget(skipThisUpdateButton);
            addRenderableWidget(cancelButton);
        } else {
            applyButton = Button.builder(Component.translatable("dlc_manager.update.apply"), button -> confirmApply())
                .bounds(this.width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
                .build();
            cancelButton = Button.builder(Component.translatable("dlc_manager.update.cancel"), button -> Minecraft.getInstance().setScreen(parent))
                .bounds(this.width / 2 + 6, y, buttonWidth, buttonHeight)
                .build();
            skipThisUpdateButton = null;
            addRenderableWidget(applyButton);
            addRenderableWidget(cancelButton);
        }
    }

    private void skipThisUpdate() {
        if (result != null && result.hasUpdates()) {
            DlcUpdateService.skipPromptForUpdate(result);
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private void confirmApply() {
        Component title = Component.translatable("dlc_manager.update.title");
        Component message = DlcConfirmUtil.buildUpdateConfirmApplyMessage();
        Minecraft.getInstance().setScreen(new DlcConfirmScreen(accepted -> {
            if (accepted) {
                DlcUpdatePackGuard.confirmBeforeUpdate(this, this.result.getUpdates(),
                    (packCheck, uninstallIncompatible) -> startApply(false, packCheck, uninstallIncompatible));
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private String formatUpdateLine(DlcUpdatePackage pkg) {
        if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
            return "";
        }
        var version = pkg.getManifest().getVersion();
        String oldBase = normalizeVersion(version.getBase());
        String oldDlc = normalizeVersion(version.getDlcOld());
        String newBase = normalizeVersion(version.getBaseNew());
        String newDlc = normalizeVersion(version.getDlcNew());
        if (newBase == null || newBase.isBlank()) {
            newBase = oldBase;
        }
        if (newDlc == null || newDlc.isBlank()) {
            newDlc = oldDlc;
        }
        String[] compact = compactTransition(oldBase, oldDlc, newBase, newDlc);
        String from = compact[0];
        String to = compact[1];
        if (from.equals(to)) {
            return from;
        }
        return from + " -> " + to;
    }

    private String formatVersionPair(String base, String dlc) {
        return normalizeVersion(base) + "+" + normalizeVersion(dlc);
    }

    private String[] compactTransition(String oldBase, String oldDlc, String newBase, String newDlc) {
        String sourceBase = normalizeVersion(oldBase);
        String sourceDlc = normalizeVersion(oldDlc);
        String targetBase = normalizeVersion(newBase);
        String targetDlc = normalizeVersion(newDlc);
        boolean baseChanged = !sourceBase.equals(targetBase);
        boolean dlcChanged = !sourceDlc.equals(targetDlc);
        if (baseChanged && dlcChanged) {
            return new String[] {formatVersionPair(sourceBase, sourceDlc), formatVersionPair(targetBase, targetDlc)};
        }
        if (baseChanged) {
            return new String[] {sourceBase, targetBase};
        }
        if (dlcChanged) {
            return new String[] {sourceDlc, targetDlc};
        }
        return new String[] {formatVersionPair(sourceBase, sourceDlc), formatVersionPair(targetBase, targetDlc)};
    }

    private String normalizeVersion(String value) {
        return value == null || value.isBlank() ? "1.0.0" : value.trim();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !forceUpdate && !applying;
    }

    @Override
    public void onClose() {
        if (!applying) {
            super.onClose();
        }
    }

    private boolean hasHiddenUpdates(UpdateCheckResult result) {
        if (result == null || result.getUpdates() == null) {
            return false;
        }
        for (DlcUpdatePackage pkg : result.getUpdates()) {
            if (pkg != null && pkg.getManifest() != null && pkg.getManifest().isHidden()) {
                return true;
            }
        }
        return false;
    }

    private void startApply(boolean forceMissing, DlcPackUpdateGuard.CheckResult packCheck, boolean uninstallIncompatible) {
        if (applying) {
            return;
        }
        applying = true;
        statusText = Component.translatable("dlc_manager.update.applying").getString();
        progress = -1f;
        if (applyButton != null) {
            applyButton.active = false;
        }
        if (cancelButton != null) {
            cancelButton.active = false;
        }
        if (skipThisUpdateButton != null) {
            skipThisUpdateButton.active = false;
        }
        new Thread(() -> {
            DlcPackUpdateCoordinator.UpdateSession packSession = null;
            try {
                DlcPackUpdateCoordinator coordinator = new DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot());
                packSession = coordinator.prepareForUpdate(packCheck, uninstallIncompatible);
                UpdateApplyResult result = DlcUpdateService.applyUpdates(this.result.getUpdates(), forceMissing, (status, value) ->
                    Minecraft.getInstance().execute(() -> {
                        statusText = status;
                        progress = value;
                    }));
                if (result != null && result.isSuccess()) {
                    packSession.finishAfterSuccessfulUpdate();
                } else {
                    packSession.restoreAfterFailedUpdate();
                }
                UpdateApplyResult finalResult = result;
                Minecraft.getInstance().execute(() -> handleApplyResult(finalResult));
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
        }, "dlc-update-apply").start();
    }

    private void handleApplyResult(UpdateApplyResult result) {
        if (result != null && result.isMissing()) {
            Minecraft.getInstance().setScreen(new DlcUpdateMissingScreen(parent, this.result, result.getMissingPaths()));
            return;
        }
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

    private boolean controlsReady;
    private int pageRevision;
    @Override protected void buildPage() {
        if (!controlsReady) { controlsReady = true; initControls(); }
        pageRevision = 0;

        var list = section(null, tr("dlc_manager.update.desc"));
        if (result != null) for (var pkg : result.getUpdates()) { if (pkg == null) continue; var part = node(list, "section", "page-section"); text(part, formatUpdateLine(pkg), "section-title"); if (pkg.getManifest() != null) markdown(part, pkg.getManifest().resolveChangelog(DlcPlatformServices.languageCode())); }
        live(content, () -> statusText, "page-description"); progress(content, () -> applying ? progress : 0);
        if (cancelButton != null) mount(footer, cancelButton); if (skipThisUpdateButton != null) mount(footer, skipThisUpdateButton); mount(footer, applyButton).getElement().setAttribute("class", "button primary");

    }
    @Override protected void updatePage() {
        applyButton.active = !applying; if (cancelButton != null) cancelButton.active = !applying; if (skipThisUpdateButton != null) skipThisUpdateButton.active = !applying;
        if (pageRevision != 0) refreshPage();
    }

}
