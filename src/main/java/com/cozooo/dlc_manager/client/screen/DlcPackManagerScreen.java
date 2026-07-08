package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.core.DlcFilePicker;
import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.pack.DlcPackArchive;
import com.cozooo.dlc_manager.client.pack.DlcPackCFResolver;
import com.cozooo.dlc_manager.client.pack.DlcPackInstaller;
import com.cozooo.dlc_manager.client.pack.DlcPackInstallPlan;
import com.cozooo.dlc_manager.client.pack.DlcPackMeta;
import com.cozooo.dlc_manager.client.pack.DlcPackMetadata;
import com.cozooo.dlc_manager.client.pack.DlcPackOrder;
import com.cozooo.dlc_manager.client.pack.DlcPackPackage;
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteMarker;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DlcPackManagerScreen extends DlcScreenCompat.MouseScroll {
    private static final int ROW_HEIGHT = 80;
    private static final int ROW_PADDING_TOP = 6;
    private static final int ROW_PADDING_BOTTOM = 8;
    private static final int ICON_SIZE = 32;
    private static final int BUTTON_WIDTH = 58;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 4;
    private static final int VERSION_LINE_HEIGHT = 18;
    private static final int VERSION_VALUE_GAP = 6;
    private static final int VERSION_BUTTON_HORIZONTAL_PADDING = 6;
    private static final int VERSION_BUTTON_TEXT_COLOR = 0xAFC8FF;
    private static final int VERSION_DROPDOWN_ITEM_HEIGHT = 18;
    private static final int ORDER_BUTTON_WIDTH = 18;
    private static final int ORDER_BUTTON_SLOTS = 2;
    private static final int HOMEPAGE_PROMPT_GAP = 12;
    private static final float HOMEPAGE_PROMPT_SCALE = 0.85F;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLL_STEP = 24;

    private final Screen parent;
    private Button importButton;
    private Button urlButton;
    private Button applyButton;
    private Button backButton;
    private Button completeButton;
    private final Map<String, PackArchiveEntry> pendingInstalls = new LinkedHashMap<>();
    private final Set<String> pendingUninstalls = new LinkedHashSet<>();
    private List<PackArchiveGroup> archiveGroups = List.of();
    private List<String> entryArchiveOrder = List.of();
    private List<String> committedArchiveOrder = List.of();
    private List<String> currentArchiveOrder = List.of();
    private boolean orderSnapshotInitialized;
    private Map<String, DlcPackMeta> installedByIdentifier = Map.of();
    private final Map<String, String> selectedVersionByIdentifier = new LinkedHashMap<>();
    private int selectedIndex = -1;
    private String versionDropdownIdentifier;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private double scrollbarDragOffset;
    private String lastClickKey;
    private long lastClickAt;
    private Component status;
    private volatile boolean busy;
    private volatile boolean cancelRequested;
    private volatile boolean cancelApplyRequested;
    private volatile boolean paused;
    private volatile BusyOperation busyOperation = BusyOperation.NONE;
    private volatile float operationProgress = -1f;
    private volatile Component operationStatus;
    private volatile Component operationDetail;
    private boolean pausedAllowRuntimeChanges;
    private final Set<String> pausedReinstallIdentifiers = new LinkedHashSet<>();
    private volatile Set<String> activeReinstallIdentifiers = Set.of();
    private int iconTextureSerial;

    public DlcPackManagerScreen(Screen parent) {
        super(Component.translatable("dlc_manager.pack.manager_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!busy) {
            refreshEntries();
        }
        clearWidgets();
        int buttonWidth = 104;
        int buttonHeight = 20;
        int y = this.height - 28;
        if (busy) {
            int gap = 8;
            int startX = this.width / 2 - buttonWidth - gap / 2;
            boolean canResume = paused && busyOperation == BusyOperation.APPLY;
            boolean applying = busyOperation == BusyOperation.APPLY;
            Component pauseLabel = canResume
                ? Component.translatable("dlc_manager.pack.continue_install")
                : applying && cancelRequested && !cancelApplyRequested
                    ? Component.translatable("dlc_manager.pack.pausing")
                    : applying
                        ? Component.translatable("dlc_manager.pack.pause_install")
                        : cancelRequested
                            ? Component.translatable("dlc_manager.pack.canceling")
                            : Component.translatable("dlc_manager.pack.cancel");
            backButton = Button.builder(pauseLabel, btn -> {
                    if (canResume) {
                        resumePausedApply();
                    } else if (applying) {
                        requestPauseApplyOperation();
                    } else {
                        onBackOrCancel();
                    }
                })
                .bounds(startX, y, buttonWidth, buttonHeight)
                .build();
            backButton.active = canResume || (!cancelRequested && busyOperation != BusyOperation.DELETE);
            Button returnButton = Button.builder(applying ? Component.translatable("dlc_manager.pack.cancel_install") : Component.translatable("dlc_manager.pack.back"), btn -> {
                    if (applying) {
                        requestCancelApplyOperation();
                    } else {
                        returnToParent();
                    }
                })
                .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
                .build();
            returnButton.active = applying || canResume;
            addRenderableWidget(backButton);
            addRenderableWidget(returnButton);
            return;
        }
        addCompleteButton();
        int gap = 8;
        int total = buttonWidth * 4 + gap * 3;
        int startX = this.width / 2 - total / 2;
        importButton = Button.builder(Component.translatable("dlc_manager.pack.import"), btn -> importPackArchive())
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build();
        importButton.active = DlcConfig.isPackFuncOn() && !busy;
        urlButton = Button.builder(Component.translatable("dlc_manager.pack.import_url"), btn -> Minecraft.getInstance().setScreen(new DlcPackUrlScreen(this)))
            .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
            .build();
        urlButton.active = DlcConfig.isPackFuncOn() && !busy;
        applyButton = Button.builder(Component.translatable("dlc_manager.pack.apply"), btn -> applyPendingChanges())
            .bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, buttonHeight)
            .build();
        applyButton.active = DlcConfig.isPackFuncOn() && hasPendingChanges() && !busy;
        Component backLabel = busy
            ? Component.translatable("dlc_manager.pack.cancel")
            : hasPendingChanges() ? Component.translatable("dlc_manager.pack.cancel_changes") : Component.translatable("dlc_manager.pack.back");
        backButton = Button.builder(backLabel, btn -> onBackOrCancel())
            .bounds(startX + (buttonWidth + gap) * 3, y, buttonWidth, buttonHeight)
            .build();
        backButton.active = true;
        addRenderableWidget(importButton);
        addRenderableWidget(urlButton);
        addRenderableWidget(applyButton);
        addRenderableWidget(backButton);
    }

    private void addCompleteButton() {
        if (!DlcConfig.isPackFuncOn() || !DlcRemoteManager.hasMissingDlcPacks()) {
            completeButton = null;
            return;
        }
        Component label = Component.translatable("dlc_manager.pack.complete");
        int buttonWidth = Math.max(76, this.font.width(label) + 8);
        completeButton = Button.builder(label, btn -> openCompletePacks())
            .bounds(12, 8, buttonWidth, 16)
            .build();
        addRenderableWidget(completeButton);
    }

    private void openCompletePacks() {
        List<DlcRemoteEntry> missing = DlcRemoteManager.collectMissingDlcPacks();
        if (missing.isEmpty()) {
            status = Component.translatable("dlc_manager.pack.complete_none");
            rebuildPackWidgets();
            return;
        }
        Minecraft.getInstance().setScreen(new DlcRemoteDownloadScreen(
            this,
            missing,
            DlcRemoteMarker.PART_DLC_PACK,
            Component.translatable("dlc_manager.pack.complete_title")
        ));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        if (busy) {
            renderOperation(guiGraphics);
            DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
            return;
        }
        if (!DlcConfig.isPackFuncOn()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.pack.disabled"), this.width / 2, this.height / 2 - 8, 0xFFCC66);
        } else {
            renderPackList(guiGraphics, mouseX, mouseY);
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderOperation(GuiGraphics guiGraphics) {
        int centerX = this.width / 2;
        int y = this.height / 2 - 44;
        Component message = operationStatus != null ? operationStatus : status != null ? status : Component.translatable("dlc_manager.pack.applying");
        String progressText = operationProgress >= 0f ? Math.round(Math.max(0f, Math.min(1f, operationProgress)) * 100f) + "%" : "...";
        guiGraphics.drawCenteredString(this.font, formatOperationTitle(message, operationProgress, progressText), centerX, y, 0xE6E6E6);
        y += 24;
        drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, operationProgress);
        guiGraphics.drawCenteredString(this.font, progressText, centerX, y + 14, 0xC8C8C8);
        if (operationDetail != null) {
            guiGraphics.drawCenteredString(this.font, operationDetail, centerX, y + 28, 0x9A9A9A);
        }
    }

    private void renderPackList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = getListLeft();
        int right = getListRight();
        int top = getListTop();
        guiGraphics.drawString(this.font, Component.translatable("dlc_manager.pack.archive_count", archiveGroups.size()), left, 38, 0xE6E6E6, false);
        if (status != null) {
            guiGraphics.drawString(this.font, status, left + 150, 38, 0xFFCC66, false);
        }
        if (archiveGroups.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.pack.none_archived"), this.width / 2, top + 36, 0x9A9A9A);
            return;
        }
        clampScroll();
        int rowTop = top - scrollOffset;
        int index = 0;
        int contentRight = right - SCROLLBAR_WIDTH - 4;
        guiGraphics.enableScissor(left, top, contentRight, getListBottom());
        while (index < archiveGroups.size() && rowTop < getListBottom()) {
            PackArchiveGroup group = archiveGroups.get(index);
            int rowHeight = rowHeight(group, contentRight);
            if (rowTop + rowHeight > top) {
                renderEntry(guiGraphics, group, index, left, contentRight, rowTop, rowHeight, mouseX, mouseY);
            }
            rowTop += rowHeight;
            index++;
        }
        guiGraphics.disableScissor();
        drawScrollBar(guiGraphics);
        renderVersionDropdown(guiGraphics, mouseX, mouseY);
    }

    private void renderEntry(GuiGraphics guiGraphics, PackArchiveGroup group, int index, int left, int right, int y, int rowHeight, int mouseX, int mouseY) {
        PackArchiveEntry entry = group.displayEntry();
        boolean selected = index == selectedIndex;
        int background = selected ? 0x66335566 : 0x55202020;
        guiGraphics.fill(left, y, right, y + rowHeight - 4, background);
        drawOrderButtons(guiGraphics, index, left + 4, y, rowHeight, mouseX, mouseY);
        int iconX = left + ORDER_BUTTON_WIDTH + 12;
        int iconY = y + ROW_PADDING_TOP;
        if (entry.iconTexture != null) {
            guiGraphics.blit(entry.iconTexture, iconX, iconY, ICON_SIZE, ICON_SIZE, 0.0F, 0.0F, entry.iconWidth, entry.iconHeight, entry.iconWidth, entry.iconHeight);
        } else {
            guiGraphics.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0xFF3B3B3B);
            String initial = entry.displayNameText.isBlank() ? "P" : entry.displayNameText.substring(0, 1).toUpperCase(Locale.ROOT);
            guiGraphics.drawCenteredString(this.font, Component.literal(initial), iconX + ICON_SIZE / 2, iconY + 12, 0xFFFFFF);
        }

        int textX = iconX + ICON_SIZE + 8;
        int textRight = right - BUTTON_WIDTH - 18;
        Component homepagePrompt = homepagePrompt(entry);
        int homepageWidth = homepagePrompt == null ? 0 : Math.min(Math.round(this.font.width(homepagePrompt) * HOMEPAGE_PROMPT_SCALE), Math.max(1, (textRight - textX) / 2));
        int nameWidth = Math.max(1, textRight - textX - (homepagePrompt == null ? 0 : homepageWidth + HOMEPAGE_PROMPT_GAP));
        int textY = y + ROW_PADDING_TOP;
        textY = drawMultiline(guiGraphics, entry.displayName, textX, textY, nameWidth, 0xFFFFFF) + 4;
        if (homepagePrompt != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(HOMEPAGE_PROMPT_SCALE, HOMEPAGE_PROMPT_SCALE, 1.0F);
            int promptX = textRight - homepageWidth;
            int promptY = y + ROW_PADDING_TOP + 1;
            guiGraphics.drawString(this.font, homepagePrompt, Math.round(promptX / HOMEPAGE_PROMPT_SCALE), Math.round(promptY / HOMEPAGE_PROMPT_SCALE), 0x8FA8C8, false);
            guiGraphics.pose().popPose();
        }
        textY = drawAuthorLines(guiGraphics, entry, textX, textY, textRight - textX, 0xC8C8C8) + 4;
        drawVersionLine(guiGraphics, group, textX, textY, textRight - textX, mouseX, mouseY);
        drawMultiline(guiGraphics, entry.description, textX, textY + VERSION_LINE_HEIGHT, textRight - textX, 0xB0B0B0);

        drawActionButtons(guiGraphics, group, right - BUTTON_WIDTH - 6, y, rowHeight, mouseX, mouseY);
    }

    private void drawRowButton(GuiGraphics guiGraphics, int x, int y, Component label, int mouseX, int mouseY) {
        drawSizedButton(guiGraphics, x, y, BUTTON_WIDTH, label, mouseX, mouseY);
    }

    private void drawSizedButton(GuiGraphics guiGraphics, int x, int y, int buttonWidth, Component label, int mouseX, int mouseY) {
        drawSizedButton(guiGraphics, x, y, buttonWidth, label, mouseX, mouseY, 0xFFFFFF);
    }

    private void drawSizedButton(GuiGraphics guiGraphics, int x, int y, int buttonWidth, Component label, int mouseX, int mouseY, int textColor) {
        boolean hover = mouseX >= x && mouseX < x + buttonWidth && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
        guiGraphics.fill(x, y, x + buttonWidth, y + BUTTON_HEIGHT, hover ? 0xFF5A5A5A : 0xFF404040);
        guiGraphics.drawCenteredString(this.font, label, x + buttonWidth / 2, y + 4, textColor);
    }

    private void drawOrderButtons(GuiGraphics guiGraphics, int index, int x, int rowY, int rowHeight, int mouseX, int mouseY) {
        int startY = centeredButtonColumnY(rowY, rowHeight, ORDER_BUTTON_SLOTS);
        if (canMoveUp(index)) {
            drawSizedButton(guiGraphics, x, startY, ORDER_BUTTON_WIDTH, Component.literal("▲"), mouseX, mouseY);
        }
        if (canMoveDown(index)) {
            drawSizedButton(guiGraphics, x, startY + BUTTON_HEIGHT + BUTTON_GAP, ORDER_BUTTON_WIDTH, Component.literal("▼"), mouseX, mouseY);
        }
    }

    private void drawActionButtons(GuiGraphics guiGraphics, PackArchiveGroup group, int x, int rowY, int rowHeight, int mouseX, int mouseY) {
        List<RowAction> actions = actionButtons(group);
        int startY = centeredButtonColumnY(rowY, rowHeight, actions.size());
        for (int i = 0; i < actions.size(); i++) {
            RowAction action = actions.get(i);
            drawRowButton(guiGraphics, x, startY + i * (BUTTON_HEIGHT + BUTTON_GAP), actionLabel(group, action), mouseX, mouseY);
        }
    }

    private void drawVersionLine(GuiGraphics guiGraphics, PackArchiveGroup group, int x, int y, int width, int mouseX, int mouseY) {
        Component prefix = Component.translatable("dlc_manager.pack.version_prefix");
        int prefixWidth = this.font.width(prefix);
        int valueX = x + prefixWidth + VERSION_VALUE_GAP;
        guiGraphics.drawString(this.font, prefix, x, y + 4, 0xAFC8FF, false);
        if (group.versions.size() <= 1) {
            guiGraphics.drawString(this.font, Component.literal(group.displayEntry().metadata.version), valueX, y + 4, 0xAFC8FF, false);
            return;
        }
        drawSizedButton(guiGraphics, valueX - VERSION_BUTTON_HORIZONTAL_PADDING, y, versionButtonWidth(group), versionButtonLabel(group), mouseX, mouseY, VERSION_BUTTON_TEXT_COLOR);
    }

    private Component installButtonLabel(PackArchiveGroup group) {
        PackArchiveEntry entry = group.displayEntry();
        DlcPackMeta installed = installedByIdentifier.get(entry.metadata.identifier);
        int compare = installed == null ? 0 : DlcPackMetadata.compareVersionStrings(entry.metadata.version, installed.version);
        if (pendingInstalls.get(entry.metadata.identifier) == entry) {
            if (installed == null) {
                return Component.translatable("dlc_manager.pack.pending_install");
            }
            if (compare > 0) {
                return Component.translatable("dlc_manager.pack.pending_upgrade");
            }
            if (compare < 0) {
                return Component.translatable("dlc_manager.pack.pending_downgrade");
            }
            return Component.translatable("dlc_manager.pack.pending_install");
        }
        if (pendingUninstalls.contains(entry.metadata.identifier)) {
            return Component.translatable("dlc_manager.pack.pending_uninstall");
        }
        if (installed != null && compare == 0) {
            return Component.translatable("dlc_manager.pack.uninstall");
        }
        if (installed != null) {
            return compare > 0 ? Component.translatable("dlc_manager.pack.upgrade") : Component.translatable("dlc_manager.pack.downgrade");
        }
        return Component.translatable("dlc_manager.pack.install");
    }

    private int centeredButtonColumnY(int rowY, int rowHeight, int count) {
        if (count <= 0) {
            return rowY + (rowHeight - 4) / 2;
        }
        int totalHeight = count * BUTTON_HEIGHT + (count - 1) * BUTTON_GAP;
        return rowY + Math.max(0, (rowHeight - 4 - totalHeight) / 2);
    }

    private int drawMultiline(GuiGraphics guiGraphics, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = this.font.split(text, Math.max(1, width));
        for (int i = 0; i < lines.size(); i++) {
            guiGraphics.drawString(this.font, lines.get(i), x, y + i * 10, color, false);
        }
        return y + lines.size() * 10;
    }

    private int drawAuthorLines(GuiGraphics guiGraphics, PackArchiveEntry entry, int x, int y, int width, int color) {
        List<List<Component>> lines = authorLines(entry, width);
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            int cursor = x;
            for (Component part : lines.get(lineIndex)) {
                guiGraphics.drawString(this.font, part, cursor, y + lineIndex * 10, color, false);
                cursor += this.font.width(part);
            }
        }
        return y + lines.size() * 10;
    }

    @Override
    public boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (busy || archiveGroups.isEmpty() || mouseY < getListTop() || mouseY > getListBottom()) {
            return false;
        }
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return false;
        }
        versionDropdownIdentifier = null;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (deltaY > 0 ? -SCROLL_STEP : SCROLL_STEP)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!busy && button == 0) {
            if (handleVersionDropdownClick(mouseX, mouseY)) {
                return true;
            }
            if (isInsideScrollbar(mouseX, mouseY)) {
                versionDropdownIdentifier = null;
                draggingScrollbar = true;
                scrollbarDragOffset = scrollbarDragOffset(mouseY);
                updateScrollFromMouse(mouseY);
                return true;
            }
            int index = entryAt(mouseY);
            if (index >= 0 && index < archiveGroups.size()) {
                return handleEntryClick(archiveGroups.get(index), index, mouseX, mouseY);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        scrollbarDragOffset = 0.0;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleEntryClick(PackArchiveGroup group, int index, double mouseX, double mouseY) {
        PackArchiveEntry entry = group.displayEntry();
        selectedIndex = index;
        int left = getListLeft();
        int right = getListRight() - SCROLLBAR_WIDTH - 4;
        Integer rowTop = rowTop(index, right);
        if (rowTop == null) {
            return true;
        }
        int rowHeight = rowHeight(group, right);
        int arrowX = left + 4;
        RowAction orderAction = orderButtonAt(index, arrowX, rowTop, rowHeight, mouseX, mouseY);
        if (orderAction != null) {
            moveEntry(index, orderAction == RowAction.MOVE_UP ? -1 : 1);
            return true;
        }

        boolean versionAction = versionButtonAt(group, versionButtonX(), versionLineY(group, rowTop, right), mouseX, mouseY);
        if (versionAction) {
            if (canShowVersionDropdown(group, rowTop, right)) {
                versionDropdownIdentifier = group.identifier.equals(versionDropdownIdentifier) ? null : group.identifier;
            } else {
                versionDropdownIdentifier = null;
                Minecraft.getInstance().setScreen(new DlcPackVersionSelectScreen(this, group.identifier, group.displayEntry().displayName, group.versions.stream().map(item -> item.key).toList(), group.selectedVersionKey, installedByIdentifier.get(group.identifier) == null ? null : installedByIdentifier.get(group.identifier).version));
            }
            return true;
        }

        RowAction rowAction = actionButtonAt(group, right - BUTTON_WIDTH - 6, rowTop, rowHeight, mouseX, mouseY);
        if (rowAction != null) {
            switch (rowAction) {
                case INSTALL -> toggleInstall(group);
                case DELETE -> confirmDeleteArchive(group);
                case FEEDBACK -> confirmOpenLink(entry, "issue");
                case SUPPORT -> confirmOpenLink(entry, "sponsor");
                case MOVE_UP, MOVE_DOWN -> {
                }
            }
            return true;
        }
        long now = Util.getMillis();
        if (entry.key.equals(lastClickKey) && now - lastClickAt < 350L) {
            confirmOpenLink(entry, "homepage");
        }
        lastClickKey = entry.key;
        lastClickAt = now;
        rebuildPackWidgets();
        return true;
    }

    private void toggleInstall(PackArchiveGroup group) {
        PackArchiveEntry entry = group.displayEntry();
        String identifier = entry.metadata.identifier;
        if (pendingUninstalls.remove(identifier)) {
            pendingInstalls.remove(identifier);
        } else if (isEntryCurrentlyInstalled(entry)) {
            pendingUninstalls.add(identifier);
            pendingInstalls.remove(identifier);
        } else if (pendingInstalls.containsKey(identifier) && pendingInstalls.get(identifier) == entry) {
            pendingInstalls.remove(identifier);
        } else {
            DlcPackMeta installed = installedByIdentifier.get(identifier);
            if (installed != null && entry.metadata.version.equals(installed.version)) {
                pendingUninstalls.add(identifier);
                pendingInstalls.remove(identifier);
            } else {
                pendingInstalls.put(identifier, entry);
            }
        }
        rebuildPackWidgets();
    }

    private void confirmDeleteArchive(PackArchiveGroup group) {
        PackArchiveEntry entry = group.displayEntry();
        boolean uninstallFirst = isEntryCurrentlyInstalled(entry);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (accepted) {
                startDeleteArchive(group, uninstallFirst);
            }
        }, Component.translatable("dlc_manager.pack.delete_confirm_title"),
            Component.translatable(
                uninstallFirst ? "dlc_manager.pack.delete_confirm_uninstall" : "dlc_manager.pack.delete_confirm",
                entry.displayName
            ),
            DlcConfirmUtil.confirmYes(),
            DlcConfirmUtil.confirmNo()));
    }

    private void startDeleteArchive(PackArchiveGroup group, boolean uninstallFirst) {
        PackArchiveEntry entry = group.displayEntry();
        if (uninstallFirst) {
            setBusy(true, Component.translatable("dlc_manager.pack.operation_uninstalling"), BusyOperation.DELETE);
            new Thread(() -> {
                try {
                    DlcPackInstaller installer = new DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot());
                    installer.uninstall(entry.metadata.identifier, (message, progress) ->
                        updateOperationProgress(Component.translatable("dlc_manager.pack.operation_uninstalling"), progress)
                    );
                    deleteArchiveFile(group);
                    finishBusy(Component.translatable("dlc_manager.pack.delete_uninstalled_success", entry.displayName), true);
                } catch (Exception ex) {
                    finishBusy(Component.translatable("dlc_manager.pack.delete_failed", describeFailure(ex)));
                }
            }, "dlc-pack-delete").start();
            return;
        }
        try {
            deleteArchiveFile(group);
            status = Component.translatable("dlc_manager.pack.delete_success", entry.displayName);
            refreshEntries();
            rebuildPackWidgets();
        } catch (Exception ex) {
            status = Component.translatable("dlc_manager.pack.delete_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private void deleteArchiveFile(PackArchiveGroup group) throws IOException {
        PackArchiveEntry entry = group.displayEntry();
        Files.deleteIfExists(entry.archivePath);
        pendingInstalls.remove(entry.metadata.identifier, entry);
        pendingUninstalls.remove(entry.metadata.identifier);
        String replacementKey = null;
        for (PackArchiveEntry version : group.versions) {
            if (!version.key.equals(entry.key)) {
                replacementKey = version.key;
                break;
            }
        }
        currentArchiveOrder = replaceOrRemoveDeletedOrderKey(currentArchiveOrder, entry, replacementKey);
        committedArchiveOrder = replaceOrRemoveDeletedOrderKey(committedArchiveOrder, entry, replacementKey);
        entryArchiveOrder = replaceOrRemoveDeletedOrderKey(entryArchiveOrder, entry, replacementKey);
        saveLiveOrder(currentArchiveOrder);
    }

    private List<String> replaceOrRemoveDeletedOrderKey(List<String> order, PackArchiveEntry entry, String replacementKey) {
        List<String> out = new ArrayList<>(order.size());
        for (String key : order) {
            if (!orderKeyMatchesIdentifier(key, entry.metadata.identifier)) {
                out.add(key);
                continue;
            }
            if (replacementKey != null && !out.contains(replacementKey)) {
                out.add(key.equals(entry.key) ? replacementKey : key);
            }
        }
        return out;
    }

    private void confirmOpenLink(PackArchiveEntry entry, String key) {
        String url = entry.metadata.links.get(key);
        if (url == null || url.isBlank()) {
            return;
        }
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (accepted) {
                Util.getPlatform().openUri(url);
            }
        }, Component.translatable("dlc_manager.pack.open_link_title"),
            Component.translatable("dlc_manager.pack.open_link_prompt", url),
            DlcConfirmUtil.confirmYes(),
            DlcConfirmUtil.confirmNo()));
    }

    private void openLink(PackArchiveEntry entry, String key) {
        String url = entry.metadata.links.get(key);
        if (url == null || url.isBlank()) {
            return;
        }
        Util.getPlatform().openUri(url);
    }

    private List<RowAction> orderButtons(int index) {
        List<RowAction> out = new ArrayList<>(2);
        if (canMoveUp(index)) {
            out.add(RowAction.MOVE_UP);
        }
        if (canMoveDown(index)) {
            out.add(RowAction.MOVE_DOWN);
        }
        return out;
    }

    private List<RowAction> actionButtons(PackArchiveGroup group) {
        PackArchiveEntry entry = group.displayEntry();
        List<RowAction> out = new ArrayList<>(4);
        out.add(RowAction.INSTALL);
        out.add(RowAction.DELETE);
        String issue = entry.metadata.links.get("issue");
        if (issue != null && !issue.isBlank()) {
            out.add(RowAction.FEEDBACK);
        }
        String sponsor = entry.metadata.links.get("sponsor");
        if (sponsor != null && !sponsor.isBlank()) {
            out.add(RowAction.SUPPORT);
        }
        return out;
    }

    private Component actionLabel(PackArchiveGroup group, RowAction action) {
        return switch (action) {
            case INSTALL -> installButtonLabel(group);
            case DELETE -> Component.translatable("dlc_manager.pack.delete");
            case FEEDBACK -> Component.translatable("dlc_manager.pack.feedback");
            case SUPPORT -> Component.translatable("dlc_manager.pack.support_author");
            case MOVE_UP -> Component.literal("▲");
            case MOVE_DOWN -> Component.literal("▼");
        };
    }

    private RowAction orderButtonAt(int index, int x, int rowY, int rowHeight, double mouseX, double mouseY) {
        int startY = centeredButtonColumnY(rowY, rowHeight, ORDER_BUTTON_SLOTS);
        if (canMoveUp(index) && mouseX >= x && mouseX < x + ORDER_BUTTON_WIDTH && mouseY >= startY && mouseY < startY + BUTTON_HEIGHT) {
            return RowAction.MOVE_UP;
        }
        int downY = startY + BUTTON_HEIGHT + BUTTON_GAP;
        if (canMoveDown(index) && mouseX >= x && mouseX < x + ORDER_BUTTON_WIDTH && mouseY >= downY && mouseY < downY + BUTTON_HEIGHT) {
            return RowAction.MOVE_DOWN;
        }
        return null;
    }

    private RowAction actionButtonAt(PackArchiveGroup group, int x, int rowY, int rowHeight, double mouseX, double mouseY) {
        List<RowAction> buttons = actionButtons(group);
        int startY = centeredButtonColumnY(rowY, rowHeight, buttons.size());
        for (int i = 0; i < buttons.size(); i++) {
            int y = startY + i * (BUTTON_HEIGHT + BUTTON_GAP);
            if (mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT) {
                return buttons.get(i);
            }
        }
        return null;
    }

    private boolean versionButtonAt(PackArchiveGroup group, int x, int y, double mouseX, double mouseY) {
        if (group.versions.size() <= 1) {
            return false;
        }
        int width = versionButtonWidth(group);
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    private void renderVersionDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        PackArchiveGroup group = dropdownGroup();
        if (group == null) {
            return;
        }
        VersionDropdownBounds bounds = versionDropdownBounds(group);
        if (bounds == null) {
            return;
        }
        guiGraphics.fill(bounds.x - 1, bounds.y - 1, bounds.x + bounds.width + 1, bounds.y + bounds.height + 1, 0xFF202020);
        guiGraphics.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 0xFF303030);
        for (int i = 0; i < group.versions.size(); i++) {
            PackArchiveEntry entry = group.versions.get(i);
            int itemY = bounds.y + 1 + i * VERSION_DROPDOWN_ITEM_HEIGHT;
            boolean hover = mouseX >= bounds.x && mouseX < bounds.x + bounds.width && mouseY >= itemY && mouseY < itemY + VERSION_DROPDOWN_ITEM_HEIGHT;
            boolean selected = entry.key.equals(group.selectedVersionKey);
            if (hover || selected) {
                guiGraphics.fill(
                    bounds.x + 1,
                    itemY,
                    bounds.x + bounds.width - 1,
                    itemY + VERSION_DROPDOWN_ITEM_HEIGHT,
                    hover ? 0xFF5A5A5A : 0xFF454545
                );
            }
            guiGraphics.drawString(this.font, versionOptionLabel(entry), bounds.x + VERSION_BUTTON_HORIZONTAL_PADDING, itemY + 5, VERSION_BUTTON_TEXT_COLOR, false);
        }
    }

    private boolean handleVersionDropdownClick(double mouseX, double mouseY) {
        PackArchiveGroup group = dropdownGroup();
        if (group == null) {
            return false;
        }
        VersionDropdownBounds bounds = versionDropdownBounds(group);
        if (bounds != null && mouseX >= bounds.x && mouseX < bounds.x + bounds.width && mouseY >= bounds.y && mouseY < bounds.y + bounds.height) {
            int index = (int) ((mouseY - bounds.y - 1) / VERSION_DROPDOWN_ITEM_HEIGHT);
            if (index >= 0 && index < group.versions.size()) {
                PackArchiveEntry entry = group.versions.get(index);
                versionDropdownIdentifier = null;
                selectPackVersion(group.identifier, entry.key);
                return true;
            }
        }
        if (!activeVersionButtonAt(group, mouseX, mouseY)) {
            versionDropdownIdentifier = null;
        }
        return false;
    }

    private boolean activeVersionButtonAt(PackArchiveGroup group, double mouseX, double mouseY) {
        int index = -1;
        for (int i = 0; i < archiveGroups.size(); i++) {
            if (archiveGroups.get(i).identifier.equals(group.identifier)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return false;
        }
        int right = getListRight() - SCROLLBAR_WIDTH - 4;
        Integer rowTop = rowTop(index, right);
        if (rowTop == null) {
            return false;
        }
        return versionButtonAt(group, versionButtonX(), versionLineY(group, rowTop, right), mouseX, mouseY);
    }

    private PackArchiveGroup dropdownGroup() {
        if (versionDropdownIdentifier == null) {
            return null;
        }
        PackArchiveGroup group = findGroup(versionDropdownIdentifier);
        return group != null && group.versions.size() > 1 ? group : null;
    }

    private VersionDropdownBounds versionDropdownBounds(PackArchiveGroup group) {
        int index = -1;
        for (int i = 0; i < archiveGroups.size(); i++) {
            if (archiveGroups.get(i).identifier.equals(group.identifier)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return null;
        }
        int right = getListRight() - SCROLLBAR_WIDTH - 4;
        Integer rowTop = rowTop(index, right);
        if (rowTop == null) {
            return null;
        }
        if (!canShowVersionDropdown(group, rowTop, right)) {
            return null;
        }
        int buttonX = versionButtonX();
        int buttonY = versionLineY(group, rowTop, right);
        int maxLabelWidth = 0;
        for (PackArchiveEntry entry : group.versions) {
            maxLabelWidth = Math.max(maxLabelWidth, this.font.width(versionOptionLabel(entry)));
        }
        int dropdownWidth = Math.max(versionButtonWidth(group), maxLabelWidth + VERSION_BUTTON_HORIZONTAL_PADDING * 2);
        int dropdownHeight = group.versions.size() * VERSION_DROPDOWN_ITEM_HEIGHT + 2;
        int x = Math.max(getListLeft(), Math.min(buttonX, right - dropdownWidth));
        int belowY = buttonY + BUTTON_HEIGHT + 2;
        int y = belowY + dropdownHeight <= getListBottom() ? belowY : Math.max(getListTop(), buttonY - dropdownHeight - 2);
        return new VersionDropdownBounds(x, y, dropdownWidth, dropdownHeight);
    }

    private boolean canShowVersionDropdown(PackArchiveGroup group, int rowTop, int right) {
        int buttonY = versionLineY(group, rowTop, right);
        int dropdownHeight = group.versions.size() * VERSION_DROPDOWN_ITEM_HEIGHT + 2;
        int below = getListBottom() - (buttonY + BUTTON_HEIGHT + 2);
        int above = buttonY - 2 - getListTop();
        return dropdownHeight <= below || dropdownHeight <= above;
    }

    private Component versionButtonLabel(PackArchiveGroup group) {
        PackArchiveEntry entry = group.displayEntry();
        Component version = Component.literal(entry.metadata.version);
        return isInstalledVersion(entry)
            ? Component.translatable("dlc_manager.pack.version_current_short", version)
            : version;
    }

    private Component versionOptionLabel(PackArchiveEntry entry) {
        Component version = Component.literal(entry.metadata.version);
        return isInstalledVersion(entry)
            ? Component.translatable("dlc_manager.pack.version_current_short", version)
            : version;
    }

    private int versionButtonWidth(PackArchiveGroup group) {
        return this.font.width(versionButtonLabel(group)) + VERSION_BUTTON_HORIZONTAL_PADDING * 2;
    }

    private int versionButtonX() {
        return getListLeft() + ORDER_BUTTON_WIDTH + 12 + ICON_SIZE + 8 + this.font.width(Component.translatable("dlc_manager.pack.version_prefix")) + VERSION_VALUE_GAP - VERSION_BUTTON_HORIZONTAL_PADDING;
    }

    private int versionLineY(PackArchiveGroup group, int rowY, int right) {
        PackArchiveEntry entry = group.displayEntry();
        int textX = getListLeft() + ORDER_BUTTON_WIDTH + 12 + ICON_SIZE + 8;
        int textRight = right - BUTTON_WIDTH - 18;
        Component homepagePrompt = homepagePrompt(entry);
        int homepageWidth = homepagePrompt == null ? 0 : Math.min(Math.round(this.font.width(homepagePrompt) * HOMEPAGE_PROMPT_SCALE), Math.max(1, (textRight - textX) / 2));
        int nameWidth = Math.max(1, textRight - textX - (homepagePrompt == null ? 0 : homepageWidth + HOMEPAGE_PROMPT_GAP));
        int y = rowY + ROW_PADDING_TOP;
        y += Math.max(1, this.font.split(entry.displayName, nameWidth).size()) * 10 + 4;
        y += authorLines(entry, textRight - textX).size() * 10 + 4;
        return y;
    }

    private void moveEntry(int index, int direction) {
        int target = index + direction;
        if (target < 0 || target >= archiveGroups.size()) {
            return;
        }
        List<PackArchiveGroup> mutable = new ArrayList<>(archiveGroups);
        PackArchiveGroup entry = mutable.remove(index);
        mutable.add(target, entry);
        archiveGroups = mutable;
        currentArchiveOrder = collectArchiveKeys(archiveGroups);
        saveLiveOrder(currentArchiveOrder);
        selectedIndex = target;
        ensureSelectionVisible();
        rebuildPackWidgets();
    }

    private void importPackArchive() {
        if (!DlcConfig.isPackFuncOn()) {
            status = Component.translatable("dlc_manager.pack.disabled");
            return;
        }
        Path zip = DlcFilePicker.pickUpdateZip(Component.translatable("dlc_manager.pack.import").getString());
        if (zip == null) {
            return;
        }
        setBusy(true, Component.translatable("dlc_manager.pack.importing"), BusyOperation.IMPORT);
        updateOperationProgress(Component.translatable("dlc_manager.pack.importing"), -1f);
        new Thread(() -> {
            Path copied = null;
            try {
                checkCanceled();
                copied = copyArchiveToPackRoot(zip);
                checkCanceled();
                finishBusy(Component.translatable("dlc_manager.pack.import_staged", copied.getFileName().toString()));
            } catch (DlcDownloadService.DownloadCanceledException ex) {
                if (copied != null) {
                    try {
                        Files.deleteIfExists(copied);
                    } catch (IOException ignored) {
                    }
                }
                finishBusy(Component.translatable("dlc_manager.pack.canceled"));
            } catch (Exception ex) {
                finishBusy(Component.translatable("dlc_manager.pack.import_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }, "dlc-pack-import-copy").start();
    }

    public void importDownloadedPack(Path zip) {
        if (zip != null) {
            status = Component.translatable("dlc_manager.pack.import_staged", zip.getFileName().toString());
            refreshEntries();
            rebuildPackWidgets();
        }
    }

    private Path copyArchiveToPackRoot(Path source) throws IOException {
        Path packRoot = DlcPaths.dlcRoot().resolve("pack");
        Files.createDirectories(packRoot);
        String fileName = source.getFileName() == null ? "pack.zip" : source.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            fileName = fileName + ".zip";
        }
        Path target = packRoot.resolve(fileName);
        if (source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
            return target;
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private void applyPendingChanges() {
        if (!hasPendingChanges() || busy) {
            return;
        }
        if (!hasPackOperationChanges()) {
            commitCurrentOrderWithoutRestart();
            return;
        }
        String confirmText = buildReplacementConfirmText();
        if (confirmText != null) {
            Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(accepted -> {
                Minecraft.getInstance().setScreen(this);
                if (accepted) {
                    continueApplyAfterReplacementConfirm();
                }
            }, Component.translatable("dlc_manager.pack.apply_confirm_title"), Component.literal(confirmText), DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
            return;
        }
        continueApplyAfterReplacementConfirm();
    }

    private void continueApplyAfterReplacementConfirm() {
        String runtimeConfirmText = buildRuntimeConfirmText();
        if (runtimeConfirmText != null) {
            Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(accepted -> {
                Minecraft.getInstance().setScreen(this);
                if (accepted) {
                    startApplyPendingChanges(true);
                }
            }, Component.translatable("dlc_manager.pack.runtime_confirm_title"), Component.literal(runtimeConfirmText), DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
            return;
        }
        startApplyPendingChanges(false);
    }

    private void commitCurrentOrderWithoutRestart() {
        committedArchiveOrder = List.copyOf(currentArchiveOrder);
        entryArchiveOrder = List.copyOf(committedArchiveOrder);
        DlcPackOrder.save(packOrderPath(), committedArchiveOrder);
        status = Component.translatable("dlc_manager.pack.order_applied");
        rebuildPackWidgets();
    }

    private void startApplyPendingChanges(boolean allowRuntimeChanges) {
        pausedAllowRuntimeChanges = allowRuntimeChanges;
        setBusy(true, Component.translatable("dlc_manager.pack.applying"), BusyOperation.APPLY);
        updateOperationProgress(Component.translatable("dlc_manager.pack.applying"), 0f);
        new Thread(() -> {
            try {
                DlcPackInstaller installer = new DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot());
                applyOrderedChanges(installer, allowRuntimeChanges);
                pendingUninstalls.clear();
                pendingInstalls.clear();
                pausedReinstallIdentifiers.clear();
                activeReinstallIdentifiers = Set.of();
                finishBusy(Component.translatable("dlc_manager.pack.apply_success"), true);
            } catch (DlcDownloadService.DownloadCanceledException ex) {
                if (cancelApplyRequested) {
                    finishBusy(Component.translatable("dlc_manager.pack.canceled"));
                } else {
                    pauseApplyOperation();
                }
            } catch (Exception ex) {
                finishBusy(Component.translatable("dlc_manager.pack.apply_failed", describeFailure(ex)));
            }
        }, "dlc-pack-apply").start();
    }

    private void applyOrderedChanges(DlcPackInstaller installer, boolean allowRuntimeChanges) throws IOException {
        List<PackArchiveGroup> orderedGroups = new ArrayList<>(archiveGroups);
        orderedGroups.sort(Comparator.comparingInt(group -> orderIndex(group.orderKey)));
        List<PackArchiveEntry> ordered = new ArrayList<>();
        for (PackArchiveGroup group : orderedGroups) {
            ordered.add(effectiveApplyEntry(group));
        }
        Set<String> reinstallIdentifiers = new LinkedHashSet<>(collectAffectedReinstallIdentifiers(ordered));
        reinstallIdentifiers.addAll(pausedReinstallIdentifiers);
        activeReinstallIdentifiers = Set.copyOf(reinstallIdentifiers);
        OperationDisplayMode displayMode = determineOperationDisplayMode(reinstallIdentifiers);
        boolean hasUninstallPhase = false;
        for (PackArchiveEntry entry : ordered) {
            String identifier = entry.metadata.identifier;
            if (isEntryCurrentlyInstalled(entry) && (pendingUninstalls.contains(identifier) || reinstallIdentifiers.contains(identifier))) {
                hasUninstallPhase = true;
                break;
            }
        }
        float installOffset = hasUninstallPhase ? 0.35f : 0f;
        float installSpan = hasUninstallPhase ? 0.65f : 1f;
        Set<String> uninstalled = new LinkedHashSet<>();
        for (PackArchiveEntry entry : ordered) {
            String identifier = entry.metadata.identifier;
            if (!uninstalled.contains(identifier) && isEntryCurrentlyInstalled(entry) && (pendingUninstalls.contains(identifier) || reinstallIdentifiers.contains(identifier))) {
                checkCanceled();
                updateOperationProgress(operationStatusFor(displayMode, entry, null), -1f);
                installer.uninstall(identifier, (message, progress) ->
                    updateOperationProgress(operationStatusFor(displayMode, entry, message), normalizePhaseProgress(0f, 0.35f, progress), operationDetailFor(message))
                );
                uninstalled.add(identifier);
            }
        }
        Set<String> installed = new LinkedHashSet<>();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            PackArchiveEntry entry = ordered.get(i);
            String identifier = entry.metadata.identifier;
            if (!installed.contains(identifier) && !pendingUninstalls.contains(identifier) && (pendingInstalls.get(identifier) == entry || (reinstallIdentifiers.contains(identifier) && isEntryCurrentlyInstalled(entry)))) {
                checkCanceled();
                updateOperationProgress(operationStatusFor(displayMode, entry, null), installOffset);
                installArchive(installer, entry, allowRuntimeChanges, displayMode, installOffset, installSpan);
                installed.add(identifier);
            }
        }
        checkCanceled();
        committedArchiveOrder = finalAppliedOrder();
        currentArchiveOrder = List.copyOf(committedArchiveOrder);
        entryArchiveOrder = List.copyOf(committedArchiveOrder);
        DlcPackOrder.save(packOrderPath(), committedArchiveOrder);
    }

    private PackArchiveEntry effectiveApplyEntry(PackArchiveGroup group) {
        PackArchiveEntry pending = pendingInstalls.get(group.identifier);
        if (pending != null) {
            return pending;
        }
        DlcPackMeta installed = installedByIdentifier.get(group.identifier);
        if (installed != null) {
            for (PackArchiveEntry entry : group.versions) {
                if (entry.metadata.version.equals(installed.version)) {
                    return entry;
                }
            }
        }
        return group.displayEntry();
    }

    private List<String> finalAppliedOrder() {
        Map<String, PackArchiveGroup> groupsById = new HashMap<>();
        for (PackArchiveGroup group : archiveGroups) {
            groupsById.put(group.identifier, group);
        }
        List<String> out = new ArrayList<>();
        for (String key : currentArchiveOrder) {
            String identifier = identifierFromOrderKey(key);
            if (identifier == null) {
                continue;
            }
            PackArchiveGroup group = groupsById.get(identifier);
            if (group == null) {
                continue;
            }
            PackArchiveEntry pending = pendingInstalls.get(identifier);
            String finalKey = pending != null
                ? pending.key
                : pendingUninstalls.contains(identifier)
                    ? group.versions.get(0).key
                    : group.orderKey;
            if (!out.contains(finalKey)) {
                out.add(finalKey);
            }
        }
        for (PackArchiveGroup group : archiveGroups) {
            PackArchiveEntry pending = pendingInstalls.get(group.identifier);
            String finalKey = pending != null
                ? pending.key
                : pendingUninstalls.contains(group.identifier)
                    ? group.versions.get(0).key
                    : group.orderKey;
            if (!out.contains(finalKey)) {
                out.add(finalKey);
            }
        }
        return out;
    }

    private OperationDisplayMode determineOperationDisplayMode(Set<String> reinstallIdentifiers) {
        boolean hasInstallOps = !pendingInstalls.isEmpty() || !reinstallIdentifiers.isEmpty();
        boolean hasReplacementInstalls = false;
        for (String identifier : pendingInstalls.keySet()) {
            if (installedByIdentifier.containsKey(identifier)) {
                hasReplacementInstalls = true;
                break;
            }
        }
        boolean hasUninstallOps = !pendingUninstalls.isEmpty() || !reinstallIdentifiers.isEmpty() || hasReplacementInstalls;
        if (hasUninstallOps && !hasInstallOps) {
            return OperationDisplayMode.UNINSTALLING;
        }
        if (hasInstallOps && !hasUninstallOps) {
            return OperationDisplayMode.INSTALLING;
        }
        return OperationDisplayMode.APPLYING;
    }

    private Set<String> collectAffectedReinstallIdentifiers(List<PackArchiveEntry> ordered) throws IOException {
        List<PackArchiveEntry> finalEntries = new ArrayList<>();
        for (PackArchiveEntry entry : ordered) {
            String identifier = entry.metadata.identifier;
            if (pendingUninstalls.contains(identifier)) {
                continue;
            }
            if (pendingInstalls.get(identifier) == entry || isEntryCurrentlyInstalled(entry)) {
                finalEntries.add(entry);
            }
        }
        Set<String> affected = new LinkedHashSet<>();
        for (int i = 0; i < finalEntries.size(); i++) {
            PackArchiveEntry left = finalEntries.get(i);
            for (int j = i + 1; j < finalEntries.size(); j++) {
                PackArchiveEntry right = finalEntries.get(j);
                if (!packsHaveFileConflict(left, right)) {
                    continue;
                }
                if (pairOrderChanged(left, right)) {
                    affected.add(left.metadata.identifier);
                    affected.add(right.metadata.identifier);
                }
            }
        }
        return affected;
    }

    private boolean packsHaveFileConflict(PackArchiveEntry left, PackArchiveEntry right) throws IOException {
        Set<String> leftFiles = inspectArchiveFiles(left);
        if (leftFiles.isEmpty()) {
            return false;
        }
        Set<String> rightFiles = inspectArchiveFiles(right);
        for (String file : leftFiles) {
            if (rightFiles.contains(file)) {
                return true;
            }
        }
        return false;
    }

    private boolean pairOrderChanged(PackArchiveEntry left, PackArchiveEntry right) {
        int leftCommitted = committedOrderIndex(left.key);
        int rightCommitted = committedOrderIndex(right.key);
        if (leftCommitted < 0 || rightCommitted < 0) {
            return true;
        }
        return leftCommitted > rightCommitted;
    }

    private boolean isEntryCurrentlyInstalled(PackArchiveEntry entry) {
        if (entry == null) {
            return false;
        }
        DlcPackMeta installed = installedByIdentifier.get(entry.metadata.identifier);
        return installed != null && entry.metadata.version.equals(installed.version);
    }

    private boolean isInstalledVersion(PackArchiveEntry entry) {
        DlcPackMeta installed = installedByIdentifier.get(entry.metadata.identifier);
        return installed != null && entry.metadata.version.equals(installed.version);
    }

    private Set<String> inspectArchiveFiles(PackArchiveEntry entry) throws IOException {
        Path staging = null;
        try {
            staging = DlcPackArchive.extractToStaging(entry.archivePath, DlcPaths.dlcRoot()).stagingDir;
            DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
            if (!result.valid || result.pack == null) {
                throw new IOException(result.error == null ? "invalid DLC Pack" : result.error);
            }
            Set<String> files = new HashSet<>();
            DlcPackInstallPlan plan = DlcPackInstallPlan.build(result.pack, DlcPaths.gameDir(), DlcPaths.dlcRoot());
            try {
                plan = DlcPackInstallPlan.merge(plan, DlcPackCFResolver.resolveManifestTargets(result.pack), DlcPaths.gameDir(), DlcPaths.dlcRoot());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", ex);
            }
            for (DlcPackInstallPlan.FileEntry file : plan.files) {
                files.add(file.relativePath);
            }
            return files;
        } finally {
            DlcPackArchive.deleteRecursive(staging);
        }
    }

    private String buildReplacementConfirmText() {
        List<String> lines = new ArrayList<>();
        for (PackArchiveGroup group : archiveGroups) {
            PackArchiveEntry entry = group.displayEntry();
            if (pendingInstalls.get(entry.metadata.identifier) != entry) {
                continue;
            }
            DlcPackMeta installed = installedByIdentifier.get(entry.metadata.identifier);
            if (installed == null) {
                continue;
            }
            int compare = DlcPackMetadata.compareVersionStrings(entry.metadata.version, installed.version);
            if (compare > 0) {
                lines.add(Component.translatable("dlc_manager.pack.apply_confirm_upgrade", entry.displayNameText, installed.version, entry.metadata.version).getString());
            } else if (compare < 0) {
                lines.add(Component.translatable("dlc_manager.pack.apply_confirm_downgrade", entry.displayNameText, installed.version, entry.metadata.version).getString());
            } else {
                lines.add(Component.translatable("dlc_manager.pack.apply_confirm_replace", entry.displayNameText, installed.version).getString());
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private String buildRuntimeConfirmText() {
        List<String> messages = new ArrayList<>();
        DlcPackInstaller installer = new DlcPackInstaller(DlcPaths.gameDir(), DlcPaths.dlcRoot());
        for (PackArchiveGroup group : archiveGroups) {
            PackArchiveEntry entry = group.displayEntry();
            if (pendingInstalls.get(entry.metadata.identifier) != entry) {
                continue;
            }
            Path staging = null;
            try {
                staging = DlcPackArchive.extractToStaging(entry.archivePath, DlcPaths.dlcRoot()).stagingDir;
                DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
                if (!result.valid || result.pack == null) {
                    messages.add(result.error == null ? "invalid DLC Pack" : result.error);
                    continue;
                }
                DlcPackInstaller.PrecheckResult precheck = installer.precheck(result.pack);
                if (precheck.hasBlockingIssues()) {
                    messages.add(DlcConfirmUtil.describePackBlockingPrecheck(precheck));
                    continue;
                }
                if (precheck.requiresRuntimeConfirmation()) {
                    messages.add(Component.translatable(
                        "dlc_manager.pack.runtime_confirm_entry",
                        entry.displayNameText,
                        DlcConfirmUtil.describePackBlockingPrecheck(precheck)
                    ).getString());
                }
            } catch (Exception ex) {
                messages.add(describeFailure(ex));
            } finally {
                DlcPackArchive.deleteRecursive(staging);
            }
        }
        return messages.isEmpty() ? null : String.join("\n\n", messages);
    }

    private void installArchive(
        DlcPackInstaller installer,
        PackArchiveEntry entry,
        boolean allowRuntimeChanges,
        OperationDisplayMode displayMode,
        float installOffset,
        float installSpan
    ) throws IOException {
        Path staging = null;
        try {
            staging = DlcPackArchive.extractToStaging(entry.archivePath, DlcPaths.dlcRoot()).stagingDir;
            DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
            if (!result.valid || result.pack == null) {
                throw new IOException(result.error == null ? "invalid DLC Pack" : result.error);
            }
            DlcPackInstaller.PrecheckResult precheck = installer.precheck(result.pack);
            if (precheck.hasBlockingIssues() || (precheck.requiresRuntimeConfirmation() && !allowRuntimeChanges)) {
                throw new IOException(DlcConfirmUtil.describePackBlockingPrecheck(precheck));
            }
            boolean replace = installedByIdentifier.containsKey(result.pack.metadata.identifier);
            DlcPackInstaller.InstallOptions options = allowRuntimeChanges
                ? DlcPackInstaller.InstallOptions.applyConfirmedRuntimeChanges()
                : DlcPackInstaller.InstallOptions.strict();
            options = options
                .withCancelChecker(() -> cancelRequested)
                .withProgressListener((message, progress) ->
                    updateOperationProgress(operationStatusFor(displayMode, entry, message), normalizePhaseProgress(installOffset, installSpan, progress), operationDetailFor(message))
                );
            installer.install(result.pack, replace, options);
        } finally {
            DlcPackArchive.deleteRecursive(staging);
        }
    }

    private void checkCanceled() throws DlcDownloadService.DownloadCanceledException {
        if (cancelRequested) {
            throw new DlcDownloadService.DownloadCanceledException("canceled");
        }
    }

    private float normalizePhaseProgress(float offset, float span, float progress) {
        return progress < 0f ? -1f : offset + Math.max(0f, Math.min(1f, progress)) * span;
    }

    private Component operationStatusFor(OperationDisplayMode mode, PackArchiveEntry entry, String fallback) {
        if ("dlc_manager.pack.selecting_source".equals(fallback)) {
            return Component.translatable("dlc_manager.pack.selecting_source");
        }
        return switch (mode) {
            case UNINSTALLING -> Component.translatable("dlc_manager.pack.operation_uninstalling");
            case INSTALLING -> Component.translatable("dlc_manager.pack.operation_installing");
            case APPLYING -> Component.translatable("dlc_manager.pack.operation_applying");
        };
    }

    private Component formatOperationTitle(Component message, float progress, String progressText) {
        String text = message.getString();
        while (text.endsWith(".") || text.endsWith("。") || text.endsWith("…")) {
            text = text.substring(0, text.length() - 1);
        }
        return progress >= 0f ? Component.literal(text + "：" + progressText) : Component.literal(text + "...");
    }

    private String describeFailure(Exception ex) {
        if (ex instanceof IOException io) {
            String message = io.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        String message = ex.getMessage();
        return (message == null || message.isBlank()) ? ex.getClass().getSimpleName() : message;
    }

    private void setBusy(boolean busy, Component message) {
        setBusy(busy, message, BusyOperation.NONE);
    }

    private void setBusy(boolean busy, Component message, BusyOperation operation) {
        if (busy) {
            cancelRequested = false;
            cancelApplyRequested = false;
            paused = false;
            busyOperation = operation;
            operationProgress = -1f;
            operationStatus = message;
            operationDetail = null;
        } else {
            paused = false;
            busyOperation = BusyOperation.NONE;
            operationDetail = null;
        }
        this.busy = busy;
        this.status = message;
        rebuildPackWidgets();
    }

    private void finishBusy(Component message) {
        finishBusy(message, false);
    }

    private void finishBusy(Component message, boolean promptRestart) {
        Minecraft.getInstance().execute(() -> {
            busy = false;
            cancelRequested = false;
            cancelApplyRequested = false;
            paused = false;
            busyOperation = BusyOperation.NONE;
            operationProgress = -1f;
            operationStatus = null;
            operationDetail = null;
            pausedAllowRuntimeChanges = false;
            status = message;
            refreshEntries();
            rebuildPackWidgets();
            if (promptRestart) {
                showRestartPrompt();
            }
        });
    }

    private void pauseApplyOperation() {
        Minecraft.getInstance().execute(() -> {
            busy = true;
            cancelRequested = false;
            cancelApplyRequested = false;
            paused = true;
            busyOperation = BusyOperation.APPLY;
            operationProgress = -1f;
            operationStatus = Component.translatable("dlc_manager.pack.paused");
            operationDetail = null;
            status = operationStatus;
            pausedReinstallIdentifiers.clear();
            pausedReinstallIdentifiers.addAll(activeReinstallIdentifiers);
            refreshEntries();
            rebuildPackWidgets();
        });
    }

    private void resumePausedApply() {
        if (!paused || busyOperation != BusyOperation.APPLY) {
            return;
        }
        boolean allowRuntimeChanges = pausedAllowRuntimeChanges;
        busy = false;
        paused = false;
        cancelRequested = false;
        cancelApplyRequested = false;
        busyOperation = BusyOperation.NONE;
        startApplyPendingChanges(allowRuntimeChanges);
    }

    private void requestPauseApplyOperation() {
        if (busyOperation != BusyOperation.APPLY || cancelRequested) {
            return;
        }
        cancelApplyRequested = false;
        cancelRequested = true;
        status = Component.translatable("dlc_manager.pack.pausing");
        operationStatus = status;
        rebuildPackWidgets();
    }

    private void requestCancelApplyOperation() {
        if (busyOperation != BusyOperation.APPLY) {
            return;
        }
        cancelApplyRequested = true;
        if (paused) {
            finishBusy(Component.translatable("dlc_manager.pack.canceled"));
            return;
        }
        cancelRequested = true;
        status = Component.translatable("dlc_manager.pack.canceling");
        operationStatus = status;
        rebuildPackWidgets();
    }

    private void showRestartPrompt() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                minecraft.stop();
            } else {
                minecraft.setScreen(this);
            }
        }, Component.translatable("dlc_manager.title"), Component.translatable("dlc_manager.pack.restart_prompt"),
            Component.translatable("dlc_manager.restart_now"),
            Component.translatable("dlc_manager.restart_later")));
    }

    private void onBackOrCancel() {
        if (busy) {
            if (paused && busyOperation == BusyOperation.APPLY) {
                requestCancelApplyOperation();
                return;
            }
            if (busyOperation == BusyOperation.DELETE) {
                return;
            }
            cancelRequested = true;
            status = busyOperation == BusyOperation.APPLY
                ? Component.translatable("dlc_manager.pack.pausing")
                : Component.translatable("dlc_manager.pack.canceling");
            operationStatus = status;
            operationDetail = null;
            rebuildPackWidgets();
            return;
        }
        if (hasPendingChanges()) {
            pendingInstalls.clear();
            pendingUninstalls.clear();
            currentArchiveOrder = List.copyOf(entryArchiveOrder);
            committedArchiveOrder = List.copyOf(entryArchiveOrder);
            saveLiveOrder(entryArchiveOrder);
            sortArchiveGroupsByCurrentOrder();
            returnToParent();
            return;
        }
        returnToParent();
    }

    private void returnToParent() {
        busy = false;
        paused = false;
        cancelRequested = false;
        cancelApplyRequested = false;
        busyOperation = BusyOperation.NONE;
        activeReinstallIdentifiers = Set.of();
        releaseIconTextures();
        Minecraft.getInstance().setScreen(parent);
    }

    public void selectPackVersion(String identifier, String versionKey) {
        PackArchiveGroup group = findGroup(identifier);
        if (group == null) {
            return;
        }
        PackArchiveEntry selected = null;
        for (PackArchiveEntry entry : group.versions) {
            if (entry.key.equals(versionKey)) {
                selected = entry;
                break;
            }
        }
        if (selected == null) {
            return;
        }
        versionDropdownIdentifier = null;
        selectedVersionByIdentifier.put(identifier, selected.key);
        pendingInstalls.remove(identifier);
        pendingUninstalls.remove(identifier);
        List<PackArchiveGroup> updated = new ArrayList<>();
        for (PackArchiveGroup item : archiveGroups) {
            updated.add(item.identifier.equals(identifier) ? item.withSelectedVersionKey(selected.key) : item);
        }
        archiveGroups = updated;
        rebuildPackWidgets();
    }

    private void refreshEntries() {
        Map<String, String> pendingInstallKeys = new HashMap<>();
        for (Map.Entry<String, PackArchiveEntry> entry : pendingInstalls.entrySet()) {
            pendingInstallKeys.put(entry.getKey(), entry.getValue().key);
        }
        releaseIconTextures();
        uninstallIncompatibleInstalledPacks();
        installedByIdentifier = loadInstalledPacks();
        List<PackArchiveGroup> scanned = scanPackArchives();
        List<String> saved = DlcPackOrder.load(packOrderPath());
        boolean preserveCurrentOrder = !currentArchiveOrder.isEmpty() && hasOrderChanges();
        List<String> normalizedSaved = normalizeGroupOrder(saved, scanned);
        if (!orderSnapshotInitialized) {
            entryArchiveOrder = List.copyOf(normalizedSaved);
            orderSnapshotInitialized = true;
        }
        committedArchiveOrder = preserveCurrentOrder ? normalizeGroupOrder(currentArchiveOrder, scanned) : normalizedSaved;
        if (!preserveCurrentOrder) {
            entryArchiveOrder = List.copyOf(committedArchiveOrder);
        }
        saveLiveOrder(committedArchiveOrder);
        if (preserveCurrentOrder) {
            currentArchiveOrder = committedArchiveOrder;
        } else {
            currentArchiveOrder = committedArchiveOrder;
        }
        archiveGroups = scanned;
        normalizeGroupSelections();
        sortArchiveGroupsByCurrentOrder();
        pendingInstalls.clear();
        for (PackArchiveGroup group : archiveGroups) {
            PackArchiveEntry entry = group.displayEntry();
            if (!isEntryCurrentlyInstalled(entry) && entry.key.equals(pendingInstallKeys.get(entry.metadata.identifier))) {
                pendingInstalls.put(entry.metadata.identifier, entry);
            }
        }
        pendingUninstalls.removeIf(identifier -> !installedByIdentifier.containsKey(identifier));
        if (selectedIndex >= archiveGroups.size()) {
            selectedIndex = archiveGroups.isEmpty() ? -1 : archiveGroups.size() - 1;
        }
        clampScroll();
    }

    private Path packOrderPath() {
        return DlcPlatformServices.configDir().resolve("dlc_manager").resolve("order.dc");
    }

    private void saveLiveOrder(List<String> order) {
        DlcPackOrder.save(packOrderPath(), order);
    }

    private String archiveOrderPath(Path archive) {
        Path packRoot = DlcPaths.dlcRoot().resolve("pack").toAbsolutePath().normalize();
        Path normalized = archive.toAbsolutePath().normalize();
        try {
            return packRoot.relativize(normalized).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return archive.getFileName().toString();
        }
    }

    private List<String> collectArchiveKeys(List<PackArchiveGroup> entries) {
        List<String> keys = new ArrayList<>();
        for (PackArchiveGroup entry : entries) {
            keys.add(entry.orderKey);
        }
        return keys;
    }

    private int orderIndex(String key) {
        int index = currentArchiveOrder.indexOf(key);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private int committedOrderIndex(String key) {
        return entryArchiveOrder.indexOf(key);
    }

    private void sortArchiveGroupsByCurrentOrder() {
        List<PackArchiveGroup> sorted = new ArrayList<>(archiveGroups);
        sorted.sort(Comparator.comparingInt(entry -> orderIndex(entry.orderKey)));
        archiveGroups = sorted;
    }

    private List<PackArchiveGroup> scanPackArchives() {
        Path packRoot = DlcPaths.dlcRoot().resolve("pack");
        try {
            Files.createDirectories(packRoot);
        } catch (IOException ignored) {
        }
        if (!Files.isDirectory(packRoot)) {
            return List.of();
        }
        List<PackArchiveEntry> out = new ArrayList<>();
        try (var stream = Files.list(packRoot)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .forEach(path -> {
                    PackArchiveEntry entry = inspectArchive(path);
                    if (entry != null) {
                        out.add(entry);
                    }
                });
        } catch (Exception ignored) {
        }
        return groupArchiveEntries(out);
    }

    private List<PackArchiveGroup> groupArchiveEntries(List<PackArchiveEntry> entries) {
        Map<String, List<PackArchiveEntry>> byIdentifier = new LinkedHashMap<>();
        for (PackArchiveEntry entry : entries) {
            byIdentifier.computeIfAbsent(entry.metadata.identifier, ignored -> new ArrayList<>()).add(entry);
        }
        List<PackArchiveGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<PackArchiveEntry>> item : byIdentifier.entrySet()) {
            String identifier = item.getKey();
            List<PackArchiveEntry> versions = new ArrayList<>(item.getValue());
            versions.sort((a, b) -> {
                int compare = DlcPackMetadata.compareVersionStrings(b.metadata.version, a.metadata.version);
                return compare != 0 ? compare : a.archiveOrderPath.compareToIgnoreCase(b.archiveOrderPath);
            });
            DlcPackMeta installed = installedByIdentifier.get(identifier);
            PackArchiveEntry orderEntry = versions.get(0);
            if (installed != null) {
                for (PackArchiveEntry entry : versions) {
                    if (entry.metadata.version.equals(installed.version)) {
                        orderEntry = entry;
                        break;
                    }
                }
            }
            String selectedVersion = selectedVersionByIdentifier.get(identifier);
            PackArchiveEntry displayEntry = orderEntry;
            if (selectedVersion != null) {
                for (PackArchiveEntry entry : versions) {
                    if (entry.key.equals(selectedVersion) || entry.metadata.version.equals(selectedVersion)) {
                        displayEntry = entry;
                        break;
                    }
                }
            }
            groups.add(new PackArchiveGroup(identifier, versions, orderEntry.key, displayEntry.key));
        }
        return groups;
    }

    private void normalizeGroupSelections() {
        Set<String> validIdentifiers = new HashSet<>();
        for (PackArchiveGroup group : archiveGroups) {
            validIdentifiers.add(group.identifier);
        }
        selectedVersionByIdentifier.keySet().removeIf(identifier -> !validIdentifiers.contains(identifier));
        List<PackArchiveGroup> updated = new ArrayList<>();
        for (PackArchiveGroup group : archiveGroups) {
            String selected = selectedVersionByIdentifier.get(group.identifier);
            PackArchiveEntry display = group.displayEntry();
            if (selected != null) {
                for (PackArchiveEntry entry : group.versions) {
                    if (entry.key.equals(selected) || entry.metadata.version.equals(selected)) {
                        display = entry;
                        break;
                    }
                }
            }
            selectedVersionByIdentifier.put(group.identifier, display.key);
            updated.add(group.withSelectedVersionKey(display.key));
        }
        archiveGroups = updated;
    }

    private List<String> normalizeGroupOrder(List<String> savedOrder, List<PackArchiveGroup> groups) {
        List<String> out = new ArrayList<>();
        if (savedOrder != null) {
            for (String key : savedOrder) {
                String identifier = identifierFromOrderKey(key);
                if (identifier == null) {
                    continue;
                }
                for (PackArchiveGroup group : groups) {
                    if (group.identifier.equals(identifier) && !out.contains(group.orderKey)) {
                        out.add(group.orderKey);
                        break;
                    }
                }
            }
        }
        for (PackArchiveGroup group : groups) {
            if (!out.contains(group.orderKey)) {
                out.add(group.orderKey);
            }
        }
        return out;
    }

    private boolean orderKeyMatchesIdentifier(String key, String identifier) {
        return identifier.equals(identifierFromOrderKey(key));
    }

    private String identifierFromOrderKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        int hash = key.lastIndexOf('#');
        int at = key.lastIndexOf('@');
        if (hash < 0 || at <= hash + 1) {
            return null;
        }
        String identifier = key.substring(hash + 1, at);
        return identifier.isBlank() ? null : identifier;
    }

    private PackArchiveGroup findGroup(String identifier) {
        for (PackArchiveGroup group : archiveGroups) {
            if (group.identifier.equals(identifier)) {
                return group;
            }
        }
        return null;
    }

    private PackArchiveEntry inspectArchive(Path archive) {
        Path staging = null;
        try {
            staging = DlcPackArchive.extractToStaging(archive, DlcPaths.dlcRoot()).stagingDir;
            DlcPackPackage.Result result = DlcPackPackage.inspect(staging);
            if (!result.valid || result.pack == null) {
                return null;
            }
            DlcPackPackage pack = result.pack;
            String lang = Minecraft.getInstance().options.languageCode;
            Component displayName = DlcTextFormatter.parse(pack.metadata.resolveDisplayName(lang));
            Component description = DlcMarkdownRenderer.renderMarkdownComponent(pack.metadata.resolveDescription(lang));
            List<String> authors = pack.metadata.authors.isEmpty()
                ? List.of(Component.translatable("dlc_manager.pack.unknown_author").getString())
                : pack.metadata.authors;
            Component author = DlcTextFormatter.parse(String.join(", ", authors));
            IconTexture icon = loadIconTexture(pack.iconFile);
            return new PackArchiveEntry(archive.toAbsolutePath().normalize(), archiveOrderPath(archive), pack.metadata, displayName, description, author, authors, icon == null ? null : icon.location, icon == null ? 0 : icon.width, icon == null ? 0 : icon.height);
        } catch (Exception ignored) {
            return null;
        } finally {
            DlcPackArchive.deleteRecursive(staging);
        }
    }

    private IconTexture loadIconTexture(Path iconFile) {
        if (iconFile == null || !Files.isRegularFile(iconFile)) {
            return null;
        }
        NativeImage image = null;
        try (InputStream input = Files.newInputStream(iconFile)) {
            image = NativeImage.read(input);
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            DynamicTexture texture = new DynamicTexture(image);
            image = null;
            ResourceLocation location = Minecraft.getInstance().getTextureManager().register("dlc_pack_icon/" + (++iconTextureSerial), texture);
            return new IconTexture(location, imageWidth, imageHeight);
        } catch (Exception ignored) {
            if (image != null) {
                image.close();
            }
            return null;
        }
    }

    private void releaseIconTextures() {
        for (PackArchiveGroup group : archiveGroups) {
            for (PackArchiveEntry entry : group.versions) {
                if (entry.iconTexture != null) {
                    Minecraft.getInstance().getTextureManager().release(entry.iconTexture);
                }
            }
        }
        archiveGroups = List.of();
    }

    private Map<String, DlcPackMeta> loadInstalledPacks() {
        Path packRoot = DlcPaths.dlcRoot().resolve("tmp").resolve("pack");
        Map<String, DlcPackMeta> out = new HashMap<>();
        if (!Files.isDirectory(packRoot)) {
            return out;
        }
        try (var stream = Files.list(packRoot)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                DlcPackMeta meta = DlcPackMeta.load(dir.resolve("meta.dc"));
                if (meta != null) {
                    out.put(meta.identifier, meta);
                }
            });
        } catch (Exception ignored) {
        }
        return out;
    }

    private void uninstallIncompatibleInstalledPacks() {
        try {
            List<DlcPackInstaller.UninstallReport> reports =
                new DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot()).uninstallIncompatibleForCurrentVersion();
            if (!reports.isEmpty()) {
                status = Component.translatable("dlc_manager.pack.auto_uninstalled_incompatible", reports.size());
            }
        } catch (Exception ex) {
            status = Component.translatable("dlc_manager.pack.auto_uninstall_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private boolean hasPendingChanges() {
        return hasPackOperationChanges() || hasOrderChanges();
    }

    private boolean hasPackOperationChanges() {
        return !pendingInstalls.isEmpty() || !pendingUninstalls.isEmpty();
    }

    private boolean hasOrderChanges() {
        return !currentArchiveOrder.equals(entryArchiveOrder);
    }

    private boolean canMoveUp(int index) {
        return index > 0;
    }

    private boolean canMoveDown(int index) {
        return index >= 0 && index < archiveGroups.size() - 1;
    }

    private int getListLeft() {
        return Math.max(14, this.width / 2 - 235);
    }

    private int getListRight() {
        return Math.min(this.width - 14, this.width / 2 + 235);
    }

    private int getListTop() {
        return 54;
    }

    private int getListBottom() {
        return Math.max(getListTop() + 96, this.height - 42);
    }

    private int getMaxScroll() {
        return Math.max(0, totalContentHeight() - (getListBottom() - getListTop()));
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(getMaxScroll(), scrollOffset));
    }

    private void ensureSelectionVisible() {
        if (selectedIndex < 0) {
            return;
        }
        int right = getListRight() - SCROLLBAR_WIDTH - 4;
        Integer rowTop = rowTop(selectedIndex, right);
        if (rowTop == null) {
            return;
        }
        if (rowTop < getListTop()) {
            scrollOffset = rowContentTop(selectedIndex, right);
        } else {
            int rowHeight = rowHeight(archiveGroups.get(selectedIndex), right);
            if (rowTop + rowHeight > getListBottom()) {
                scrollOffset = rowContentTop(selectedIndex, right) + rowHeight - (getListBottom() - getListTop());
            }
        }
        clampScroll();
    }

    private boolean isInsideScrollbar(double mouseX, double mouseY) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return false;
        }
        int x = getListRight() - SCROLLBAR_WIDTH;
        return mouseX >= x && mouseX <= x + SCROLLBAR_WIDTH && mouseY >= getListTop() && mouseY <= getListBottom();
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int top = getListTop();
        int track = Math.max(1, getListBottom() - top);
        int barHeight = scrollBarHeight(track);
        int travel = Math.max(1, track - barHeight);
        double ratio = Math.max(0.0, Math.min(1.0, (mouseY - top - scrollbarDragOffset) / travel));
        scrollOffset = Math.max(0, Math.min(maxScroll, (int) Math.round(ratio * maxScroll)));
    }

    private double scrollbarDragOffset(double mouseY) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return 0.0;
        }
        int top = getListTop();
        int track = Math.max(1, getListBottom() - top);
        int barHeight = scrollBarHeight(track);
        int travel = Math.max(1, track - barHeight);
        int barY = top + (int) Math.round(travel * (scrollOffset / (double) maxScroll));
        if (mouseY >= barY && mouseY <= barY + barHeight) {
            return mouseY - barY;
        }
        return barHeight / 2.0;
    }

    private void drawScrollBar(GuiGraphics guiGraphics) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int top = getListTop();
        int bottom = getListBottom();
        int trackHeight = Math.max(1, bottom - top);
        int barHeight = scrollBarHeight(trackHeight);
        int travel = Math.max(1, trackHeight - barHeight);
        int barY = top + (int) Math.round(travel * (scrollOffset / (double) maxScroll));
        int x = getListRight() - SCROLLBAR_WIDTH;
        guiGraphics.fill(x, top, x + SCROLLBAR_WIDTH, bottom, 0x663A3F48);
        guiGraphics.fill(x, barY, x + SCROLLBAR_WIDTH, barY + barHeight, 0xFF8A95A8);
    }

    private int scrollBarHeight(int trackHeight) {
        int totalHeight = Math.max(trackHeight, totalContentHeight());
        return Math.min(trackHeight, Math.max(18, trackHeight * trackHeight / Math.max(1, totalHeight)));
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height, float progress) {
        int filled = progress >= 0f ? Math.round(width * Math.max(0f, Math.min(1f, progress))) : 0;
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B);
        if (progress < 0f) {
            int pulse = (int) ((Util.getMillis() / 12L) % width);
            guiGraphics.fill(x + Math.min(pulse, width - 24), y, x + Math.min(pulse + 24, width), y + height, 0xFF66CCFF);
        } else {
            guiGraphics.fill(x, y, x + filled, y + height, 0xFF66CCFF);
        }
    }

    private void updateOperationProgress(Component message, float progress) {
        updateOperationProgress(message, progress, null);
    }

    private void updateOperationProgress(Component message, float progress, Component detail) {
        operationStatus = message;
        operationProgress = progress;
        operationDetail = detail;
    }

    private Component operationDetailFor(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || "dlc_manager.pack.selecting_source".equals(value)) {
            return null;
        }
        if (value.startsWith("Downloading ")) {
            return Component.translatable("dlc_manager.pack.downloading_file", value.substring("Downloading ".length()));
        }
        if (value.startsWith("Resolved ")) {
            return Component.translatable("dlc_manager.pack.resolved_file", value.substring("Resolved ".length()));
        }
        if (value.startsWith("Installing ")) {
            return Component.translatable("dlc_manager.pack.installing", value.substring("Installing ".length()));
        }
        if (value.startsWith("Uninstalling installed DLC items")) {
            return Component.translatable("dlc_manager.pack.operation_uninstalling");
        }
        if (value.startsWith("Uninstalling ")) {
            return Component.translatable("dlc_manager.pack.uninstalling", value.substring("Uninstalling ".length()));
        }
        if (value.startsWith("Restoring ")) {
            return Component.translatable("dlc_manager.pack.restoring_file", value.substring("Restoring ".length()));
        }
        if (value.startsWith("Preparing ")) {
            return Component.translatable("dlc_manager.pack.preparing_pack", value.substring("Preparing ".length()));
        }
        if (value.startsWith("Backing up ")) {
            return Component.translatable("dlc_manager.pack.backing_up_pack", value.substring("Backing up ".length()));
        }
        if ("Applying runtime changes".equals(value)) {
            return Component.translatable("dlc_manager.pack.applying_runtime");
        }
        if ("Writing metadata".equals(value)) {
            return Component.translatable("dlc_manager.pack.writing_metadata");
        }
        if (value.startsWith("Installed ")) {
            return Component.translatable("dlc_manager.pack.installed_pack", value.substring("Installed ".length()));
        }
        if (value.startsWith("Uninstalled ")) {
            return Component.translatable("dlc_manager.pack.uninstalled_pack", value.substring("Uninstalled ".length()));
        }
        if (value.startsWith("dlc_manager.")) {
            return Component.translatable(value);
        }
        return DlcTextFormatter.parse(value);
    }

    private FormattedCharSequence trimToWidth(Component text, int width) {
        if (this.font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        List<FormattedCharSequence> lines = this.font.split(text, Math.max(1, width));
        return lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.get(0);
    }

    private int rowHeight(PackArchiveGroup group, int right) {
        PackArchiveEntry entry = group.displayEntry();
        int textX = getListLeft() + ORDER_BUTTON_WIDTH + 12 + ICON_SIZE + 8;
        int textRight = right - BUTTON_WIDTH - 18;
        int textWidth = Math.max(1, textRight - textX);
        Component homepagePrompt = homepagePrompt(entry);
        int homepageWidth = homepagePrompt == null ? 0 : Math.min(Math.round(this.font.width(homepagePrompt) * HOMEPAGE_PROMPT_SCALE), Math.max(1, textWidth / 2));
        int nameWidth = Math.max(1, textWidth - (homepagePrompt == null ? 0 : homepageWidth + HOMEPAGE_PROMPT_GAP));
        int nameLines = Math.max(1, this.font.split(entry.displayName, nameWidth).size());
        int authorLines = authorLines(entry, textWidth).size();
        int descriptionLines = Math.max(1, this.font.split(entry.description, textWidth).size());
        int contentHeight = ROW_PADDING_TOP + nameLines * 10 + 4 + authorLines * 10 + 4 + VERSION_LINE_HEIGHT + 4 + descriptionLines * 10;
        List<RowAction> actions = actionButtons(group);
        int buttonHeight = Math.max(BUTTON_HEIGHT, actions.size() * BUTTON_HEIGHT + (actions.size() - 1) * BUTTON_GAP);
        int iconHeight = ROW_PADDING_TOP + ICON_SIZE;
        return Math.max(ROW_HEIGHT, Math.max(contentHeight, Math.max(buttonHeight, iconHeight)) + ROW_PADDING_BOTTOM);
    }

    private Component homepagePrompt(PackArchiveEntry entry) {
        if (entry.metadata.links.get("homepage") == null || entry.metadata.links.get("homepage").isBlank()) {
            return null;
        }
        return Component.translatable("dlc_manager.pack.double_click_homepage");
    }

    private List<List<Component>> authorLines(PackArchiveEntry entry, int width) {
        Component prefix = Component.translatable("dlc_manager.pack.author_prefix");
        Component separator = Component.literal(", ");
        List<List<Component>> lines = new ArrayList<>();
        List<Component> current = new ArrayList<>();
        int currentWidth = 0;
        current.add(prefix);
        currentWidth += this.font.width(prefix);
        for (int i = 0; i < entry.authorNames.size(); i++) {
            Component author = DlcTextFormatter.parse(entry.authorNames.get(i));
            int groupWidth = this.font.width(author) + (i > 0 ? this.font.width(separator) : 0);
            if (!current.isEmpty() && currentWidth + groupWidth > width) {
                lines.add(current);
                current = new ArrayList<>();
                currentWidth = 0;
            }
            if (i > 0) {
                current.add(separator);
                currentWidth += this.font.width(separator);
            }
            current.add(author);
            currentWidth += this.font.width(author);
        }
        if (!current.isEmpty()) {
            lines.add(current);
        }
        if (lines.isEmpty()) {
            lines.add(List.of(prefix));
        }
        return lines;
    }

    private Integer rowTop(int index, int right) {
        if (index < 0 || index >= archiveGroups.size()) {
            return null;
        }
        int y = getListTop() - scrollOffset + rowContentTop(index, right);
        if (y >= getListBottom() || y + rowHeight(archiveGroups.get(index), right) <= getListTop()) {
            return null;
        }
        return y;
    }

    private int entryAt(double mouseY) {
        int top = getListTop();
        int bottom = getListBottom();
        if (mouseY < top || mouseY >= bottom) {
            return -1;
        }
        int y = top - scrollOffset;
        int index = 0;
        int right = getListRight() - SCROLLBAR_WIDTH - 4;
        while (index < archiveGroups.size() && y < bottom) {
            int rowHeight = rowHeight(archiveGroups.get(index), right);
            int rowBottom = y + rowHeight;
            if (mouseY >= y && mouseY < rowBottom) {
                return index;
            }
            y = rowBottom;
            index++;
        }
        return -1;
    }

    private int rowContentTop(int index, int right) {
        int y = 0;
        for (int i = 0; i < index; i++) {
            y += rowHeight(archiveGroups.get(i), right);
        }
        return y;
    }

    private int totalContentHeight() {
        int right = getListRight() - SCROLLBAR_WIDTH - 4;
        int total = 0;
        for (PackArchiveGroup group : archiveGroups) {
            total += rowHeight(group, right);
        }
        return total;
    }

    private void rebuildPackWidgets() {
        init(this.minecraft, this.width, this.height);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !busy || paused;
    }

    @Override
    public void onClose() {
        if (paused) {
            returnToParent();
        } else if (!busy) {
            onBackOrCancel();
        }
    }

    private static final class IconTexture {
        private final ResourceLocation location;
        private final int width;
        private final int height;

        private IconTexture(ResourceLocation location, int width, int height) {
            this.location = location;
            this.width = width;
            this.height = height;
        }
    }

    private static final class PackArchiveEntry {
        private final Path archivePath;
        private final String archiveOrderPath;
        private final DlcPackMetadata metadata;
        private final Component displayName;
        private final String displayNameText;
        private final Component description;
        private final Component author;
        private final List<String> authorNames;
        private final ResourceLocation iconTexture;
        private final int iconWidth;
        private final int iconHeight;
        private final String key;

        private PackArchiveEntry(Path archivePath, String archiveOrderPath, DlcPackMetadata metadata, Component displayName, Component description, Component author, List<String> authorNames, ResourceLocation iconTexture, int iconWidth, int iconHeight) {
            this.archivePath = archivePath;
            this.archiveOrderPath = archiveOrderPath;
            this.metadata = metadata;
            this.displayName = displayName == null ? Component.literal(metadata.identifier) : displayName;
            String rawDisplayName = this.displayName.getString();
            this.displayNameText = rawDisplayName.isBlank() ? metadata.identifier : rawDisplayName;
            this.description = description == null ? Component.empty() : description;
            this.author = author == null ? Component.empty() : author;
            this.authorNames = authorNames == null || authorNames.isEmpty() ? List.of(this.author.getString()) : List.copyOf(authorNames);
            this.iconTexture = iconTexture;
            this.iconWidth = iconWidth;
            this.iconHeight = iconHeight;
            this.key = archiveOrderPath + "#" + metadata.identifier + "@" + metadata.version;
        }
    }

    private static final class PackArchiveGroup {
        private final String identifier;
        private final List<PackArchiveEntry> versions;
        private final String orderKey;
        private final String selectedVersionKey;

        private PackArchiveGroup(String identifier, List<PackArchiveEntry> versions, String orderKey, String selectedVersionKey) {
            this.identifier = identifier;
            this.versions = List.copyOf(versions);
            this.orderKey = orderKey;
            this.selectedVersionKey = selectedVersionKey;
        }

        private PackArchiveEntry displayEntry() {
            for (PackArchiveEntry entry : versions) {
                if (entry.key.equals(selectedVersionKey)) {
                    return entry;
                }
            }
            return versions.get(0);
        }

        private PackArchiveGroup withSelectedVersionKey(String selectedVersionKey) {
            return new PackArchiveGroup(identifier, versions, orderKey, selectedVersionKey);
        }
    }

    private static final class VersionDropdownBounds {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private VersionDropdownBounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private enum OperationDisplayMode {
        APPLYING,
        INSTALLING,
        UNINSTALLING
    }

    private enum RowAction {
        MOVE_UP,
        MOVE_DOWN,
        INSTALL,
        DELETE,
        FEEDBACK,
        SUPPORT
    }

    private enum BusyOperation {
        NONE,
        APPLY,
        IMPORT,
        DELETE
    }

}
