package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.packs.repository.PackRepository;

import java.nio.file.Path;
import java.util.List;

public class DlcListEntry extends WidgetListEntryBase<DlcListRow> {
    private static final int COLOR_GROUP = 0xFFBFD7FF;
    private static final int COLOR_PATH = 0xFF9AA7B1;
    private static final int COLOR_ITEM = 0xFFE6E6E6;

    private final DlcListRow row;
    private ButtonGeneric toggleButton;
    private ButtonGeneric groupToggleButton;
    private int groupToggleWidth;
    private int groupToggleHeight;

    public DlcListEntry(int x, int y, int width, int height, boolean isOdd, DlcListRow row, int listIndex) {
        super(x, y, width, height, row, listIndex);
        this.row = row;
        if (row.getType() == DlcListRow.RowType.ITEM) {
            createToggleButton(width, height, row.getItem());
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
        groupToggleWidth = 64;
        groupToggleHeight = 18;
        int bx = this.x + width - groupToggleWidth - 6 - 64 - 6;
        int by = this.y + (height - groupToggleHeight) / 2;
        groupToggleButton = new ButtonGeneric(bx, by, groupToggleWidth, groupToggleHeight, getGroupToggleState(items).label);
        addButton(groupToggleButton, new GroupToggleListener(items));
    }

    @Override
    public void render(int mouseX, int mouseY, boolean hovered, GuiGraphics guiGraphics) {
        if (row.getType() == DlcListRow.RowType.GROUP_HEADER) {
            String label = Component.translatable(row.getGroupType().getDisplayKey()).getString();
            drawStringWithShadow(this.x + 6, this.y + 4, COLOR_GROUP, label, guiGraphics);
        } else if (row.getType() == DlcListRow.RowType.PATH_HEADER) {
            drawString(this.x + 10, this.y + 4, COLOR_PATH, row.getLabel(), guiGraphics);
            if (groupToggleButton != null) {
                GroupToggleState state = getGroupToggleState(row.getItems());
                if (state.show) {
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
        } else if (row.getType() == DlcListRow.RowType.ITEM) {
            DlcItem item = row.getItem();
            boolean dirty = isDirty(item);
            item.setDirty(dirty);
            if (dirty) {
                String suffix = DlcMaLiLibScreen.getPendingSuffix(item);
                String label = item.getName() + suffix;
                guiGraphics.drawString(Minecraft.getInstance().font,
                    Component.literal(label).setStyle(Style.EMPTY.withItalic(true)),
                    this.x + 10, this.y + 4, COLOR_ITEM, false);
            } else {
                drawString(this.x + 10, this.y + 4, COLOR_ITEM, item.getName(), guiGraphics);
            }
            if (toggleButton != null) {
                toggleButton.setDisplayString(getToggleLabel(item));
            }
        }
        super.render(mouseX, mouseY, hovered, guiGraphics);
        if (row.getType() == DlcListRow.RowType.ITEM && isMouseWithin(mouseX, mouseY)) {
            DlcItem item = row.getItem();
                String tooltip = getTooltipText(item);
            if (tooltip != null && !tooltip.isBlank()) {
                var font = Minecraft.getInstance().font;
                int maxWidth = Math.max(160, Math.min(this.width - 20, 280));
                var lines = buildTooltipLines(font, tooltip, maxWidth);
                guiGraphics.renderTooltip(font, lines, mouseX, mouseY);
            }
        }
    }

    private String getToggleLabel(DlcItem item) {
        return item.isEnabled()
            ? Component.translatable("dlc_manager.toggle.on").getString()
            : Component.translatable("dlc_manager.toggle.off").getString();
    }

    private static GroupToggleState getGroupToggleState(java.util.List<DlcItem> items) {
        if (items == null || items.isEmpty()) {
            return new GroupToggleState("", false, false);
        }
        boolean anyEligibleDisabled = items.stream().anyMatch(item -> item.AllowEnableByAll() && !item.isEnabled());
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

    private java.util.List<net.minecraft.util.FormattedCharSequence> buildTooltipLines(net.minecraft.client.gui.Font font, String tooltip, int maxWidth) {
        java.util.ArrayList<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
        for (String line : tooltip.split("\\R", -1)) {
            if (line.isEmpty()) {
                lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
                continue;
            }
            lines.addAll(font.split(Component.literal(line), maxWidth));
        }
        return lines;
    }

    private static class ToggleListener implements IButtonActionListener {
        private final DlcItem item;

        private ToggleListener(DlcItem item) {
            this.item = item;
        }

        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
            item.setEnabled(!item.isEnabled());
            DlcRuntimeState.setOverride(item.getId(), item.isEnabled());
            if (button instanceof ButtonGeneric generic) {
                generic.setDisplayString(item.isEnabled()
                    ? Component.translatable("dlc_manager.toggle.on").getString()
                    : Component.translatable("dlc_manager.toggle.off").getString());
            }
            item.setDirty(isDirty(item));
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
            boolean anyEligibleDisabled = items.stream().anyMatch(item -> item.AllowEnableByAll() && !item.isEnabled());
            boolean target = anyEligibleDisabled;
            for (DlcItem item : items) {
                if (!target || item.AllowEnableByAll()) {
                    item.setEnabled(target);
                    DlcRuntimeState.setOverride(item.getId(), item.isEnabled());
                }
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
        List<Path> targetDirs = switch (item.getType()) {
            case MOD -> List.of(DlcPaths.modsDir());
            case RESOURCE_PACK -> List.of();
            case DATA_PACK -> DlcPathConfig.getDatapackRoots();
            case TACZ_PACK -> List.of(DlcPaths.taczDir());
            default -> DlcPathConfig.getOtherRoots();
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
