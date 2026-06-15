package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcConfigDlcSupport;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.core.DlcType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import java.nio.file.Path;
import java.util.List;

public class DlcListEntry extends WidgetListEntryBase<DlcListRow> {
    private static final int COLOR_GROUP = 0xFFBFD7FF;
    private static final int COLOR_PATH = 0xFF9AA7B1;
    private static final int COLOR_ITEM = 0xFFE6E6E6;
    private static final int COLOR_ENABLED = 0xFF7AC37A;
    private static final int COLOR_REDIRECT = 0xFFBFD7FF;
    private static final int COLOR_ENABLED_BAR = 0xFF4CAF50;
    private static final int COLOR_PENDING_BAR = 0xFFFFC24A;
    private static final int COLOR_PENDING_BG = 0xCC4C3410;
    private static final int COLOR_PENDING_BORDER = 0xFFFFC24A;
    private static final int COLOR_PENDING_TEXT = 0xFFFFE2A6;
    private static final int COLOR_GROUP_SEPARATOR = 0xCC6A88B8;
    private static final int COLOR_PATH_SEPARATOR = 0x887A8B9D;
    private static final int COLOR_ITEM_SEPARATOR = 0x33485460;

    private final DlcListRow row;
    private final int listIndex;
    private ButtonGeneric toggleButton;
    private ButtonGeneric groupToggleButton;
    private ButtonGeneric redirectButton;
    private int groupToggleWidth;
    private int groupToggleHeight;
    private long lastNameClickTime;
    private int lastNameClickButton = -1;
    private static final long DOUBLE_CLICK_INTERVAL_MS = 250L;

    public DlcListEntry(int x, int y, int width, int height, boolean isOdd, DlcListRow row, int listIndex) {
        super(x, y, width, height, row, listIndex);
        this.row = row;
        this.listIndex = listIndex;
        if (row.getType() == DlcListRow.RowType.ITEM) {
            createToggleButton(width, height, row.getItem());
        } else if (row.getType() == DlcListRow.RowType.REDIRECT_ITEM) {
            createRedirectButton(width, height, row.getRedirectEntry());
        } else if (row.getType() == DlcListRow.RowType.PATH_HEADER && !row.isRootPath()) {
            createGroupToggleButton(width, height, row.getItems());
        }
    }

    private void createToggleButton(int width, int height, DlcItem item) {
        int buttonWidth = 64;
        int buttonHeight = 18;
        int bx = this.x + width - buttonWidth - 6;
        int by = getAlignedButtonY(this.y, height, buttonHeight);
        toggleButton = new ButtonGeneric(bx, by, buttonWidth, buttonHeight, getToggleLabel(item));
        addButton(toggleButton, new ToggleListener(item));
    }

    private void createGroupToggleButton(int width, int height, java.util.List<DlcItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        groupToggleWidth = Math.max(86, Minecraft.getInstance().font.width(getGroupToggleState(items).label) + 16);
        groupToggleHeight = 16;
        int bx = this.x + width - groupToggleWidth - 6 - 64 - 6;
        int by = getGroupToggleButtonY(this.y, height, groupToggleHeight);
        groupToggleButton = new ButtonGeneric(bx, by, groupToggleWidth, groupToggleHeight, getGroupToggleState(items).label);
        addButton(groupToggleButton, new GroupToggleListener(items));
    }

    private void createRedirectButton(int width, int height, DlcRedirectEntry entry) {
        if (entry == null) {
            return;
        }
        int buttonWidth = 64;
        int buttonHeight = 18;
        int bx = this.x + width - buttonWidth - 6;
        int by = getAlignedButtonY(this.y, height, buttonHeight);
        redirectButton = new ButtonGeneric(bx, by, buttonWidth, buttonHeight,
            Component.translatable("dlc_manager.redirect.open").getString());
        addButton(redirectButton, new RedirectListener(entry));
    }

