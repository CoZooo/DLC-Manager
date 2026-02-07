package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry;
import com.cozooo.dlc_manager.core.DlcType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.packs.repository.PackRepository;
import java.nio.file.Path;
import java.util.List;

public class DlcListEntry extends WidgetListEntryBase<DlcListRow> {
    private static final int COLOR_GROUP = 0xFFBFD7FF;
    private static final int COLOR_PATH = 0xFF9AA7B1;
    private static final int COLOR_ITEM = 0xFFE6E6E6;
    private static final int COLOR_ENABLED = 0xFF7AC37A;
    private static final int COLOR_REDIRECT = 0xFFBFD7FF;

    private final DlcListRow row;
    private ButtonGeneric toggleButton;
    private ButtonGeneric groupToggleButton;
    private ButtonGeneric redirectButton;
    private int groupToggleWidth;
    private int groupToggleHeight;

    public DlcListEntry(int x, int y, int width, int height, boolean isOdd, DlcListRow row, int listIndex) {
        super(x, y, width, height, row, listIndex);
        this.row = row;
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
        int by = this.y + (height - buttonHeight) / 2;
        toggleButton = new ButtonGeneric(bx, by, buttonWidth, buttonHeight, getToggleLabel(item));
        addButton(toggleButton, new ToggleListener(item));
    }

    private void createGroupToggleButton(int width, int height, java.util.List<DlcItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        groupToggleWidth = Math.max(86, Minecraft.getInstance().font.width(getGroupToggleState(items).label) + 16);
        groupToggleHeight = 18;
        int bx = this.x + width - groupToggleWidth - 6 - 64 - 6;
        int by = this.y + (height - groupToggleHeight) / 2;
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
        int by = this.y + (height - buttonHeight) / 2;
        redirectButton = new ButtonGeneric(bx, by, buttonWidth, buttonHeight,
            Component.translatable("dlc_manager.redirect.open").getString());
        addButton(redirectButton, new RedirectListener(entry));
    }

