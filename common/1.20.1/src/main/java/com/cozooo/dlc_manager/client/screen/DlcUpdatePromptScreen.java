package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public class DlcUpdatePromptScreen extends Screen {
    private final Screen parent;
    private final UpdateCheckResult result;
    private final boolean forceUpdate;
    private Button applyButton;
    private Button cancelButton;
    private int scrollIndex;
    private boolean applying;
    private String statusText;

    public DlcUpdatePromptScreen(Screen parent, UpdateCheckResult result) {
        super(Component.translatable("dlc_manager.update.title"));
        this.parent = parent;
        this.result = result;
        this.forceUpdate = hasHiddenUpdates(result);
    }

    @Override
    protected void init() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int y = this.height - 40;
        if (forceUpdate) {
            applyButton = Button.builder(Component.translatable("dlc_manager.update.apply"), button -> confirmApply())
                .bounds(this.width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight)
                .build();
            addRenderableWidget(applyButton);
            cancelButton = null;
        } else {
            applyButton = Button.builder(Component.translatable("dlc_manager.update.apply"), button -> confirmApply())
                .bounds(this.width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
                .build();
            cancelButton = Button.builder(Component.translatable("dlc_manager.update.cancel"), button -> Minecraft.getInstance().setScreen(parent))
                .bounds(this.width / 2 + 6, y, buttonWidth, buttonHeight)
                .build();
            addRenderableWidget(applyButton);
            addRenderableWidget(cancelButton);
        }
    }

    private void confirmApply() {
        Component title = Component.translatable("dlc_manager.update.title");
        Component message = Component.translatable("dlc_manager.update.confirm_apply");
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                startApply(false);
            } else {
                Minecraft.getInstance().setScreen(this);
            }
        }, title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        if (applyButton != null) {
            applyButton.active = !applying;
        }
        if (cancelButton != null) {
            cancelButton.active = !applying;
        }
        int centerX = this.width / 2;
        int y = 20;
        int titleX = (this.width - this.font.width(getTitle())) / 2;
        guiGraphics.drawString(this.font, getTitle(), titleX, y, 0xFFFFFF, false);
        y += 16;
        String currentVersion = result.getCurrent() == null ? "" : result.getCurrent().getDlc();
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.current", currentVersion), centerX, y, 0xC8C8C8);
        y += 14;
        if (applying) {
            String text = statusText == null ? "" : statusText;
            guiGraphics.drawCenteredString(this.font, text, centerX, y, 0xE6E6E6);
            y += 12;
        } else {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.desc"), centerX, y, 0xE6E6E6);
            y += 12;
        }
        drawUpdateList(guiGraphics, y);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawUpdateList(GuiGraphics guiGraphics, int startY) {
        List<DlcUpdatePackage> updates = result.getUpdates();
        int listTop = startY;
        int listBottom = this.height - 70;
        int lineHeight = 12;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = updates.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        int y = listTop;
        for (int i = scrollIndex; i < Math.min(totalLines, scrollIndex + maxVisible); i++) {
            DlcUpdatePackage pkg = updates.get(i);
            String line = formatUpdateLine(pkg);
            guiGraphics.drawString(this.font, "- " + line, 24, y, 0xE6E6E6, false);
            y += lineHeight;
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);
    }

    private String formatUpdateLine(DlcUpdatePackage pkg) {
        if (pkg == null || pkg.getManifest() == null || pkg.getManifest().getVersion() == null) {
            return "";
        }
        String oldV = pkg.getManifest().getVersion().getDlcOld();
        String newV = pkg.getManifest().getVersion().getDlcNew();
        if (oldV == null) {
            oldV = "";
        }
        if (newV == null) {
            newV = "";
        }
        return oldV + " -> " + newV;
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listTop = 20 + 16 + 14 + 12;
        int listBottom = this.height - 70;
        int lineHeight = 12;
        int totalLines = result.getUpdates().size();
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = delta > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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

    private void startApply(boolean forceMissing) {
        if (applying) {
            return;
        }
        applying = true;
        statusText = Component.translatable("dlc_manager.update.applying").getString();
        if (applyButton != null) {
            applyButton.active = false;
        }
        if (cancelButton != null) {
            cancelButton.active = false;
        }
        new Thread(() -> {
            UpdateApplyResult result = DlcUpdateService.applyUpdates(this.result.getUpdates(), forceMissing);
            Minecraft.getInstance().execute(() -> handleApplyResult(result));
        }, "dlc-update-apply").start();
    }

    private void handleApplyResult(UpdateApplyResult result) {
        if (result.isMissing()) {
            Minecraft.getInstance().setScreen(new DlcUpdateMissingScreen(parent, this.result, result.getMissingPaths()));
            return;
        }
        if (!result.isSuccess()) {
            Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(parent, false, Component.translatable("dlc_manager.update.failed")));
            return;
        }
        if (tryShowPostUpdateDownloads(result)) {
            return;
        }
        Minecraft.getInstance().setScreen(new DlcUpdateResultScreen(parent, true, Component.translatable("dlc_manager.update.success")));
    }

    private boolean tryShowPostUpdateDownloads(UpdateApplyResult result) {
        if (result == null || !result.isSuccess()) {
            return false;
        }
        if (result.hasRequiredAdd()) {
            List<DlcRequiredEntry> missing = DlcRequiredManager.collectMissingDownloadsForPrompt();
            if (!missing.isEmpty()) {
                Minecraft.getInstance().setScreen(new DlcRequiredDownloadScreen(parent, missing));
                return true;
            }
            return false;
        }
        if (result.hasRemoteAdd()) {
            List<DlcRemoteEntry> missing = DlcRemoteManager.collectMissing();
            if (!missing.isEmpty()) {
                Minecraft.getInstance().setScreen(new DlcRemoteDownloadScreen(parent, missing));
                return true;
            }
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return false;
    }
}