    @Override
    public void render(int mouseX, int mouseY, boolean hovered, GuiGraphics guiGraphics) {
        if (row.getType() == DlcListRow.RowType.GROUP_HEADER) {
            drawGroupSeparator(guiGraphics);
            String label = Component.translatable(row.getGroupType().getDisplayKey()).getString();
            drawStringWithShadow(this.x + 6, this.y + 4, COLOR_GROUP, label, guiGraphics);
        } else if (row.getType() == DlcListRow.RowType.REDIRECT_HEADER) {
            drawGroupSeparator(guiGraphics);
            drawStringWithShadow(this.x + 6, this.y + 4, COLOR_REDIRECT, row.getLabel(), guiGraphics);
        } else if (row.getType() == DlcListRow.RowType.PATH_HEADER) {
            drawPathSeparator(guiGraphics);
            drawString(this.x + 10, this.y + 4, COLOR_PATH, row.getLabel(), guiGraphics);
            if (groupToggleButton != null) {
                GroupToggleState state = getGroupToggleState(row.getItems());
                if (state.show) {
                    groupToggleWidth = Math.max(86, Minecraft.getInstance().font.width(state.label) + 16);
                    int bx = this.x + this.width - groupToggleWidth - 6 - 64 - 6;
                    int by = getGroupToggleButtonY(this.y, this.height, groupToggleHeight);
                    groupToggleButton.setX(bx);
                    groupToggleButton.setY(by);
                    groupToggleButton.setWidth(groupToggleWidth);
                    groupToggleButton.setHeight(groupToggleHeight);
                    groupToggleButton.setDisplayString(state.label);
                    groupToggleButton.setEnabled(state.enabled);
                } else {
                    groupToggleButton.setDisplayString("");
                    groupToggleButton.setEnabled(false);
                    groupToggleButton.setWidth(0);
                    groupToggleButton.setX(-10000);
                }
            }
        } else if (row.getType() == DlcListRow.RowType.REDIRECT_ITEM) {
            drawItemSeparator(guiGraphics);
            DlcRedirectEntry entry = row.getRedirectEntry();
            String lang = Minecraft.getInstance().options.languageCode;
            String label = entry == null ? "" : entry.resolveDisplayName(lang);
            int textY = this.y + (this.height - Minecraft.getInstance().font.lineHeight) / 2;
            guiGraphics.drawString(Minecraft.getInstance().font,
                DlcTextFormatter.parse(label),
                this.x + 10, textY, COLOR_ITEM, false);
            if (redirectButton != null) {
                int bx = this.x + this.width - redirectButton.getWidth() - 6;
                int by = getAlignedButtonY(this.y, this.height, redirectButton.getHeight());
                redirectButton.setX(bx);
                redirectButton.setY(by);
            }
        } else if (row.getType() == DlcListRow.RowType.ITEM) {
            drawItemSeparator(guiGraphics);
            DlcItem item = row.getItem();
            boolean dirty = isDirty(item);
            item.setDirty(dirty);
            String displayName = DlcDisplayNameUtil.resolveDisplayName(item);
            int textY = this.y + (this.height - Minecraft.getInstance().font.lineHeight) / 2;
            if (dirty) {
                drawPendingAccent(guiGraphics);
            } else if (item.isEnabled()) {
                drawEnabledAccent(guiGraphics);
            }
            if (dirty) {
                guiGraphics.drawString(Minecraft.getInstance().font,
                    DlcTextFormatter.parse(displayName),
                    this.x + 10, textY, COLOR_ITEM, false);
                drawPendingBadge(guiGraphics, DlcMaLiLibScreen.getPendingSuffix(item));
            } else {
                guiGraphics.drawString(Minecraft.getInstance().font,
                    DlcTextFormatter.parse(displayName),
                    this.x + 10, textY, COLOR_ITEM, false);
            }
            if (toggleButton != null) {
                toggleButton.setDisplayString(getToggleLabel(item));
                boolean missing = DlcRuntimeState.hasMissingDependencies(item);
                boolean blocked = DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier());
                toggleButton.setEnabled(!blocked && (item.isEnabled() || !missing));
            }
        }
        super.render(mouseX, mouseY, hovered, guiGraphics);
        if (isMouseWithin(mouseX, mouseY) && !isMouseOverButton(mouseX, mouseY)) {
            if (row.getType() == DlcListRow.RowType.ITEM) {
                DlcItem item = row.getItem();
                var font = Minecraft.getInstance().font;
                int maxWidth = Math.max(160, Math.min(this.width - 20, 280));
                var lines = buildTooltipLines(font, item, maxWidth);
                if (!lines.isEmpty()) {
                    DlcTooltipContext.set(lines, mouseX, mouseY);
                }
            } else if (row.getType() == DlcListRow.RowType.REDIRECT_ITEM) {
                DlcRedirectEntry entry = row.getRedirectEntry();
                String tip = entry == null ? null : entry.resolveTip(Minecraft.getInstance().options.languageCode);
                if (tip != null && !tip.isBlank()) {
                    var font = Minecraft.getInstance().font;
                    int maxWidth = Math.max(160, Math.min(this.width - 20, 280));
                    java.util.ArrayList<net.minecraft.util.FormattedCharSequence> lines =
                        new java.util.ArrayList<>(DlcTextFormatter.splitMultiline(font, tip, maxWidth));
                    if (!lines.isEmpty()) {
                        DlcTooltipContext.set(lines, mouseX, mouseY);
                    }
                }
            }
        }
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.onMouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton != 0 || row.getType() != DlcListRow.RowType.ITEM) {
            return false;
        }
        DlcItem item = row.getItem();
        if (item == null || item.getRedirectUrl() == null || item.getRedirectUrl().isBlank()) {
            return false;
        }
        if (!isMouseWithin(mouseX, mouseY) || isMouseOverButton(mouseX, mouseY) || !isMouseOverItemLabel(mouseX, mouseY, item)) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean isDoubleClick = this.lastNameClickButton == mouseButton && now - this.lastNameClickTime <= DOUBLE_CLICK_INTERVAL_MS;
        this.lastNameClickTime = now;
        this.lastNameClickButton = mouseButton;
        if (isDoubleClick) {
            this.lastNameClickTime = 0L;
            openItemRedirect(item);
            return true;
        }
        return false;
    }

    private void drawPendingAccent(GuiGraphics guiGraphics) {
        guiGraphics.fill(this.x + 1, this.y + 2, this.x + 4, this.y + this.height - 2, COLOR_PENDING_BAR);
    }

    private void drawEnabledAccent(GuiGraphics guiGraphics) {
        guiGraphics.fill(this.x + 1, this.y + 2, this.x + 4, this.y + this.height - 2, COLOR_ENABLED_BAR);
    }

    private void drawGroupSeparator(GuiGraphics guiGraphics) {
        if (this.listIndex > 0) {
            guiGraphics.fill(this.x + 2, this.y + 1, this.x + this.width - 2, this.y + 3, COLOR_GROUP_SEPARATOR);
        }
    }

    private void drawPathSeparator(GuiGraphics guiGraphics) {
        if (!row.shouldHideTopSeparator()) {
            guiGraphics.fill(this.x + 6, this.y + 1, this.x + this.width - 6, this.y + 2, COLOR_PATH_SEPARATOR);
        }
    }

    private void drawItemSeparator(GuiGraphics guiGraphics) {
        guiGraphics.fill(this.x + 10, this.y + this.height - 1, this.x + this.width - 6, this.y + this.height, COLOR_ITEM_SEPARATOR);
    }

    private int getAlignedButtonY(int rowY, int rowHeight, int buttonHeight) {
        return rowY + (rowHeight - buttonHeight) / 2;
    }

    private int getGroupToggleButtonY(int rowY, int rowHeight, int buttonHeight) {
        return getAlignedButtonY(rowY, rowHeight, buttonHeight) + 2;
    }

    private void drawPendingBadge(GuiGraphics guiGraphics, String pendingText) {
        if (pendingText == null || pendingText.isBlank()) {
            return;
        }
        String label = pendingText.trim();
        var font = Minecraft.getInstance().font;
        int textWidth = font.width(label);
        int badgeHeight = 14;
        int badgeWidth = textWidth + 10;
        int rightAnchor = toggleButton != null ? toggleButton.getX() - 6 : this.x + this.width - 6;
        int badgeX = rightAnchor - badgeWidth;
        int badgeY = this.y + (this.height - badgeHeight) / 2;
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, COLOR_PENDING_BG);
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + 1, COLOR_PENDING_BORDER);
        guiGraphics.fill(badgeX, badgeY + badgeHeight - 1, badgeX + badgeWidth, badgeY + badgeHeight, COLOR_PENDING_BORDER);
        guiGraphics.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeHeight, COLOR_PENDING_BORDER);
        guiGraphics.fill(badgeX + badgeWidth - 1, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, COLOR_PENDING_BORDER);
        guiGraphics.drawString(font, label, badgeX + 5, badgeY + 3, COLOR_PENDING_TEXT, false);
    }

    private boolean isMouseOverButton(int mouseX, int mouseY) {
        if (toggleButton != null && isMouseOverButton(toggleButton, mouseX, mouseY)) {
            return true;
        }
        if (redirectButton != null && isMouseOverButton(redirectButton, mouseX, mouseY)) {
            return true;
        }
        return groupToggleButton != null && isMouseOverButton(groupToggleButton, mouseX, mouseY);
    }

    private boolean isMouseOverButton(ButtonGeneric button, int mouseX, int mouseY) {
        int bx = button.getX();
        int by = button.getY();
        int bw = button.getWidth();
        int bh = button.getHeight();
        return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
    }

    private boolean isMouseOverItemLabel(int mouseX, int mouseY, DlcItem item) {
        if (item == null) {
            return false;
        }
        var font = Minecraft.getInstance().font;
        String displayName = DlcDisplayNameUtil.resolveDisplayName(item);
        int textX = this.x + 10;
        int textY = this.y + (this.height - font.lineHeight) / 2;
        int textWidth = font.width(DlcTextFormatter.parse(displayName));
        return mouseX >= textX && mouseX < textX + textWidth
            && mouseY >= textY && mouseY < textY + font.lineHeight;
    }

    private String getToggleLabel(DlcItem item) {
        return item.isEnabled()
            ? "\u00A7a" + Component.translatable("dlc_manager.toggle.on").getString()
            : Component.translatable("dlc_manager.toggle.off").getString();
    }

    private void openRedirect(DlcRedirectEntry entry) {
        if (entry == null || entry.getRedirectUrl() == null || entry.getRedirectUrl().isBlank()) {
            return;
        }
        Screen parent = Minecraft.getInstance().screen;
        String lang = Minecraft.getInstance().options.languageCode;
        String notice = entry.resolveNotice(lang);
        Minecraft.getInstance().setScreen(new DlcRedirectConfirmScreen(parent, entry.getRedirectUrl(), notice));
    }

    private void openItemRedirect(DlcItem item) {
        if (item == null || item.getRedirectUrl() == null || item.getRedirectUrl().isBlank()) {
            return;
        }
        Screen parent = Minecraft.getInstance().screen;
        Minecraft.getInstance().setScreen(new DlcRedirectConfirmScreen(parent, item.getRedirectUrl(), ""));
    }

    private static GroupToggleState getGroupToggleState(java.util.List<DlcItem> items) {
        if (items == null || items.isEmpty()) {
            return new GroupToggleState("", false, false);
        }
        boolean anyEligibleDisabled = items.stream().anyMatch(item -> item.AllowEnableByAll()
            && !DlcRuntimeState.hasMissingDependencies(item)
            && !DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())
            && !item.isEnabled());
        boolean anyEnabled = items.stream().anyMatch(DlcItem::isEnabled);
        if (anyEligibleDisabled) {
            return new GroupToggleState(Component.translatable("dlc_manager.group.enable").getString(), true, true);
        }
        if (anyEnabled) {
            return new GroupToggleState(Component.translatable("dlc_manager.group.disable").getString(), true, true);
        }
        return new GroupToggleState("", false, false);
    }

    private String getTooltipText(DlcItem item) {
        var tips = item.getTooltips();
        if (tips.isEmpty()) {
            return null;
        }
        String lang = Minecraft.getInstance().options.languageCode;
        if (tips.containsKey(lang)) {
            return tips.get(lang);
        }
        if (tips.containsKey("en_us")) {
            return tips.get("en_us");
        }
        return tips.values().stream().findFirst().orElse(null);
    }

    private boolean isMouseWithin(int mouseX, int mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width
            && mouseY >= this.y && mouseY < this.y + this.height;
    }

    private java.util.List<net.minecraft.util.FormattedCharSequence> buildTooltipLines(net.minecraft.client.gui.Font font, DlcItem item, int maxWidth) {
        java.util.ArrayList<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
        String tooltip = getTooltipText(item);
        String redirectHint = null;
        if (item.getRedirectUrl() != null && !item.getRedirectUrl().isBlank()) {
            redirectHint = "\u00A7b" + Component.translatable("dlc_manager.tooltip.double_click_redirect").getString() + "\u00A7r";
        }
        if (tooltip != null && !tooltip.isBlank()) {
            lines.addAll(DlcTextFormatter.splitMultiline(font, tooltip, maxWidth));
        } else if (redirectHint != null) {
            lines.addAll(DlcTextFormatter.splitMultiline(font, redirectHint, maxWidth));
        }

        if (redirectHint != null && tooltip != null && !tooltip.isBlank()) {
            if (!lines.isEmpty()) {
                lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            }
            lines.addAll(DlcTextFormatter.splitMultiline(font, redirectHint, maxWidth));
        }

        java.util.List<String> deps = collectDependencyNamesRecursive(item);
        java.util.List<String> conflicts = item.getConflicts();
        java.util.Set<String> missing = DlcRuntimeState.collectMissingDependencyIdentifiers(item);
        java.util.List<String> presetBlockers = DlcPresetRuntimeState.getDlcBlockerNames(item.getIdentifier(),
            Minecraft.getInstance().options.languageCode);
        if (!presetBlockers.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            }
            for (String blocker : presetBlockers) {
                Component line = Component.translatable("dlc_manager.preset.conflict", blocker)
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED);
                lines.addAll(font.split(line, maxWidth));
            }
        }
        if (!missing.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            }
            Component missingLabel = Component.empty()
                .append(Component.translatable("dlc_manager.tooltip.missing_dependencies").withStyle(net.minecraft.ChatFormatting.RED))
                .append(":");
            lines.addAll(font.split(missingLabel, maxWidth));
            for (String dep : missing) {
                lines.addAll(font.split(DlcTextFormatter.parse(DlcDisplayNameUtil.resolveDisplayName(dep)), maxWidth));
            }
        }
        if (deps != null && !deps.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            }
            Component depLabel = Component.empty()
                .append(Component.translatable("dlc_manager.tooltip.dependencies").withStyle(net.minecraft.ChatFormatting.GOLD))
                .append(":");
            lines.addAll(font.split(depLabel, maxWidth));
            for (String dep : deps) {
                lines.addAll(font.split(DlcTextFormatter.parse(DlcDisplayNameUtil.resolveDisplayName(dep)), maxWidth));
            }
        }
        if (conflicts != null && !conflicts.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            }
            Component conflictLabel = Component.empty()
                .append(Component.translatable("dlc_manager.tooltip.conflicts").withStyle(net.minecraft.ChatFormatting.DARK_RED))
                .append(":");
            lines.addAll(font.split(conflictLabel, maxWidth));
            for (String conflict : conflicts) {
                lines.addAll(font.split(DlcTextFormatter.parse(DlcDisplayNameUtil.resolveDisplayName(conflict)), maxWidth));
            }
        }
        return lines;
    }

    private static java.util.List<String> collectDependencyNamesRecursive(DlcItem item) {
        if (item == null) {
            return java.util.List.of();
        }
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        addDependencyNames(item.getDependencies(), result, queue);
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (name == null || name.isBlank()) {
                continue;
            }
            DlcItem depItem = DlcRuntimeState.getItemByIdentifier(name);
            if (depItem == null) {
                continue;
            }
            addDependencyNames(depItem.getDependencies(), result, queue);
        }
        return new java.util.ArrayList<>(result);
    }

    private static void addDependencyNames(java.util.List<String> names, java.util.Set<String> result,
                                           java.util.ArrayDeque<String> queue) {
        if (names == null || names.isEmpty()) {
            return;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (result.add(name)) {
                queue.add(name);
            }
        }
    }

    private static class ToggleListener implements IButtonActionListener {
        private final DlcItem item;

        private ToggleListener(DlcItem item) {
            this.item = item;
        }

        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
            if (item.isEnabled()) {
                disableItemsWithDependents(java.util.List.of(item));
                updateToggleButtonLabel(button, item);
                item.setDirty(isDirty(item));
                return;
            }

            if (DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())) {
                return;
            }

            requestEnable(item, button);
        }
    }

    private class RedirectListener implements IButtonActionListener {
        private final DlcRedirectEntry entry;

        private RedirectListener(DlcRedirectEntry entry) {
            this.entry = entry;
        }

        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
            openRedirect(entry);
        }
    }

    private static class GroupToggleListener implements IButtonActionListener {
        private final java.util.List<DlcItem> items;

        private GroupToggleListener(java.util.List<DlcItem> items) {
            this.items = items;
        }

        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
            if (items == null || items.isEmpty()) {
                return;
            }
            GroupToggleState state = getGroupToggleState(items);
            if (!state.show) {
                return;
            }
            boolean anyEligibleDisabled = items.stream().anyMatch(item -> item.AllowEnableByAll()
                && !DlcRuntimeState.hasMissingDependencies(item)
                && !DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())
                && !item.isEnabled());
            boolean target = anyEligibleDisabled;
            if (target) {
                java.util.List<DlcItem> targets = items.stream()
                    .filter(item -> item.AllowEnableByAll()
                        && !DlcRuntimeState.hasMissingDependencies(item)
                        && !DlcPresetRuntimeState.isDlcBlocked(item.getIdentifier())
                        && !item.isEnabled())
                    .toList();
                DlcRuntimeState.ConflictBatchResult conflicts = DlcRuntimeState.collectConflicts(targets);
                if (!conflicts.getLines().isEmpty()) {
                    java.util.List<DlcItem> filteredTargets = filterConflictTargets(targets, conflicts);
                    showBatchConflictPrompt(conflicts.getLines(), () -> continueBatchEnable(filteredTargets));
                    return;
                }
                continueBatchEnable(targets);
            } else {
                disableItemsWithDependents(items);
            }
            if (button instanceof ButtonGeneric generic) {
                state = getGroupToggleState(items);
                generic.setDisplayString(state.label);
                generic.setEnabled(state.enabled);
            }
        }
    }

    private static void continueBatchEnable(java.util.List<DlcItem> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        DlcRuntimeState.DependencyBatchResult deps = DlcRuntimeState.collectMissingDependencies(targets);
        if (!deps.getLines().isEmpty()) {
            Component scopeLabel = Component.translatable("dlc_manager.scope.group");
            showBatchDependencyPrompt(deps.getLines(), deps.getItemsToEnable(), targets, scopeLabel);
            return;
        }
        java.util.List<DlcItem> alarmItems = targets.stream()
            .filter(DlcItem::isAlarm)
            .toList();
        if (!alarmItems.isEmpty()) {
            showBatchAlarmPrompt(alarmItems, () -> enableItems(targets, true));
            return;
        }
        enableItems(targets, true);
    }

    private static boolean isDirty(DlcItem item) {
        return DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled());
    }

    private static void updateToggleButtonLabel(fi.dy.masa.malilib.gui.button.ButtonBase button, DlcItem item) {
        if (button instanceof ButtonGeneric generic) {
            generic.setDisplayString(item.isEnabled()
                ? "\u00A7a" + Component.translatable("dlc_manager.toggle.on").getString()
                : Component.translatable("dlc_manager.toggle.off").getString());
        }
    }

    private static void requestEnable(DlcItem item, fi.dy.masa.malilib.gui.button.ButtonBase button) {
        if (item == null) {
            return;
        }
        if (item.isAlarm()) {
            Screen returnScreen = Minecraft.getInstance().screen;
            DlcReturnPositionHost.remember(returnScreen);
            String tip = resolveLocalized(item.getTooltips());
            Minecraft.getInstance().setScreen(new DlcAlarmConfirmScreen(returnScreen, tip, () -> {
                attemptEnable(item, button);
                if (returnScreen instanceof DlcMaLiLibScreen screen) {
                    screen.refreshAfterStateChange();
                }
            }));
            return;
        }
        attemptEnable(item, button);
    }

    private static void attemptEnable(DlcItem item, fi.dy.masa.malilib.gui.button.ButtonBase button) {
        ConflictCheck conflicts = getConflicts(item);
        if (!conflicts.items.isEmpty()) {
            showConflictPrompt(item, conflicts, button);
            return;
        }

        DependencyCheck dependencies = getMissingDependencies(item);
        if (!dependencies.names.isEmpty()) {
            showDependencyPrompt(item, dependencies, button);
            return;
        }

        item.setEnabled(true);
        DlcRuntimeState.setOverride(item.getId(), true);
        updateToggleButtonLabel(button, item);
        item.setDirty(isDirty(item));
    }

    private static void continueEnableAfterConflictResolution(DlcItem item, fi.dy.masa.malilib.gui.button.ButtonBase button) {
        int guard = 0;
        while (guard++ < 64) {
            ConflictCheck conflicts = getConflicts(item);
            if (conflicts.items.isEmpty()) {
                break;
            }
            disableItemsWithDependents(conflicts.items);
        }

        DependencyCheck dependencies = getMissingDependencies(item);
        if (!dependencies.names.isEmpty()) {
            showDependencyPrompt(item, dependencies, button);
            return;
        }

        item.setEnabled(true);
        DlcRuntimeState.setOverride(item.getId(), true);
        updateToggleButtonLabel(button, item);
        item.setDirty(isDirty(item));
    }

    private static String resolveLocalized(java.util.Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        String lang = Minecraft.getInstance().options.languageCode;
        if (values.containsKey(lang)) {
            return values.get(lang);
        }
        if (values.containsKey("en_us")) {
            return values.get("en_us");
        }
        return values.values().stream().findFirst().orElse("");
    }

    private static void showBatchAlarmPrompt(java.util.List<DlcItem> alarmItems, Runnable onConfirm) {
        Screen returnScreen = Minecraft.getInstance().screen;
        DlcReturnPositionHost.remember(returnScreen);
        String tip = buildBatchAlarmTip(alarmItems);
        Minecraft.getInstance().setScreen(new DlcAlarmConfirmScreen(returnScreen, tip, () -> {
            if (onConfirm != null) {
                onConfirm.run();
            }
            if (returnScreen instanceof DlcMaLiLibScreen screen) {
                screen.refreshAfterStateChange();
            }
        }));
    }

    private static String buildBatchAlarmTip(java.util.List<DlcItem> alarmItems) {
        if (alarmItems == null || alarmItems.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (DlcItem item : alarmItems) {
            if (item == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(DlcDisplayNameUtil.resolveDisplayName(item));
            String tip = resolveLocalized(item.getTooltips());
            if (tip != null && !tip.isBlank()) {
                sb.append("\n").append(tip);
            }
        }
        return sb.toString();
    }

    private static java.util.List<DlcItem> filterConflictTargets(java.util.List<DlcItem> targets, DlcRuntimeState.ConflictBatchResult conflicts) {
        if (targets == null || targets.isEmpty()) {
            return java.util.List.of();
        }
        java.util.Set<String> targetIds = new java.util.HashSet<>();
        for (DlcItem item : targets) {
            if (item != null && item.getIdentifier() != null && !item.getIdentifier().isBlank()) {
                targetIds.add(item.getIdentifier());
            }
        }
        if (targetIds.isEmpty()) {
            return java.util.List.of();
        }
        if (conflicts == null || conflicts.getLines().isEmpty()) {
            return targets;
        }
        java.util.Set<String> skipped = new java.util.HashSet<>();
        for (DlcRuntimeState.ConflictLine line : conflicts.getLines()) {
            String left = line.getLeftName();
            String right = line.getRightName();
            if (left != null && targetIds.contains(left)) {
                skipped.add(left);
            }
            if (right != null && targetIds.contains(right)) {
                skipped.add(right);
            }
        }
        if (skipped.isEmpty()) {
            return targets;
        }
        return targets.stream()
            .filter(item -> item != null && (item.getIdentifier() == null || !skipped.contains(item.getIdentifier())))
            .toList();
    }

    private static DependencyCheck getMissingDependencies(DlcItem item) {
        DlcRuntimeState.DependencyBatchResult result = DlcRuntimeState.collectMissingDependencies(java.util.List.of(item));
        if (result.getLines().isEmpty()) {
            return new DependencyCheck(java.util.List.of(), java.util.List.of());
        }
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (DlcRuntimeState.DependencyLine line : result.getLines()) {
            names.add(DlcDisplayNameUtil.resolveDisplayName(line.getDependencyName()));
        }
        return new DependencyCheck(new java.util.ArrayList<>(names), result.getItemsToEnable());
    }

    private static ConflictCheck getConflicts(DlcItem item) {
        if (item == null) {
            return new ConflictCheck(java.util.List.of(), java.util.List.of());
        }
        DlcRuntimeState.ConflictBatchResult batch = DlcRuntimeState.collectConflicts(java.util.List.of(item));
        if (batch.getLines().isEmpty()) {
            return new ConflictCheck(java.util.List.of(), java.util.List.of());
        }
        String rootId = item.getIdentifier();
        java.util.Set<String> conflictIds = new java.util.LinkedHashSet<>();
        for (DlcRuntimeState.ConflictLine line : batch.getLines()) {
            String left = line.getLeftName();
            String right = line.getRightName();
            if (left != null && !left.isBlank() && (rootId == null || !left.equals(rootId))) {
                conflictIds.add(left);
            }
            if (right != null && !right.isBlank() && (rootId == null || !right.equals(rootId))) {
                conflictIds.add(right);
            }
        }
        java.util.List<DlcItem> toDisable = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String identifier : conflictIds) {
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            DlcItem conflictItem = DlcRuntimeState.getItemByIdentifier(identifier);
            if (conflictItem != null && conflictItem.isEnabled()) {
                toDisable.add(conflictItem);
                names.add(DlcDisplayNameUtil.resolveDisplayName(identifier));
            }
        }
        return new ConflictCheck(names, toDisable);
    }

    private static void enableItems(java.util.Collection<DlcItem> items, boolean enabled) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            item.setEnabled(enabled);
            DlcRuntimeState.setOverride(item.getId(), enabled);
        }
    }

    private static void disableItemsWithDependents(java.util.Collection<DlcItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<DlcItem> toDisable = new java.util.LinkedHashSet<>();
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            toDisable.addAll(DlcRuntimeState.collectDependentsRecursive(item));
        }
        enableItems(toDisable, false);
        java.util.Set<DlcItem> hiddenDeps = DlcRuntimeState.collectHiddenDependenciesToDisable(toDisable);
        enableItems(hiddenDeps, false);
    }

    private static void showBatchConflictPrompt(java.util.List<DlcRuntimeState.ConflictLine> lines, Runnable onSkipConfirm) {
        Component message = DlcConfirmUtil.buildConflictMessage(lines);
        Screen returnScreen = Minecraft.getInstance().screen;
        if (returnScreen instanceof DlcMaLiLibScreen screen) {
            screen.rememberListScroll();
        }
        Component title = Component.translatable("dlc_manager.batch.conflicts_title")
            .withStyle(net.minecraft.ChatFormatting.RED);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(returnScreen);
            if (!accepted) {
                return;
            }
            if (onSkipConfirm != null) {
                onSkipConfirm.run();
            }
            if (returnScreen instanceof DlcMaLiLibScreen screen) {
                screen.refreshAfterStateChange();
            }
        }, title, message,
            Component.translatable("dlc_manager.batch.conflicts_skip_enable"),
            Component.translatable("dlc_manager.confirm_no")));
    }

    private static void showBatchDependencyPrompt(java.util.List<DlcRuntimeState.DependencyLine> lines,
        java.util.List<DlcItem> depsToEnable, java.util.List<DlcItem> targets, Component scopeLabel) {
        Component message = DlcConfirmUtil.buildDependencyMessage(lines);
        Screen returnScreen = Minecraft.getInstance().screen;
        if (returnScreen instanceof DlcMaLiLibScreen screen) {
            screen.rememberListScroll();
        }
        Component title = Component.translatable("dlc_manager.batch.dependencies_title");
        Component enableLabel = Component.translatable("dlc_manager.batch.enable_deps", scopeLabel);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(returnScreen);
            if (!accepted) {
                return;
            }
            java.util.ArrayList<DlcItem> planned = new java.util.ArrayList<>();
            if (depsToEnable != null) {
                planned.addAll(depsToEnable);
            }
            if (targets != null) {
                planned.addAll(targets);
            }
            DlcRuntimeState.ConflictBatchResult conflicts = DlcRuntimeState.collectConflicts(planned);
            if (!conflicts.getLines().isEmpty()) {
                java.util.List<DlcItem> filtered = filterConflictTargets(planned, conflicts);
                showBatchConflictPrompt(conflicts.getLines(), () -> {
                    if (!filtered.isEmpty()) {
                        enableItems(filtered, true);
                    }
                    if (returnScreen instanceof DlcMaLiLibScreen screen) {
                        screen.refreshAfterStateChange();
                    }
                });
                return;
            }
            enableItems(planned, true);
            if (returnScreen instanceof DlcMaLiLibScreen screen) {
                screen.refreshAfterStateChange();
            }
        }, title, message, enableLabel, Component.translatable("dlc_manager.confirm_no")));
    }

    private static void showDependencyPrompt(DlcItem item, DependencyCheck dependencies, fi.dy.masa.malilib.gui.button.ButtonBase button) {
        if (dependencies.names.isEmpty()) {
            return;
        }
        Screen returnScreen = Minecraft.getInstance().screen;
        if (returnScreen instanceof DlcMaLiLibScreen screen) {
            screen.rememberListScroll();
        }
        String list = String.join(", ", dependencies.names);
        Component message = DlcConfirmUtil.buildDependencyPromptMessage(list);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(returnScreen);
            if (!accepted) {
                return;
            }
            for (DlcItem dep : dependencies.items) {
                dep.setEnabled(true);
                DlcRuntimeState.setOverride(dep.getId(), true);
            }
            item.setEnabled(true);
            DlcRuntimeState.setOverride(item.getId(), true);
            updateToggleButtonLabel(button, item);
            if (returnScreen instanceof DlcMaLiLibScreen screen) {
                screen.refreshAfterStateChange();
            }
        }, Component.translatable("dlc_manager.title"), message,
            Component.translatable("dlc_manager.dependency.enable"),
            Component.translatable("dlc_manager.confirm_no")));
    }

    private static void showConflictPrompt(DlcItem item, ConflictCheck conflicts, fi.dy.masa.malilib.gui.button.ButtonBase button) {
        if (conflicts.items.isEmpty()) {
            return;
        }
        Screen returnScreen = Minecraft.getInstance().screen;
        if (returnScreen instanceof DlcMaLiLibScreen screen) {
            screen.rememberListScroll();
        }
        String list = String.join(", ", conflicts.names);
        Component message = DlcConfirmUtil.buildConflictPromptMessage(list);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(returnScreen);
            if (!accepted) {
                return;
            }
            continueEnableAfterConflictResolution(item, button);
            if (returnScreen instanceof DlcMaLiLibScreen screen) {
                screen.refreshAfterStateChange();
            }
        }, Component.translatable("dlc_manager.title"), message,
            Component.translatable("dlc_manager.conflict.resolve"),
            Component.translatable("dlc_manager.confirm_no")));
    }

    private static final class DependencyCheck {
        private final java.util.List<String> names;
        private final java.util.List<DlcItem> items;

        private DependencyCheck(java.util.List<String> names, java.util.List<DlcItem> items) {
            this.names = names;
            this.items = items;
        }
    }

    private static final class ConflictCheck {
        private final java.util.List<String> names;
        private final java.util.List<DlcItem> items;

        private ConflictCheck(java.util.List<String> names, java.util.List<DlcItem> items) {
            this.names = names;
            this.items = items;
        }
    }

    private static final class GroupToggleState {
        private final String label;
        private final boolean enabled;
        private final boolean show;

        private GroupToggleState(String label, boolean enabled, boolean show) {
            this.label = label;
            this.enabled = enabled;
            this.show = show;
        }
    }

    private static String resolvePackId(PackRepository repo, String fileName) {
        if (repo == null) {
            return null;
        }
        String fileId = "file/" + fileName;
        if (repo.isAvailable(fileId)) {
            return fileId;
        }
        if (repo.isAvailable(fileName)) {
            return fileName;
        }
        for (String id : repo.getAvailableIds()) {
            if (id.endsWith("/" + fileName) || id.endsWith(fileName)) {
                return id;
            }
        }
        return null;
    }
}
