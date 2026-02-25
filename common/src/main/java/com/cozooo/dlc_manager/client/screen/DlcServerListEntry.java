package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerItem;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.core.DlcType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

public class DlcServerListEntry extends WidgetListEntryBase<DlcServerListRow> {
    private static final int COLOR_GROUP = 0xFFBFD7FF;
    private static final int COLOR_PATH = 0xFF9AA7B1;
    private static final int COLOR_ITEM = 0xFFE6E6E6;
    private static final int COLOR_ENABLED = 0xFF7AC37A;

    private final DlcServerListRow row;
    private final DlcServerMaLiLibScreen screen;
    private ButtonGeneric toggleButton;
    private ButtonGeneric groupToggleButton;
    private int groupToggleWidth;
    private int groupToggleHeight;

    public DlcServerListEntry(int x, int y, int width, int height, boolean isOdd, DlcServerListRow row, int listIndex,
                              DlcServerMaLiLibScreen screen) {
        super(x, y, width, height, row, listIndex);
        this.row = row;
        this.screen = screen;
        if (row.getType() == DlcServerListRow.RowType.ITEM) {
            createToggleButton(width, height, row.getItem());
        } else if (row.getType() == DlcServerListRow.RowType.PATH_HEADER && !row.isRootPath()) {
            createGroupToggleButton(width, height, row.getItems());
        }
    }

    private void createToggleButton(int width, int height, DlcServerItem item) {
        int buttonWidth = 64;
        int buttonHeight = 18;
        int bx = this.x + width - buttonWidth - 6;
        int by = this.y + (height - buttonHeight) / 2;
        toggleButton = new ButtonGeneric(bx, by, buttonWidth, buttonHeight, getToggleLabel(item));
        addButton(toggleButton, new ToggleListener(item));
    }

    private void createGroupToggleButton(int width, int height, java.util.List<DlcServerItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        GroupToggleState state = getGroupToggleState(items);
        groupToggleWidth = Math.max(86, Minecraft.getInstance().font.width(state.label) + 16);
        groupToggleHeight = 18;
        int bx = this.x + width - groupToggleWidth - 6 - 64 - 6;
        int by = this.y + (height - groupToggleHeight) / 2;
        groupToggleButton = new ButtonGeneric(bx, by, groupToggleWidth, groupToggleHeight, state.label);
        addButton(groupToggleButton, new GroupToggleListener(items));
    }

    @Override
    public void render(int mouseX, int mouseY, boolean hovered, GuiGraphics guiGraphics) {
        if (row.getType() == DlcServerListRow.RowType.GROUP_HEADER) {
            String label = Component.translatable(row.getGroupType().getDisplayKey()).getString();
            drawStringWithShadow(this.x + 6, this.y + 4, COLOR_GROUP, label, guiGraphics);
        } else if (row.getType() == DlcServerListRow.RowType.PATH_HEADER) {
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
        } else if (row.getType() == DlcServerListRow.RowType.ITEM) {
            DlcServerItem item = row.getItem();
            boolean dirty = screen.isDirty(item);
            int nameColor = item.enabled ? COLOR_ENABLED : COLOR_ITEM;
            String displayName = screen.resolveDisplayName(item);
            if (dirty) {
                String suffix = screen.getPendingSuffix(item);
                String label = displayName + suffix;
                guiGraphics.drawString(Minecraft.getInstance().font,
                    Component.literal(label).setStyle(Style.EMPTY.withItalic(true)),
                    this.x + 10, this.y + 4, nameColor, false);
            } else {
                drawString(this.x + 10, this.y + 4, nameColor, displayName, guiGraphics);
            }
            if (toggleButton != null) {
                toggleButton.setDisplayString(getToggleLabel(item));
                boolean blocked = DlcPresetRuntimeState.isDlcBlocked(item.identifier);
                toggleButton.setEnabled(!blocked);
            }
        }
        super.render(mouseX, mouseY, hovered, guiGraphics);
        if (row.getType() == DlcServerListRow.RowType.ITEM && isMouseWithin(mouseX, mouseY) && !isMouseOverButton(mouseX, mouseY)) {
            DlcServerItem item = row.getItem();
            var font = Minecraft.getInstance().font;
            int maxWidth = Math.max(160, Math.min(this.width - 20, 280));
            var lines = screen.buildTooltipLines(font, item, maxWidth);
            if (!lines.isEmpty()) {
                DlcTooltipContext.set(lines, mouseX, mouseY);
            }
        }
    }