    @Override
    public void render(int mouseX, int mouseY, boolean hovered, GuiGraphics guiGraphics) {
        if (row.getType() == DlcListRow.RowType.GROUP_HEADER) {
            String label = Component.translatable(row.getGroupType().getDisplayKey()).getString();
            drawStringWithShadow(this.x + 6, this.y + 4, COLOR_GROUP, label, guiGraphics);
        } else if (row.getType() == DlcListRow.RowType.REDIRECT_HEADER) {
            drawStringWithShadow(this.x + 6, this.y + 4, COLOR_REDIRECT, row.getLabel(), guiGraphics);
        } else if (row.getType() == DlcListRow.RowType.PATH_HEADER) {
            drawString(this.x + 10, this.y + 4, COLOR_PATH, row.getLabel(), guiGraphics);
            if (groupToggleButton != null) {
                GroupToggleState state = getGroupToggleState(row.getItems());
                if (state.show) {
                    groupToggleWidth = Math.max(86, Minecraft.getInstance().font.width(state.label) + 16);
                    int bx = this.x + this.width - groupToggleWidth - 6 - 64 - 6;
                    int by = this.y + (this.height - groupToggleHeight) / 2;
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
            DlcRedirectEntry entry = row.getRedirectEntry();
            String lang = Minecraft.getInstance().options.languageCode;
            String label = entry == null ? "" : entry.resolveDisplayName(lang);
            drawString(this.x + 10, this.y + 4, COLOR_ITEM, label, guiGraphics);
            if (redirectButton != null) {
                int bx = this.x + this.width - redirectButton.getWidth() - 6;
                int by = this.y + (this.height - redirectButton.getHeight()) / 2;
                redirectButton.setX(bx);
                redirectButton.setY(by);
            }
        } else if (row.getType() == DlcListRow.RowType.ITEM) {
            DlcItem item = row.getItem();
            boolean dirty = isDirty(item);
            item.setDirty(dirty);
            int nameColor = item.isEnabled() ? COLOR_ENABLED : COLOR_ITEM;
            String displayName = DlcDisplayNameUtil.resolveDisplayName(item);
            if (dirty) {
                String suffix = DlcMaLiLibScreen.getPendingSuffix(item);
                String label = displayName + suffix;
                guiGraphics.drawString(Minecraft.getInstance().font,
                    Component.literal(label).setStyle(Style.EMPTY.withItalic(true)),
                    this.x + 10, this.y + 4, nameColor, false);
            } else {
                drawString(this.x + 10, this.y + 4, nameColor, displayName, guiGraphics);
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
                    java.util.ArrayList<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
                    for (String line : tip.split("\\R", -1)) {
                        if (line.isEmpty()) {
                            lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
                            continue;
                        }
                        lines.addAll(font.split(Component.literal(line), maxWidth));
                    }
                    if (!lines.isEmpty()) {
                        DlcTooltipContext.set(lines, mouseX, mouseY);
                    }
                }
            }
        }
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

    private String getToggleLabel(DlcItem item) {
        return item.isEnabled()
            ? Component.translatable("dlc_manager.toggle.on").getString()
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
        if (tooltip != null && !tooltip.isBlank()) {
            for (String line : tooltip.split("\\R", -1)) {
                if (line.isEmpty()) {
                    lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
                    continue;
                }
                lines.addAll(font.split(Component.literal(line), maxWidth));
            }
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
                lines.addAll(font.split(Component.literal(DlcDisplayNameUtil.resolveDisplayName(dep)), maxWidth));
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
                lines.addAll(font.split(Component.literal(DlcDisplayNameUtil.resolveDisplayName(dep)), maxWidth));
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
                lines.addAll(font.split(Component.literal(DlcDisplayNameUtil.resolveDisplayName(conflict)), maxWidth));
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
                    showBatchConflictPrompt(conflicts.getLines());
                    return;
                }
                DlcRuntimeState.DependencyBatchResult deps = DlcRuntimeState.collectMissingDependencies(targets);
                if (!deps.getLines().isEmpty()) {
                    Component scopeLabel = Component.translatable("dlc_manager.scope.group");
                    showBatchDependencyPrompt(deps.getLines(), deps.getItemsToEnable(), targets, scopeLabel);
                    return;
                }
                enableItems(targets, true);
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

    private static boolean isDirty(DlcItem item) {
        return DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled());
    }

    private static void updateToggleButtonLabel(fi.dy.masa.malilib.gui.button.ButtonBase button, DlcItem item) {
        if (button instanceof ButtonGeneric generic) {
            generic.setDisplayString(item.isEnabled()
                ? Component.translatable("dlc_manager.toggle.on").getString()
                : Component.translatable("dlc_manager.toggle.off").getString());
        }
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
        java.util.List<String> conflicts = item.getConflicts();
        if (conflicts == null || conflicts.isEmpty()) {
            return new ConflictCheck(java.util.List.of(), java.util.List.of());
        }
        java.util.List<DlcItem> toDisable = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String name : conflicts) {
            if (name == null || name.isBlank()) {
                continue;
            }
        DlcItem conflictItem = DlcRuntimeState.getItemByIdentifier(name);
            if (conflictItem != null && conflictItem.isEnabled()) {
                toDisable.add(conflictItem);
                names.add(DlcDisplayNameUtil.resolveDisplayName(name));
            }
        }
        return new ConflictCheck(names, toDisable);
    }

    private static Component buildConflictPromptMessage(String list) {
        return Component.translatable("dlc_manager.conflict.prompt_prefix", list)
            .append(Component.translatable("dlc_manager.conflict.word").withStyle(net.minecraft.ChatFormatting.DARK_RED));
    }

    private static Component buildConflictLine(String left, String right) {
        return Component.translatable("dlc_manager.batch.conflicts_line_prefix", left, right)
            .append(Component.translatable("dlc_manager.conflict.word").withStyle(net.minecraft.ChatFormatting.DARK_RED));
    }

    private static Component joinLines(java.util.List<Component> lines) {
        net.minecraft.network.chat.MutableComponent result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result = result.append(Component.literal("\n"));
            }
            result = result.append(lines.get(i));
        }
        return result;
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

