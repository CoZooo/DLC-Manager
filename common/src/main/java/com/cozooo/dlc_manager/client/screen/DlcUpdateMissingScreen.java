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
import com.cozooo.dlc_manager.core.DlcPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public class DlcUpdateMissingScreen extends DlcScreenCompat.MouseScroll {
    private final Screen parent;
    private final UpdateCheckResult updateResult;
    private final List<String> missingPaths;
    private Button forceButton;
    private Button cancelButton;
    private int scrollIndex;
    private boolean applying;

    public DlcUpdateMissingScreen(Screen parent, UpdateCheckResult updateResult, List<String> missingPaths) {
        super(Component.translatable("dlc_manager.update.missing_title"));
        this.parent = parent;
        this.updateResult = updateResult;
        this.missingPaths = missingPaths == null ? List.of() : missingPaths;
    }

    @Override
    protected void init() {
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int y = 20;
        int titleX = (this.width - this.font.width(getTitle())) / 2;
        guiGraphics.drawString(this.font, getTitle(), titleX, y, 0xFFFFFF, false);
        y += 16;
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.missing_desc"), centerX, y, 0xE6E6E6);
        y += 12;
        drawMissingList(guiGraphics, y);
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawMissingList(GuiGraphics guiGraphics, int startY) {
        int listTop = startY;
        int listBottom = this.height - 70;
        int lineHeight = 12;
        int totalLines = missingPaths.size();
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        int y = listTop;
        for (int i = scrollIndex; i < Math.min(totalLines, scrollIndex + maxVisible); i++) {
            String path = missingPaths.get(i);
            guiGraphics.drawString(this.font, "- " + path, 24, y, 0xE6E6E6, false);
            y += lineHeight;
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);
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

    @Override
    protected boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        return handleMouseScrolled(deltaY);
    }

    private boolean handleMouseScrolled(double deltaY) {
        int listTop = 20 + 16 + 12;
        int listBottom = this.height - 70;
        int lineHeight = 12;
        int totalLines = missingPaths.size();
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = deltaY > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return false;
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
            Component component = DlcUpdateService.toFailureComponent(result);
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
}