    private boolean isMouseOverButton(int mouseX, int mouseY) {
        if (toggleButton == null) {
            return groupToggleButton != null && isMouseOverButton(groupToggleButton, mouseX, mouseY);
        }
        if (isMouseOverButton(toggleButton, mouseX, mouseY)) {
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

    private String getToggleLabel(DlcServerItem item) {
        return item != null && item.enabled
            ? Component.translatable("dlc_manager.toggle.on").getString()
            : Component.translatable("dlc_manager.toggle.off").getString();
    }

    private GroupToggleState getGroupToggleState(java.util.List<DlcServerItem> items) {
        if (items == null || items.isEmpty()) {
            return new GroupToggleState("", false, false);
        }
        boolean anyEligibleDisabled = items.stream().anyMatch(item -> item != null
            && !DlcPresetRuntimeState.isDlcBlocked(item.identifier)
            && !item.enabled);
        boolean anyEnabled = items.stream().anyMatch(item -> item != null && item.enabled);
        if (anyEligibleDisabled) {
            return new GroupToggleState(Component.translatable("dlc_manager.group.enable").getString(), true, true);
        }
        if (anyEnabled) {
            return new GroupToggleState(Component.translatable("dlc_manager.group.disable").getString(), true, true);
        }
        return new GroupToggleState("", false, false);
    }

    private boolean isMouseWithin(int mouseX, int mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width
            && mouseY >= this.y && mouseY < this.y + this.height;
    }

    public static List<String> getFilterStrings(DlcServerItem item) {
        if (item == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (item.identifier != null) {
            result.add(item.identifier);
        }
        if (item.name != null) {
            result.add(item.name);
        }
        if (item.displayNames != null) {
            result.addAll(item.displayNames.values());
        }
        if (item.tips != null) {
            result.addAll(item.tips.values());
        }
        if (item.type != null) {
            result.add(item.type);
            DlcType mapped = screenType(item);
            if (mapped != null) {
                result.add(typeId(mapped));
            }
        }
        return result;
    }

    private static DlcType screenType(DlcServerItem item) {
        if (item == null || item.type == null) {
            return null;
        }
        String type = item.type.toLowerCase();
        for (DlcType dlcType : DlcType.values()) {
            if (typeId(dlcType).equalsIgnoreCase(type)) {
                return dlcType;
            }
        }
        return null;
    }

    private static String typeId(DlcType type) {
        return switch (type) {
            case MOD -> "mod";
            case RESOURCE_PACK -> "resource";
            case DATA_PACK -> "data";
            case TACZ_PACK -> "tacz";
            case UNKNOWN -> "other";
        };
    }

    private class ToggleListener implements IButtonActionListener {
        private final DlcServerItem item;

        private ToggleListener(DlcServerItem item) {
            this.item = item;
        }

        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
            screen.handleToggle(item, (ButtonGeneric) button);
        }
    }

    private class GroupToggleListener implements IButtonActionListener {
        private final java.util.List<DlcServerItem> items;

        private GroupToggleListener(java.util.List<DlcServerItem> items) {
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
            boolean anyEligibleDisabled = items.stream().anyMatch(item -> item != null
                && !DlcPresetRuntimeState.isDlcBlocked(item.identifier)
                && !item.enabled);
            boolean targetEnable = anyEligibleDisabled;
            if (targetEnable) {
                List<DlcServerItem> targets = items.stream()
                    .filter(item -> item != null
                        && !DlcPresetRuntimeState.isDlcBlocked(item.identifier)
                        && !item.enabled)
                    .toList();
                DependencyBatch deps = collectDependencies(targets);
                ConflictBatch conflicts = collectConflicts(targets);
                if (!conflicts.names.isEmpty()) {
                    Component message = Component.translatable("dlc_manager.conflict.prompt_prefix", String.join(", ", conflicts.names));
                    Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
                        Minecraft.getInstance().setScreen(screen);
                        if (accepted) {
                            screen.disableItemsWithDependents(conflicts.items);
                            enableItems(deps.missingItems, targets);
                        }
                    }, Component.translatable("dlc_manager.conflict.word"), message,
                        Component.translatable("dlc_manager.conflict.resolve"), Component.translatable("gui.cancel")));
                    return;
                }
                if (!deps.names.isEmpty()) {
                    Component message = Component.translatable("dlc_manager.dependency.prompt", String.join(", ", deps.names));
                    Minecraft.getInstance().setScreen(new ConfirmScreen(accepted -> {
                        Minecraft.getInstance().setScreen(screen);
                        if (accepted) {
                            enableItems(deps.missingItems, targets);
                        }
                    }, Component.translatable("dlc_manager.dependency.enable"), message,
                        Component.translatable("dlc_manager.dependency.enable"), Component.translatable("gui.cancel")));
                    return;
                }
                enableItems(deps.missingItems, targets);
            } else {
                screen.disableItemsWithDependents(items);
                screen.updateApplyState();
            }
            if (button instanceof ButtonGeneric generic) {
                state = getGroupToggleState(items);
                generic.setDisplayString(state.label);
                generic.setEnabled(state.enabled);
            }
        }