    private static void showBatchConflictPrompt(java.util.List<DlcRuntimeState.ConflictLine> lines) {
        java.util.List<Component> components = new java.util.ArrayList<>();
        for (DlcRuntimeState.ConflictLine line : lines) {
            components.add(buildConflictLine(
                DlcDisplayNameUtil.resolveDisplayName(line.getLeftName()),
                DlcDisplayNameUtil.resolveDisplayName(line.getRightName())));
        }
        Component message = joinLines(components);
        Screen returnScreen = Minecraft.getInstance().screen;
        if (returnScreen instanceof DlcMaLiLibScreen screen) {
            screen.rememberListScroll();
        }
        Component title = Component.translatable("dlc_manager.batch.conflicts_title")
            .withStyle(net.minecraft.ChatFormatting.RED);
        Minecraft.getInstance().setScreen(new DlcSingleButtonScreen(returnScreen, title, message,
            Component.translatable("dlc_manager.confirm_no")));
    }

    private static void showBatchDependencyPrompt(java.util.List<DlcRuntimeState.DependencyLine> lines,
        java.util.List<DlcItem> depsToEnable, java.util.List<DlcItem> targets, Component scopeLabel) {
        java.util.List<Component> components = new java.util.ArrayList<>();
        for (DlcRuntimeState.DependencyLine line : lines) {
            components.add(Component.translatable("dlc_manager.batch.dependencies_line",
                DlcDisplayNameUtil.resolveDisplayName(line.getItemName()),
                DlcDisplayNameUtil.resolveDisplayName(line.getDependencyName())));
        }
        Component message = joinLines(components);
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
            enableItems(depsToEnable, true);
            enableItems(targets, true);
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
        Component message = Component.translatable("dlc_manager.dependency.prompt", list);
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
        Component message = buildConflictPromptMessage(list);
        Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
            Minecraft.getInstance().setScreen(returnScreen);
            if (!accepted) {
                return;
            }
            for (DlcItem conflict : conflicts.items) {
                conflict.setEnabled(false);
                DlcRuntimeState.setOverride(conflict.getId(), false);
            }
            item.setEnabled(true);
            DlcRuntimeState.setOverride(item.getId(), true);
            updateToggleButtonLabel(button, item);
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

    private static boolean isEnabledInTargets(DlcItem item) {
        String name = item.getSourcePath().getFileName().toString();
        if (item.getType() == DlcType.UNKNOWN) {
            Path target = DlcPaths.resolveAppliedTarget(item.getAppliedTargetRaw());
            if (target == null) {
                return false;
            }
            boolean dirTarget = DlcPaths.isAppliedTargetDirectory(item.getAppliedTargetRaw(), target, item.getSourcePath());
            if (dirTarget) {
                return java.nio.file.Files.exists(target.resolve(name));
            }
            return java.nio.file.Files.exists(target);
        }
        List<Path> targetDirs = switch (item.getType()) {
            case MOD -> List.of(DlcPaths.modsDir());
            case RESOURCE_PACK -> List.of();
            case DATA_PACK -> DlcPathConfig.getDatapackRoots();
            case TACZ_PACK -> List.of(DlcPaths.taczDir());
            default -> List.of();
        };
        if (item.getType() == DlcType.RESOURCE_PACK) {
            return isResourcePackSelected(name);
        }
        if (targetDirs.isEmpty()) {
            return false;
        }
        for (Path dir : targetDirs) {
            if (!java.nio.file.Files.exists(dir.resolve(name))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isResourcePackSelected(String fileName) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            PackRepository repo = minecraft.getResourcePackRepository();
            String packId = resolvePackId(repo, fileName);
            if (packId == null) {
                return false;
            }
            return repo.getSelectedIds().contains(packId);
        } catch (Throwable ignored) {
            return false;
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
