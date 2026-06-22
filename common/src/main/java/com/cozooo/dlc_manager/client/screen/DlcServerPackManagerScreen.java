package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.core.DlcFilePicker;
import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.client.pack.DlcPackMetadata;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerPackInfo;
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

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DlcServerPackManagerScreen extends DlcScreenCompat.MouseScroll {
    private static final int TIMEOUT_MS = 8000;
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
    private final DlcServerEntry entry;
    private Button importButton;
    private Button urlButton;
    private Button applyButton;
    private Button backButton;
    private Button refreshButton;
    private final Map<String, PackEntry> pendingInstalls = new LinkedHashMap<>();
    private final Set<String> pendingUninstalls = new LinkedHashSet<>();
    private List<PackGroup> archiveGroups = List.of();
    private List<String> entryOrder = List.of();
    private List<String> currentOrder = List.of();
    private final Map<String, String> selectedVersionByIdentifier = new LinkedHashMap<>();
    private int scrollOffset;
    private boolean draggingScrollbar;
    private double scrollbarDragOffset;
    private String versionDropdownIdentifier;
    private Component status;
    private String lastClickKey;
    private long lastClickAt;
    private volatile boolean loading;
    private volatile boolean working;
    private int iconTextureSerial;

    public DlcServerPackManagerScreen(Screen parent, DlcServerEntry entry) {
        super(Component.translatable("dlc_manager.server.pack.manager_title"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        if (!working) {
            refresh();
        }
        rebuildButtons();
    }

    public void refresh() {
        refresh(null);
    }

    public void refresh(Component finalStatus) {
        loading = true;
        status = Component.translatable("dlc_manager.server.files_loading");
        new Thread(() -> {
            DlcServerConnection.PackListResponse result;
            try {
                result = DlcServerConnection.fetchPackList(entry, TIMEOUT_MS);
            } catch (Exception ex) {
                result = null;
            }
            DlcServerConnection.PackListResponse finalResult = result;
            Minecraft.getInstance().execute(() -> {
                loading = false;
                if (finalResult == null) {
                    releaseIconTextures();
                    archiveGroups = List.of();
                    entryOrder = List.of();
                    currentOrder = List.of();
                    status = Component.translatable("dlc_manager.server.pack.load_failed");
                } else {
                    rebuildEntries(finalResult.packs, finalResult.order);
                    int count = 0;
                    for (PackGroup group : archiveGroups) {
                        count += group.versions.size();
                    }
                    status = finalStatus == null ? Component.translatable("dlc_manager.pack.archive_count", count) : finalStatus;
                }
                rebuildButtons();
            });
        }, "dlc-server-pack-refresh").start();
    }

    private void rebuildButtons() {
        clearWidgets();
        int buttonWidth = 104;
        int buttonHeight = 20;
        int gap = 8;
        int total = buttonWidth * 4 + gap * 3;
        int startX = this.width / 2 - total / 2;
        int y = this.height - 28;
        importButton = Button.builder(Component.translatable("dlc_manager.pack.import"), btn -> importPack())
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build();
        urlButton = Button.builder(Component.translatable("dlc_manager.pack.import_url"), btn -> Minecraft.getInstance().setScreen(new DlcServerPackUrlScreen(this, entry)))
            .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
            .build();
        applyButton = Button.builder(Component.translatable("dlc_manager.pack.apply"), btn -> applyPendingChanges())
            .bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, buttonHeight)
            .build();
        Component backLabel = hasPendingChanges() ? Component.translatable("dlc_manager.pack.cancel_changes") : Component.translatable("dlc_manager.pack.back");
        backButton = Button.builder(backLabel, btn -> onBackOrCancel())
            .bounds(startX + (buttonWidth + gap) * 3, y, buttonWidth, buttonHeight)
            .build();
        refreshButton = Button.builder(Component.translatable("dlc_manager.server.files_refresh"), btn -> refresh())
            .bounds(this.width - 24 - 70, 8, 70, 20)
            .build();
        addRenderableWidget(importButton);
        addRenderableWidget(urlButton);
        addRenderableWidget(applyButton);
        addRenderableWidget(backButton);
        addRenderableWidget(refreshButton);
        updateButtonState();
    }

    private void updateButtonState() {
        boolean active = !loading && !working;
        if (importButton != null) importButton.active = active;
        if (urlButton != null) urlButton.active = active;
        if (applyButton != null) applyButton.active = active && hasPendingChanges();
        if (backButton != null) backButton.active = !working;
        if (refreshButton != null) refreshButton.active = active && !hasPendingChanges();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, getTitle(), this.width / 2, 12, 0xFFFFFF);
        renderPackList(guiGraphics, mouseX, mouseY);
        if (status != null) {
            guiGraphics.drawCenteredString(this.font, status, this.width / 2, this.height - 52, working ? 0xFFE6E6 : 0xC8C8C8);
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
        renderVersionDropdown(guiGraphics, mouseX, mouseY);
    }

    private void renderPackList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = getListLeft();
        int right = getListRight();
        int top = getListTop();
        int bottom = getListBottom();
        guiGraphics.fill(left, top - 4, right, bottom + 4, 0x66000000);
        if (archiveGroups.isEmpty() && !loading) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("dlc_manager.pack.none_archived"), this.width / 2, top + 24, 0xC8C8C8);
            return;
        }
        int contentRight = right - SCROLLBAR_WIDTH - 4;
        guiGraphics.enableScissor(left, top, right, bottom);
        int y = top - scrollOffset;
        for (int i = 0; i < archiveGroups.size(); i++) {
            PackGroup group = archiveGroups.get(i);
            int rowHeight = rowHeight(group, contentRight);
            if (y + rowHeight >= top && y <= bottom) {
                renderEntry(guiGraphics, group, i, left + 4, contentRight, y, rowHeight, mouseX, mouseY);
            }
            y += rowHeight;
        }
        guiGraphics.disableScissor();
        drawScrollBar(guiGraphics);
    }

    private void renderEntry(GuiGraphics guiGraphics, PackGroup group, int index, int left, int right, int y, int rowHeight, int mouseX, int mouseY) {
        PackEntry pack = group.displayEntry();
        guiGraphics.fill(left, y, right, y + rowHeight - 4, 0x55202020);
        drawOrderButtons(guiGraphics, index, left + 4, y, rowHeight, mouseX, mouseY);
        int iconX = left + ORDER_BUTTON_WIDTH + 12;
        int iconY = y + ROW_PADDING_TOP;
        if (pack.iconTexture != null) {
            guiGraphics.blit(pack.iconTexture.location, iconX, iconY, ICON_SIZE, ICON_SIZE, 0.0F, 0.0F, pack.iconTexture.width, pack.iconTexture.height, pack.iconTexture.width, pack.iconTexture.height);
        } else {
            guiGraphics.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0xFF3B3B3B);
            String initial = pack.displayNameText.isBlank() ? "P" : pack.displayNameText.substring(0, 1).toUpperCase(Locale.ROOT);
            guiGraphics.drawCenteredString(this.font, Component.literal(initial), iconX + ICON_SIZE / 2, iconY + 12, 0xFFFFFF);
        }
        int textX = iconX + ICON_SIZE + 8;
        int textRight = right - BUTTON_WIDTH - 18;
        Component homepagePrompt = homepagePrompt(pack);
        int homepageWidth = homepagePrompt == null ? 0 : Math.min(Math.round(this.font.width(homepagePrompt) * HOMEPAGE_PROMPT_SCALE), Math.max(1, (textRight - textX) / 2));
        int nameWidth = Math.max(1, textRight - textX - (homepagePrompt == null ? 0 : homepageWidth + HOMEPAGE_PROMPT_GAP));
        int textY = y + ROW_PADDING_TOP;
        textY = drawMultiline(guiGraphics, pack.displayName, textX, textY, nameWidth, 0xFFFFFF) + 4;
        if (homepagePrompt != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(HOMEPAGE_PROMPT_SCALE, HOMEPAGE_PROMPT_SCALE, 1.0F);
            int promptX = textRight - homepageWidth;
            int promptY = y + ROW_PADDING_TOP + 1;
            guiGraphics.drawString(this.font, homepagePrompt, Math.round(promptX / HOMEPAGE_PROMPT_SCALE), Math.round(promptY / HOMEPAGE_PROMPT_SCALE), 0x8FA8C8, false);
            guiGraphics.pose().popPose();
        }
        textY = drawAuthorLines(guiGraphics, pack, textX, textY, textRight - textX, 0xC8C8C8) + 4;
        drawVersionLine(guiGraphics, group, textX, textY, textRight - textX, mouseX, mouseY);
        drawMultiline(guiGraphics, pack.description, textX, textY + VERSION_LINE_HEIGHT, textRight - textX, 0xB0B0B0);
        drawActionButtons(guiGraphics, group, right - BUTTON_WIDTH - 6, y, rowHeight, mouseX, mouseY);
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

    private void drawActionButtons(GuiGraphics guiGraphics, PackGroup group, int x, int rowY, int rowHeight, int mouseX, int mouseY) {
        List<RowAction> actions = actionButtons(group);
        int startY = centeredButtonColumnY(rowY, rowHeight, actions.size());
        for (int i = 0; i < actions.size(); i++) {
            drawSizedButton(guiGraphics, x, startY + i * (BUTTON_HEIGHT + BUTTON_GAP), BUTTON_WIDTH, actionLabel(group, actions.get(i)), mouseX, mouseY);
        }
    }

    private void drawVersionLine(GuiGraphics guiGraphics, PackGroup group, int x, int y, int width, int mouseX, int mouseY) {
        Component prefix = Component.translatable("dlc_manager.pack.version_prefix");
        int valueX = x + this.font.width(prefix) + VERSION_VALUE_GAP;
        guiGraphics.drawString(this.font, prefix, x, y + 4, 0xAFC8FF, false);
        if (group.versions.size() <= 1) {
            guiGraphics.drawString(this.font, Component.literal(group.displayEntry().version), valueX, y + 4, 0xAFC8FF, false);
            return;
        }
        drawSizedButton(guiGraphics, valueX - VERSION_BUTTON_HORIZONTAL_PADDING, y, versionButtonWidth(group), versionButtonLabel(group), mouseX, mouseY, VERSION_BUTTON_TEXT_COLOR);
    }

    private void drawSizedButton(GuiGraphics guiGraphics, int x, int y, int width, Component label, int mouseX, int mouseY) {
        drawSizedButton(guiGraphics, x, y, width, label, mouseX, mouseY, 0xFFFFFF);
    }

    private void drawSizedButton(GuiGraphics guiGraphics, int x, int y, int width, Component label, int mouseX, int mouseY, int textColor) {
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
        guiGraphics.fill(x, y, x + width, y + BUTTON_HEIGHT, hover ? 0xFF5A5A5A : 0xFF404040);
        guiGraphics.drawCenteredString(this.font, label, x + width / 2, y + 4, textColor);
    }

    private int drawMultiline(GuiGraphics guiGraphics, Component text, int x, int y, int width, int color) {
        var lines = this.font.split(text, Math.max(1, width));
        for (int i = 0; i < lines.size(); i++) {
            guiGraphics.drawString(this.font, lines.get(i), x, y + i * 10, color, false);
        }
        return y + lines.size() * 10;
    }

    private int drawAuthorLines(GuiGraphics guiGraphics, PackEntry entry, int x, int y, int width, int color) {
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

    private void renderVersionDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        PackGroup group = dropdownGroup();
        if (group == null) return;
        Bounds bounds = versionDropdownBounds(group);
        if (bounds == null) return;
        guiGraphics.fill(bounds.x - 1, bounds.y - 1, bounds.x + bounds.width + 1, bounds.y + bounds.height + 1, 0xFF202020);
        guiGraphics.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 0xFF303030);
        for (int i = 0; i < group.versions.size(); i++) {
            PackEntry pack = group.versions.get(i);
            int itemY = bounds.y + 1 + i * VERSION_DROPDOWN_ITEM_HEIGHT;
            boolean hover = mouseX >= bounds.x && mouseX < bounds.x + bounds.width && mouseY >= itemY && mouseY < itemY + VERSION_DROPDOWN_ITEM_HEIGHT;
            boolean selected = pack.key.equals(group.selectedVersionKey);
            if (hover || selected) {
                guiGraphics.fill(bounds.x + 1, itemY, bounds.x + bounds.width - 1, itemY + VERSION_DROPDOWN_ITEM_HEIGHT, hover ? 0xFF5A5A5A : 0xFF454545);
            }
            guiGraphics.drawString(this.font, versionOptionLabel(pack), bounds.x + VERSION_BUTTON_HORIZONTAL_PADDING, itemY + 5, VERSION_BUTTON_TEXT_COLOR, false);
        }
    }

    @Override
    protected boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (loading || working || archiveGroups.isEmpty() || mouseY < getListTop() || mouseY > getListBottom()) return false;
        int maxScrollValue = getMaxScroll();
        if (maxScrollValue <= 0) return false;
        versionDropdownIdentifier = null;
        scrollOffset = Math.max(0, Math.min(maxScrollValue, scrollOffset + (deltaY > 0 ? -SCROLL_STEP : SCROLL_STEP)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (loading || working) return super.mouseClicked(mouseX, mouseY, button);
        if (handleVersionDropdownClick(mouseX, mouseY)) return true;
        if (versionDropdownIdentifier != null) {
            versionDropdownIdentifier = null;
            return true;
        }
        if (isOnScrollbar(mouseX, mouseY) && getMaxScroll() > 0) {
            draggingScrollbar = true;
            scrollbarDragOffset = mouseY - scrollThumbY();
            return true;
        }
        int index = entryAt(mouseY);
        if (index >= 0) {
            PackGroup group = archiveGroups.get(index);
            Integer rowYValue = rowTop(index, getListRight() - SCROLLBAR_WIDTH - 4);
            if (rowYValue == null) return true;
            int rowY = rowYValue;
            int rowHeight = rowHeight(group, getListRight() - SCROLLBAR_WIDTH - 4);
            RowAction orderAction = orderButtonAt(index, getListLeft() + 8, rowY, rowHeight, mouseX, mouseY);
            if (orderAction == RowAction.MOVE_UP) return moveGroup(index, -1);
            if (orderAction == RowAction.MOVE_DOWN) return moveGroup(index, 1);
            int versionX = versionButtonX(group);
            int versionY = versionButtonY(index);
            if (versionButtonAt(group, versionX, versionY, mouseX, mouseY)) {
                versionDropdownIdentifier = group.identifier.equals(versionDropdownIdentifier) ? null : group.identifier;
                return true;
            }
            RowAction action = actionButtonAt(group, getListRight() - SCROLLBAR_WIDTH - 4 - BUTTON_WIDTH - 6, rowY, rowHeight, mouseX, mouseY);
            if (action == RowAction.INSTALL) {
                toggleInstall(group);
                return true;
            }
            if (action == RowAction.DELETE) {
                confirmDelete(group.displayEntry());
                return true;
            }
            if (action == RowAction.FEEDBACK) {
                confirmOpenLink(group.displayEntry(), "issue");
                return true;
            }
            if (action == RowAction.SUPPORT) {
                confirmOpenLink(group.displayEntry(), "sponsor");
                return true;
            }
            handleEntryDoubleClick(group);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!draggingScrollbar) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        int trackHeight = getListBottom() - getListTop();
        int thumbHeight = scrollThumbHeight();
        int maxScrollValue = getMaxScroll();
        double y = mouseY - scrollbarDragOffset;
        scrollOffset = Math.max(0, Math.min(maxScrollValue, Math.round((float) ((y - getListTop()) * maxScrollValue / Math.max(1, trackHeight - thumbHeight)))));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void importPack() {
        Path zip = DlcFilePicker.pickAnyFile(Component.translatable("dlc_manager.pack.import").getString());
        if (zip == null) return;
        startWork(Component.translatable("dlc_manager.pack.importing"), Component.translatable("dlc_manager.pack.import_staged", zip.getFileName().toString()), false, () -> {
            try {
                boolean ok = DlcServerConnection.importPackPackage(entry, TIMEOUT_MS, zip);
                return ok ? null : Component.translatable("dlc_manager.pack.import_failed", "upload failed");
            } catch (Exception ex) {
                return Component.translatable("dlc_manager.pack.import_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
        });
    }

    private void applyPendingChanges() {
        if (!hasPendingChanges()) return;
        if (pendingInstalls.isEmpty() && pendingUninstalls.isEmpty()) {
            startWork(Component.translatable("dlc_manager.pack.applying"), Component.translatable("dlc_manager.pack.order_applied"), false, () -> {
                try {
                    DlcServerConnection.UpdateResponse orderResponse = DlcServerConnection.savePackOrder(entry, TIMEOUT_MS, currentOrder);
                    if (orderResponse == null || !orderResponse.ok) {
                        return Component.translatable("dlc_manager.pack.apply_failed", orderResponse == null ? "save order failed" : orderResponse.message);
                    }
                    entryOrder = new ArrayList<>(currentOrder);
                    return null;
                } catch (Exception ex) {
                    return Component.translatable("dlc_manager.pack.apply_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                }
            });
            return;
        }
        working = true;
        updateButtonState();
        Minecraft.getInstance().setScreen(new DlcServerPackApplyScreen(
            this,
            entry,
            new ArrayList<>(currentOrder),
            new ArrayList<>(pendingUninstalls),
            pendingInstalls.values().stream().map(pack -> pack.archivePath).toList()
        ));
    }

    public void finishServerPackApply(boolean success, String message) {
        working = false;
        if (success) {
            pendingInstalls.clear();
            pendingUninstalls.clear();
            refresh(Component.translatable("dlc_manager.pack.apply_success"));
        } else {
            status = Component.translatable("dlc_manager.pack.apply_failed", message == null ? "failed" : message);
            rebuildButtons();
        }
    }

    public void showServerRestartPrompt() {
        Minecraft.getInstance().setScreen(new ConfirmScreen(restart -> {
            if (restart) {
                new Thread(() -> {
                    try {
                        DlcServerConnection.restartServer(entry, TIMEOUT_MS);
                    } catch (Exception ignored) {
                    }
                }, "dlc-server-pack-restart").start();
            }
            Minecraft.getInstance().setScreen(this);
        }, Component.translatable("dlc_manager.server.restart_title"), Component.translatable("dlc_manager.server.restart_prompt"),
            Component.translatable("dlc_manager.restart_now"), Component.translatable("dlc_manager.restart_later")));
    }

    private void startWork(Component workingText, WorkAction action) {
        startWork(workingText, Component.translatable("dlc_manager.pack.apply_success"), false, action);
    }

    private void startWork(Component workingText, Component successText, boolean clearPendingOnSuccess, WorkAction action) {
        if (working) return;
        working = true;
        status = workingText;
        updateButtonState();
        new Thread(() -> {
            Component error = action.run();
            Minecraft.getInstance().execute(() -> {
                working = false;
                if (error == null) {
                    if (clearPendingOnSuccess) {
                        pendingInstalls.clear();
                        pendingUninstalls.clear();
                    }
                    refresh(successText);
                } else {
                    status = error;
                    rebuildButtons();
                }
            });
        }, "dlc-server-pack-work").start();
    }

    private void toggleInstall(PackGroup group) {
        PackEntry pack = group.displayEntry();
        if (pendingInstalls.get(pack.identifier) == pack) {
            pendingInstalls.remove(pack.identifier);
            rebuildButtons();
            return;
        }
        if (pendingUninstalls.remove(pack.identifier)) {
            rebuildButtons();
            return;
        }
        if (isEntryCurrentlyInstalled(pack)) {
            pendingUninstalls.add(pack.identifier);
        } else {
            pendingInstalls.put(pack.identifier, pack);
            pendingUninstalls.remove(pack.identifier);
        }
        rebuildButtons();
    }

    private void confirmDelete(PackEntry pack) {
        Component prompt = isIdentifierInstalled(pack.identifier)
            ? Component.translatable("dlc_manager.pack.delete_confirm_uninstall", pack.displayNameText)
            : Component.translatable("dlc_manager.pack.delete_confirm", pack.displayNameText);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (accepted) {
                Component successText = isIdentifierInstalled(pack.identifier)
                    ? Component.translatable("dlc_manager.pack.delete_uninstalled_success", pack.displayNameText)
                    : Component.translatable("dlc_manager.pack.delete_success", pack.displayNameText);
                startWork(Component.translatable("dlc_manager.pack.delete"), successText, false, () -> {
                    try {
                        DlcServerConnection.UpdateResponse response = DlcServerConnection.deletePack(entry, TIMEOUT_MS, pack.archivePath);
                        return response == null || !response.ok
                            ? Component.translatable("dlc_manager.pack.delete_failed", response == null ? pack.displayNameText : response.message)
                            : null;
                    } catch (Exception ex) {
                        return Component.translatable("dlc_manager.pack.delete_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                    }
                });
            }
        }, Component.translatable("dlc_manager.pack.delete_confirm_title"), prompt, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void confirmOpenLink(PackEntry pack, String key) {
        String url = pack.links.get(key);
        if (url == null || url.isBlank()) return;
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(this);
            if (accepted) {
                Util.getPlatform().openUri(url);
            }
        }, Component.translatable("dlc_manager.pack.open_link_title"), Component.translatable("dlc_manager.pack.open_link_prompt", url), DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()));
    }

    private void handleEntryDoubleClick(PackGroup group) {
        PackEntry pack = group.displayEntry();
        if (pack.links.get("homepage") == null || pack.links.get("homepage").isBlank()) return;
        long now = Util.getMillis();
        if (pack.key.equals(lastClickKey) && now - lastClickAt <= 400L) {
            confirmOpenLink(pack, "homepage");
            lastClickKey = null;
            lastClickAt = 0L;
        } else {
            lastClickKey = pack.key;
            lastClickAt = now;
        }
    }

    private boolean moveGroup(int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= archiveGroups.size()) return true;
        List<PackGroup> groups = new ArrayList<>(archiveGroups);
        PackGroup moved = groups.remove(index);
        groups.add(target, moved);
        archiveGroups = groups;
        currentOrder = groups.stream().map(group -> group.orderKey).toList();
        saveOrderLive(currentOrder);
        clampScroll();
        rebuildButtons();
        return true;
    }

    private void saveOrderLive(List<String> order) {
        new Thread(() -> {
            try {
                DlcServerConnection.savePackOrder(entry, TIMEOUT_MS, order);
            } catch (Exception ignored) {
            }
        }, "dlc-server-pack-order").start();
    }

    private void onBackOrCancel() {
        if (hasPendingChanges()) {
            pendingInstalls.clear();
            pendingUninstalls.clear();
            currentOrder = List.copyOf(entryOrder);
            saveOrderLive(entryOrder);
            sortGroupsByCurrentOrder();
            status = Component.translatable("dlc_manager.pack.changes_cancelled");
            rebuildButtons();
            return;
        }
        returnToParent();
    }

    private void returnToParent() {
        releaseIconTextures();
        Minecraft.getInstance().setScreen(parent);
    }

    private void rebuildEntries(List<DlcServerPackInfo> packs, List<String> order) {
        Map<String, String> pendingInstallKeys = new HashMap<>();
        for (Map.Entry<String, PackEntry> entry : pendingInstalls.entrySet()) {
            pendingInstallKeys.put(entry.getKey(), entry.getValue().key);
        }
        releaseIconTextures();
        List<PackEntry> entries = new ArrayList<>();
        if (packs != null) {
            for (DlcServerPackInfo info : packs) {
                PackEntry entry = toEntry(info);
                if (!entry.identifier.isBlank() && !entry.version.isBlank() && !entry.archivePath.isBlank()) {
                    entries.add(entry);
                }
            }
        }
        List<PackGroup> groups = groupEntries(entries);
        entryOrder = normalizeGroupOrder(order, groups);
        currentOrder = List.copyOf(entryOrder);
        archiveGroups = groups;
        normalizeSelections();
        sortGroupsByCurrentOrder();
        pendingInstalls.clear();
        for (PackGroup group : archiveGroups) {
            PackEntry pack = group.displayEntry();
            if (!isEntryCurrentlyInstalled(pack) && pack.key.equals(pendingInstallKeys.get(pack.identifier))) {
                pendingInstalls.put(pack.identifier, pack);
            }
        }
        pendingInstalls.keySet().removeIf(identifier -> archiveGroups.stream().noneMatch(group -> group.identifier.equals(identifier)));
        pendingUninstalls.removeIf(identifier -> archiveGroups.stream().noneMatch(group -> group.identifier.equals(identifier)));
        clampScroll();
    }

    private PackEntry toEntry(DlcServerPackInfo info) {
        String identifier = info.identifier == null ? "" : info.identifier;
        String version = info.version == null ? "" : info.version;
        String archivePath = info.archivePath == null ? "" : info.archivePath;
        String orderKey = info.orderKey != null && !info.orderKey.isBlank() ? info.orderKey : archivePath + "#" + identifier + "@" + version;
        Component displayName = DlcTextFormatter.parse(info.displayName != null && !info.displayName.isBlank() ? info.displayName : identifier);
        Component description = DlcMarkdownRenderer.renderMarkdownComponent(info.description);
        List<String> authors = info.authors == null ? List.of() : info.authors.stream().filter(value -> value != null && !value.isBlank()).toList();
        if (authors.isEmpty()) {
            authors = List.of(Component.translatable("dlc_manager.pack.unknown_author").getString());
        }
        return new PackEntry(archivePath, orderKey, identifier, version, displayName, description, authors, info.links == null ? Map.of() : info.links,
            info.installed, info.installedVersion == null ? "" : info.installedVersion, loadIconTexture(info.iconBase64));
    }

    private List<PackGroup> groupEntries(List<PackEntry> entries) {
        Map<String, List<PackEntry>> byIdentifier = new LinkedHashMap<>();
        Map<String, List<PackEntry>> mutable = new LinkedHashMap<>();
        for (PackEntry entry : entries) {
            mutable.computeIfAbsent(entry.identifier, key -> new ArrayList<>()).add(entry);
        }
        byIdentifier.putAll(mutable);
        List<PackGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<PackEntry>> mapEntry : byIdentifier.entrySet()) {
            List<PackEntry> versions = new ArrayList<>(mapEntry.getValue());
            versions.sort((a, b) -> {
                int compare = DlcPackMetadata.compareVersionStrings(b.version, a.version);
                return compare != 0 ? compare : a.archivePath.compareToIgnoreCase(b.archivePath);
            });
            PackEntry installed = versions.stream().filter(pack -> pack.installed).findFirst().orElse(null);
            PackEntry orderEntry = installed == null ? versions.get(0) : installed;
            String selected = selectedVersionByIdentifier.get(mapEntry.getKey());
            PackEntry display = versions.stream().filter(pack -> pack.key.equals(selected) || pack.version.equals(selected)).findFirst().orElse(orderEntry);
            groups.add(new PackGroup(mapEntry.getKey(), versions, orderEntry.key, display.key));
        }
        return groups;
    }

    private void normalizeSelections() {
        Set<String> valid = new HashSet<>();
        for (PackGroup group : archiveGroups) {
            valid.add(group.identifier);
        }
        selectedVersionByIdentifier.keySet().removeIf(key -> !valid.contains(key));
        List<PackGroup> out = new ArrayList<>();
        for (PackGroup group : archiveGroups) {
            String selected = selectedVersionByIdentifier.get(group.identifier);
            PackEntry display = group.versions.stream().filter(pack -> pack.key.equals(selected) || pack.version.equals(selected)).findFirst().orElse(group.displayEntry());
            selectedVersionByIdentifier.put(group.identifier, display.key);
            out.add(new PackGroup(group.identifier, group.versions, group.orderKey, display.key));
        }
        archiveGroups = out;
    }

    private List<String> normalizeGroupOrder(List<String> savedOrder, List<PackGroup> groups) {
        List<String> out = new ArrayList<>();
        if (savedOrder != null) {
            for (String key : savedOrder) {
                String identifier = identifierFromOrderKey(key);
                if (identifier == null) continue;
                PackGroup group = groups.stream().filter(candidate -> candidate.identifier.equals(identifier)).findFirst().orElse(null);
                if (group != null && !out.contains(group.orderKey)) {
                    out.add(group.orderKey);
                }
            }
        }
        for (PackGroup group : groups) {
            if (!out.contains(group.orderKey)) {
                out.add(group.orderKey);
            }
        }
        return out;
    }

    private void sortGroupsByCurrentOrder() {
        archiveGroups = archiveGroups.stream()
            .sorted(Comparator.comparingInt(group -> {
                int index = currentOrder.indexOf(group.orderKey);
                return index < 0 ? Integer.MAX_VALUE : index;
            }))
            .toList();
    }

    private String identifierFromOrderKey(String key) {
        if (key == null || key.isBlank()) return null;
        int hash = key.lastIndexOf('#');
        int at = key.lastIndexOf('@');
        if (hash < 0 || at <= hash + 1) return null;
        return key.substring(hash + 1, at);
    }

    private IconTexture loadIconTexture(String base64) {
        if (base64 == null || base64.isBlank()) return null;
        NativeImage image = null;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
                image = NativeImage.read(input);
            }
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            DynamicTexture texture = new DynamicTexture(image);
            image = null;
            ResourceLocation location = Minecraft.getInstance().getTextureManager().register("dlc_server_pack_icon/" + (++iconTextureSerial), texture);
            return new IconTexture(location, imageWidth, imageHeight);
        } catch (Exception ignored) {
            if (image != null) image.close();
            return null;
        }
    }

    private void releaseIconTextures() {
        for (PackGroup group : archiveGroups) {
            for (PackEntry pack : group.versions) {
                if (pack.iconTexture != null) {
                    Minecraft.getInstance().getTextureManager().release(pack.iconTexture.location);
                }
            }
        }
    }

    private boolean hasPendingChanges() {
        return !pendingInstalls.isEmpty() || !pendingUninstalls.isEmpty() || !currentOrder.equals(entryOrder);
    }

    private boolean isEntryCurrentlyInstalled(PackEntry entry) {
        return entry.installed && entry.installedVersion.equals(entry.version);
    }

    private boolean isIdentifierInstalled(String identifier) {
        return archiveGroups.stream().anyMatch(group -> group.identifier.equals(identifier) && group.versions.stream().anyMatch(pack -> pack.installed));
    }

    private Component installButtonLabel(PackGroup group) {
        PackEntry pack = group.displayEntry();
        PackEntry installed = group.versions.stream().filter(candidate -> candidate.installed).findFirst().orElse(null);
        int compare = installed == null ? 0 : DlcPackMetadata.compareVersionStrings(pack.version, installed.installedVersion.isBlank() ? installed.version : installed.installedVersion);
        if (pendingInstalls.get(pack.identifier) == pack) {
            if (installed == null) return Component.translatable("dlc_manager.pack.pending_install");
            if (compare > 0) return Component.translatable("dlc_manager.pack.pending_upgrade");
            if (compare < 0) return Component.translatable("dlc_manager.pack.pending_downgrade");
            return Component.translatable("dlc_manager.pack.pending_install");
        }
        if (pendingUninstalls.contains(pack.identifier)) return Component.translatable("dlc_manager.pack.pending_uninstall");
        if (installed != null && compare == 0) return Component.translatable("dlc_manager.pack.uninstall");
        if (installed != null) return compare > 0 ? Component.translatable("dlc_manager.pack.upgrade") : Component.translatable("dlc_manager.pack.downgrade");
        return Component.translatable("dlc_manager.pack.install");
    }

    private List<RowAction> actionButtons(PackGroup group) {
        PackEntry pack = group.displayEntry();
        List<RowAction> out = new ArrayList<>();
        out.add(RowAction.INSTALL);
        out.add(RowAction.DELETE);
        String issue = pack.links.get("issue");
        if (issue != null && !issue.isBlank()) out.add(RowAction.FEEDBACK);
        String sponsor = pack.links.get("sponsor");
        if (sponsor != null && !sponsor.isBlank()) out.add(RowAction.SUPPORT);
        return out;
    }

    private Component actionLabel(PackGroup group, RowAction action) {
        return switch (action) {
            case INSTALL -> installButtonLabel(group);
            case DELETE -> Component.translatable("dlc_manager.pack.delete");
            case FEEDBACK -> Component.translatable("dlc_manager.pack.feedback");
            case SUPPORT -> Component.translatable("dlc_manager.pack.support_author");
            case MOVE_UP -> Component.literal("▲");
            case MOVE_DOWN -> Component.literal("▼");
        };
    }

    private List<List<Component>> authorLines(PackEntry entry, int width) {
        Component prefix = Component.translatable("dlc_manager.pack.author_prefix");
        Component separator = Component.literal(", ");
        List<List<Component>> lines = new ArrayList<>();
        List<Component> current = new ArrayList<>();
        int currentWidth = 0;
        current.add(prefix);
        currentWidth += this.font.width(prefix);
        for (int i = 0; i < entry.authorNames.size(); i++) {
            Component author = DlcTextFormatter.parse(entry.authorNames.get(i));
            int partWidth = this.font.width(author) + (i > 0 ? this.font.width(separator) : 0);
            if (!current.isEmpty() && currentWidth + partWidth > width) {
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
        if (!current.isEmpty()) lines.add(current);
        if (lines.isEmpty()) lines.add(List.of(prefix));
        return lines;
    }

    private int rowHeight(PackGroup group, int right) {
        PackEntry pack = group.displayEntry();
        int textX = getListLeft() + ORDER_BUTTON_WIDTH + 12 + ICON_SIZE + 8;
        int textRight = right - BUTTON_WIDTH - 18;
        int textWidth = Math.max(1, textRight - textX);
        Component homepagePrompt = homepagePrompt(pack);
        int homepageWidth = homepagePrompt == null ? 0 : Math.min(Math.round(this.font.width(homepagePrompt) * HOMEPAGE_PROMPT_SCALE), Math.max(1, textWidth / 2));
        int nameWidth = Math.max(1, textWidth - (homepagePrompt == null ? 0 : homepageWidth + HOMEPAGE_PROMPT_GAP));
        int nameLines = Math.max(1, this.font.split(pack.displayName, nameWidth).size());
        int authorLines = authorLines(pack, textWidth).size();
        int descriptionLines = Math.max(1, this.font.split(pack.description, textWidth).size());
        int contentHeight = ROW_PADDING_TOP + nameLines * 10 + 4 + authorLines * 10 + 4 + VERSION_LINE_HEIGHT + 4 + descriptionLines * 10;
        int actionCount = actionButtons(group).size();
        int buttonHeight = Math.max(BUTTON_HEIGHT, actionCount * BUTTON_HEIGHT + (actionCount - 1) * BUTTON_GAP);
        return Math.max(ROW_HEIGHT, Math.max(contentHeight, Math.max(buttonHeight, ROW_PADDING_TOP + ICON_SIZE)) + ROW_PADDING_BOTTOM);
    }

    private Component homepagePrompt(PackEntry entry) {
        String homepage = entry.links.get("homepage");
        return homepage == null || homepage.isBlank() ? null : Component.translatable("dlc_manager.pack.double_click_homepage");
    }

    private int centeredButtonColumnY(int rowY, int rowHeight, int count) {
        if (count <= 0) return rowY + (rowHeight - 4) / 2;
        int totalHeight = count * BUTTON_HEIGHT + (count - 1) * BUTTON_GAP;
        return rowY + Math.max(0, (rowHeight - 4 - totalHeight) / 2);
    }

    private Integer rowTop(int index, int right) {
        if (index < 0 || index >= archiveGroups.size()) return null;
        int y = getListTop() - scrollOffset + rowContentTop(index, right);
        if (y >= getListBottom() || y + rowHeight(archiveGroups.get(index), right) <= getListTop()) return null;
        return y;
    }

    private int rowContentTop(int index, int right) {
        int y = 0;
        for (int i = 0; i < index; i++) y += rowHeight(archiveGroups.get(i), right);
        return y;
    }

    private int totalContentHeight() {
        int right = getListRight() - SCROLLBAR_WIDTH - 4;
        int total = 0;
        for (PackGroup group : archiveGroups) total += rowHeight(group, right);
        return total;
    }

    private int entryAt(double mouseY) {
        if (mouseY < getListTop() || mouseY >= getListBottom()) return -1;
        int y = getListTop() - scrollOffset;
        int right = getListRight() - SCROLLBAR_WIDTH - 4;
        for (int index = 0; index < archiveGroups.size(); index++) {
            int rowHeight = rowHeight(archiveGroups.get(index), right);
            if (mouseY >= y && mouseY < y + rowHeight) return index;
            y += rowHeight;
        }
        return -1;
    }

    private RowAction orderButtonAt(int index, int x, int rowY, int rowHeight, double mouseX, double mouseY) {
        int startY = centeredButtonColumnY(rowY, rowHeight, ORDER_BUTTON_SLOTS);
        if (canMoveUp(index) && mouseX >= x && mouseX < x + ORDER_BUTTON_WIDTH && mouseY >= startY && mouseY < startY + BUTTON_HEIGHT) return RowAction.MOVE_UP;
        int downY = startY + BUTTON_HEIGHT + BUTTON_GAP;
        if (canMoveDown(index) && mouseX >= x && mouseX < x + ORDER_BUTTON_WIDTH && mouseY >= downY && mouseY < downY + BUTTON_HEIGHT) return RowAction.MOVE_DOWN;
        return null;
    }

    private RowAction actionButtonAt(PackGroup group, int x, int rowY, int rowHeight, double mouseX, double mouseY) {
        List<RowAction> buttons = actionButtons(group);
        int startY = centeredButtonColumnY(rowY, rowHeight, buttons.size());
        for (int buttonIndex = 0; buttonIndex < buttons.size(); buttonIndex++) {
            int y = startY + buttonIndex * (BUTTON_HEIGHT + BUTTON_GAP);
            if (mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT) {
                return buttons.get(buttonIndex);
            }
        }
        return null;
    }

    private boolean canMoveUp(int index) {
        return index > 0;
    }

    private boolean canMoveDown(int index) {
        return index >= 0 && index < archiveGroups.size() - 1;
    }

    private boolean versionButtonAt(PackGroup group, int x, int y, double mouseX, double mouseY) {
        if (group.versions.size() <= 1) return false;
        int width = versionButtonWidth(group);
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    private int versionButtonX(PackGroup group) {
        int x = getListLeft() + ORDER_BUTTON_WIDTH + 12 + ICON_SIZE + 8;
        return x + this.font.width(Component.translatable("dlc_manager.pack.version_prefix")) + VERSION_VALUE_GAP - VERSION_BUTTON_HORIZONTAL_PADDING;
    }

    private int versionButtonY(int index) {
        Integer rowYValue = rowTop(index, getListRight() - SCROLLBAR_WIDTH - 4);
        if (rowYValue == null) return -1000;
        PackGroup group = archiveGroups.get(index);
        int textX = getListLeft() + ORDER_BUTTON_WIDTH + 12 + ICON_SIZE + 8;
        int textRight = getListRight() - SCROLLBAR_WIDTH - 4 - BUTTON_WIDTH - 18;
        int textWidth = Math.max(1, textRight - textX);
        Component homepagePrompt = homepagePrompt(group.displayEntry());
        int homepageWidth = homepagePrompt == null ? 0 : Math.min(Math.round(this.font.width(homepagePrompt) * HOMEPAGE_PROMPT_SCALE), Math.max(1, textWidth / 2));
        int nameWidth = Math.max(1, textWidth - (homepagePrompt == null ? 0 : homepageWidth + HOMEPAGE_PROMPT_GAP));
        int nameLines = Math.max(1, this.font.split(group.displayEntry().displayName, nameWidth).size());
        int authorLines = authorLines(group.displayEntry(), textWidth).size();
        return rowYValue + ROW_PADDING_TOP + nameLines * 10 + 4 + authorLines * 10 + 4;
    }

    private int versionButtonWidth(PackGroup group) {
        return Math.max(32, this.font.width(versionButtonLabel(group)) + VERSION_BUTTON_HORIZONTAL_PADDING * 2);
    }

    private Component versionButtonLabel(PackGroup group) {
        return versionOptionLabel(group.displayEntry());
    }

    private Component versionOptionLabel(PackEntry entry) {
        return entry.installed ? Component.translatable("dlc_manager.pack.version_installed", entry.version) : Component.literal(entry.version);
    }

    private PackGroup dropdownGroup() {
        if (versionDropdownIdentifier == null) return null;
        return archiveGroups.stream().filter(group -> group.identifier.equals(versionDropdownIdentifier) && group.versions.size() > 1).findFirst().orElse(null);
    }

    private Bounds versionDropdownBounds(PackGroup group) {
        int index = -1;
        for (int i = 0; i < archiveGroups.size(); i++) {
            if (archiveGroups.get(i).identifier.equals(group.identifier)) {
                index = i;
                break;
            }
        }
        if (index < 0) return null;
        int x = versionButtonX(group);
        int buttonY = versionButtonY(index);
        int width = versionButtonWidth(group);
        for (PackEntry entry : group.versions) {
            width = Math.max(width, this.font.width(versionOptionLabel(entry)) + VERSION_BUTTON_HORIZONTAL_PADDING * 2);
        }
        int height = group.versions.size() * VERSION_DROPDOWN_ITEM_HEIGHT + 2;
        int downY = buttonY + BUTTON_HEIGHT + 2;
        int upY = buttonY - height - 2;
        int y;
        if (downY + height <= getListBottom()) {
            y = downY;
        } else if (upY >= getListTop()) {
            y = upY;
        } else {
            return null;
        }
        return new Bounds(x, y, width, height);
    }

    private boolean handleVersionDropdownClick(double mouseX, double mouseY) {
        PackGroup group = dropdownGroup();
        if (group == null) return false;
        Bounds bounds = versionDropdownBounds(group);
        if (bounds == null) return false;
        if (mouseX < bounds.x || mouseX >= bounds.x + bounds.width || mouseY < bounds.y || mouseY >= bounds.y + bounds.height) return false;
        int index = Math.max(0, Math.min(group.versions.size() - 1, ((int) mouseY - bounds.y - 1) / VERSION_DROPDOWN_ITEM_HEIGHT));
        PackEntry selected = group.versions.get(index);
        selectedVersionByIdentifier.put(group.identifier, selected.key);
        List<PackGroup> out = new ArrayList<>();
        for (PackGroup candidate : archiveGroups) {
            out.add(candidate.identifier.equals(group.identifier) ? new PackGroup(candidate.identifier, candidate.versions, candidate.orderKey, selected.key) : candidate);
        }
        archiveGroups = out;
        if (pendingInstalls.get(group.identifier) != null && isEntryCurrentlyInstalled(selected)) {
            pendingInstalls.remove(group.identifier);
        }
        versionDropdownIdentifier = null;
        rebuildButtons();
        return true;
    }

    private void drawScrollBar(GuiGraphics guiGraphics) {
        int maxScrollValue = getMaxScroll();
        if (maxScrollValue <= 0) return;
        int x = getListRight() - SCROLLBAR_WIDTH;
        int thumbHeight = scrollThumbHeight();
        int thumbY = scrollThumbY();
        guiGraphics.fill(x, getListTop(), x + 4, getListBottom(), 0x66000000);
        guiGraphics.fill(x, thumbY, x + 4, thumbY + thumbHeight, 0xFF555555);
    }

    private int scrollThumbHeight() {
        int contentHeight = totalContentHeight();
        int viewport = getListBottom() - getListTop();
        return Math.max(18, viewport * viewport / Math.max(1, contentHeight));
    }

    private int scrollThumbY() {
        int maxScrollValue = getMaxScroll();
        if (maxScrollValue <= 0) return getListTop();
        int trackHeight = getListBottom() - getListTop();
        int thumbHeight = scrollThumbHeight();
        return getListTop() + (trackHeight - thumbHeight) * scrollOffset / maxScrollValue;
    }

    private boolean isOnScrollbar(double mouseX, double mouseY) {
        return mouseX >= getListRight() - SCROLLBAR_WIDTH - 4 && mouseX <= getListRight() + 4 && mouseY >= getListTop() && mouseY <= getListBottom();
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(getMaxScroll(), scrollOffset));
    }

    private int getMaxScroll() {
        return Math.max(0, totalContentHeight() - (getListBottom() - getListTop()));
    }

    private int getListLeft() {
        return 24;
    }

    private int getListRight() {
        return this.width - 24;
    }

    private int getListTop() {
        return 34;
    }

    private int getListBottom() {
        return this.height - 64;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !working;
    }

    @Override
    public void onClose() {
        if (!working) onBackOrCancel();
    }

    private interface WorkAction {
        Component run();
    }

    private record IconTexture(ResourceLocation location, int width, int height) {
    }

    private static final class PackEntry {
        private final String archivePath;
        private final String key;
        private final String identifier;
        private final String version;
        private final Component displayName;
        private final Component description;
        private final List<String> authorNames;
        private final Map<String, String> links;
        private final boolean installed;
        private final String installedVersion;
        private final IconTexture iconTexture;
        private final String displayNameText;

        private PackEntry(String archivePath, String key, String identifier, String version, Component displayName, Component description,
                          List<String> authorNames, Map<String, String> links, boolean installed, String installedVersion, IconTexture iconTexture) {
            this.archivePath = archivePath == null ? "" : archivePath;
            this.key = key == null ? "" : key;
            this.identifier = identifier == null ? "" : identifier;
            this.version = version == null ? "" : version;
            this.displayName = displayName == null ? Component.literal(this.identifier) : displayName;
            this.description = description == null ? Component.empty() : description;
            this.authorNames = authorNames == null || authorNames.isEmpty() ? List.of() : List.copyOf(authorNames);
            this.links = links == null ? Map.of() : Map.copyOf(links);
            this.installed = installed;
            this.installedVersion = installedVersion == null ? "" : installedVersion;
            this.iconTexture = iconTexture;
            String rawDisplayName = this.displayName.getString();
            this.displayNameText = rawDisplayName.isBlank() ? this.identifier : rawDisplayName;
        }
    }

    private static final class PackGroup {
        private final String identifier;
        private final List<PackEntry> versions;
        private final String orderKey;
        private final String selectedVersionKey;

        private PackGroup(String identifier, List<PackEntry> versions, String orderKey, String selectedVersionKey) {
            this.identifier = identifier;
            this.versions = List.copyOf(versions);
            this.orderKey = orderKey;
            this.selectedVersionKey = selectedVersionKey;
        }

        private PackEntry displayEntry() {
            for (PackEntry entry : versions) {
                if (entry.key.equals(selectedVersionKey)) {
                    return entry;
                }
            }
            return versions.get(0);
        }
    }

    private record Bounds(int x, int y, int width, int height) {
    }

    private enum RowAction {
        INSTALL,
        DELETE,
        FEEDBACK,
        SUPPORT,
        MOVE_UP,
        MOVE_DOWN
    }
}