        private void enableItems(List<DlcServerItem> missing, List<DlcServerItem> targets) {
            if (missing != null) {
                for (DlcServerItem item : missing) {
                    if (item != null) {
                        item.enabled = true;
                    }
                }
            }
            for (DlcServerItem item : targets) {
                if (item != null) {
                    item.enabled = true;
                }
            }
            screen.updateApplyState();
        }

        private DependencyBatch collectDependencies(List<DlcServerItem> targets) {
            DependencyBatch batch = new DependencyBatch();
            if (targets == null || targets.isEmpty()) {
                return batch;
            }
            Set<String> plannedIds = new LinkedHashSet<>();
            Set<String> visited = new LinkedHashSet<>();
            ArrayDeque<DlcServerItem> queue = new ArrayDeque<>();
            for (DlcServerItem item : targets) {
                if (item != null && item.identifier != null) {
                    String key = normalizeIdentifier(item.identifier);
                    if (!key.isBlank() && plannedIds.add(key)) {
                        queue.add(item);
                    }
                }
            }
            while (!queue.isEmpty()) {
                DlcServerItem item = queue.poll();
                if (item == null || item.identifier == null) {
                    continue;
                }
                String itemKey = normalizeIdentifier(item.identifier);
                if (itemKey.isBlank() || !visited.add(itemKey)) {
                    continue;
                }
                if (item.dependencies == null || item.dependencies.isEmpty()) {
                    continue;
                }
                for (String depId : item.dependencies) {
                    if (depId == null || depId.isBlank()) {
                        continue;
                    }
                    String depKey = normalizeIdentifier(depId);
                    if (depKey.isBlank()) {
                        continue;
                    }
                    DlcServerItem dep = screen.getItemByIdentifier(depId);
                    if (dep == null || !dep.enabled) {
                        if (batch.missingIds.add(depKey)) {
                            String name = screen.resolveDisplayName(dep);
                            if (name == null || name.isBlank()) {
                                name = depId;
                            }
                            batch.names.add(name);
                            if (dep != null) {
                                batch.missingItems.add(dep);
                            }
                        }
                    }
                    if (dep != null && dep.identifier != null) {
                        String resolved = normalizeIdentifier(dep.identifier);
                        if (!resolved.isBlank() && plannedIds.add(resolved)) {
                            queue.add(dep);
                        }
                    }
                }
            }
            return batch;
        }

