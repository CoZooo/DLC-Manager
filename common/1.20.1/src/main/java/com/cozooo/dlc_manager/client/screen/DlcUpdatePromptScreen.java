package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage;
import com.cozooo.dlc_manager.client.text.DlcTextFormatter;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;
import java.util.ArrayList;

public class DlcUpdatePromptScreen extends Screen {
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

    public DlcUpdatePromptScreen(Screen parent, UpdateCheckResult result) {
        this(parent, result, false);
    }

    public DlcUpdatePromptScreen(Screen parent, UpdateCheckResult result, boolean allowSkipThisUpdate) {
        super(Component.translatable("dlc_manager.update.title"));
        this.parent = parent;
        this.result = result;
        this.forceUpdate = hasHiddenUpdates(result);
        this.allowSkipThisUpdate = allowSkipThisUpdate;
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
        if (skipThisUpdateButton != null) {
            skipThisUpdateButton.active = !applying;
        }
        int centerX = this.width / 2;
        int y = 20;
        int titleX = (this.width - this.font.width(getTitle())) / 2;
        guiGraphics.drawString(this.font, getTitle(), titleX, y, 0xFFFFFF, false);
        y += 16;
        String currentVersion = result.getCurrent() == null
            ? formatVersionPair("", "")
            : formatVersionPair(result.getCurrent().getBase(), result.getCurrent().getDlc());
        guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.update.current_full", currentVersion), centerX, y, 0xC8C8C8);
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
        List<FormattedCharSequence> lines = buildUpdateLines();
        int listTop = startY;
        int listBottom = this.height - 70;
        int lineHeight = 12;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = lines.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        int y = listTop;
        for (int i = scrollIndex; i < Math.min(totalLines, scrollIndex + maxVisible); i++) {
            guiGraphics.drawString(this.font, lines.get(i), 24, y, 0xE6E6E6, false);
            y += lineHeight;
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);
    }

    private List<FormattedCharSequence> buildUpdateLines() {
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<DlcUpdatePackage> updates = result == null ? List.of() : result.getUpdates();
        if (updates == null || updates.isEmpty()) {
            return lines;
        }
        String lang = CrossPlatformRuntime.languageCode();
        int maxWidth = Math.max(120, this.width - 56);
        for (DlcUpdatePackage pkg : updates) {
            String updateLine = "- " + formatUpdateLine(pkg);
            lines.addAll(this.font.split(Component.literal(updateLine), maxWidth));
            String changelog = "";
            if (pkg != null && pkg.getManifest() != null) {
                changelog = pkg.getManifest().resolveChangelog(lang);
            }
            if (changelog != null && !changelog.isBlank()) {
                lines.addAll(this.font.split(Component.translatable("dlc_manager.update.changelog_tooltip_title"), maxWidth));
                lines.addAll(DlcTextFormatter.splitMultiline(this.font, changelog, maxWidth));
            }
            lines.add(FormattedCharSequence.EMPTY);
        }
        return lines;
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
        int totalLines = buildUpdateLines().size();
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
        if (skipThisUpdateButton != null) {
            skipThisUpdateButton.active = false;
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
            String message = result == null ? "" : result.getMessage();
            Component component = (message == null || message.isBlank())
                ? Component.translatable("dlc_manager.update.failed")
                : Component.literal(message);
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