        private ConflictBatch collectConflicts(List<DlcServerItem> targets) {
            ConflictBatch batch = new ConflictBatch();
            if (targets == null || targets.isEmpty()) {
                return batch;
            }
            Set<String> plannedIds = new LinkedHashSet<>();
            Set<String> visited = new LinkedHashSet<>();
            ArrayDeque<DlcServerItem> queue = new ArrayDeque<>();
            for (DlcServerItem item : targets) {
                if (item != null && item.identifier != null) {
                    String key = normalizeIdentifier(item.identifier);
                    if (!key.isBlank() && plannedIds.add(key)) {
                        queue.add(item);
                    }
                }
            }
            while (!queue.isEmpty()) {
                DlcServerItem item = queue.poll();
                if (item == null || item.identifier == null) {
                    continue;
                }
                String itemKey = normalizeIdentifier(item.identifier);
                if (itemKey.isBlank() || !visited.add(itemKey)) {
                    continue;
                }
                if (item.dependencies != null) {
                    for (String depId : item.dependencies) {
                        if (depId == null || depId.isBlank()) {
                            continue;
                        }
                        DlcServerItem dep = screen.getItemByIdentifier(depId);
                        if (dep != null && dep.identifier != null) {
                            String depKey = normalizeIdentifier(dep.identifier);
                            if (!depKey.isBlank() && plannedIds.add(depKey)) {
                                queue.add(dep);
                            }
                        }
                    }
                }
                if (item.conflicts == null || item.conflicts.isEmpty()) {
                    continue;
                }
                for (String conflictId : item.conflicts) {
                    if (conflictId == null || conflictId.isBlank()) {
                        continue;
                    }
                    String conflictKey = normalizeIdentifier(conflictId);
                    if (conflictKey.isBlank() || plannedIds.contains(conflictKey)) {
                        continue;
                    }
                    DlcServerItem conflict = screen.getItemByIdentifier(conflictId);
                    if (conflict != null && conflict.enabled) {
                        if (batch.conflictIds.add(conflictKey)) {
                            batch.names.add(screen.resolveDisplayName(conflict));
                            batch.items.add(conflict);
                        }
                    }
                }
            }
            for (DlcServerItem existing : screen.getItemsSnapshot()) {
                if (existing == null || existing.identifier == null || existing.identifier.isBlank() || !existing.enabled) {
                    continue;
                }
                String existingKey = normalizeIdentifier(existing.identifier);
                if (existingKey.isBlank() || plannedIds.contains(existingKey)) {
                    continue;
                }
                if (existing.conflicts == null || existing.conflicts.isEmpty()) {
                    continue;
                }
                boolean hit = false;
                for (String conflictId : existing.conflicts) {
                    if (conflictId == null || conflictId.isBlank()) {
                        continue;
                    }
                    String conflictKey = normalizeIdentifier(conflictId);
                    if (!conflictKey.isBlank() && plannedIds.contains(conflictKey)) {
                        hit = true;
                        break;
                    }
                }
                if (hit && batch.conflictIds.add(existingKey)) {
                    batch.names.add(screen.resolveDisplayName(existing));
                    batch.items.add(existing);
                }
            }
            return batch;
        }
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        return identifier.trim().toLowerCase(Locale.ROOT);
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

    private static final class DependencyBatch {
        private final Set<String> missingIds = new LinkedHashSet<>();
        private final List<String> names = new ArrayList<>();
        private final List<DlcServerItem> missingItems = new ArrayList<>();
    }

    private static final class ConflictBatch {
        private final Set<String> conflictIds = new LinkedHashSet<>();
        private final List<String> names = new ArrayList<>();
        private final List<DlcServerItem> items = new ArrayList<>();
    }
}
