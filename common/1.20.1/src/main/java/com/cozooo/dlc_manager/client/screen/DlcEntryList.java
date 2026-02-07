package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.DlcItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

public class DlcEntryList extends ObjectSelectionList<DlcEntryList.Entry> {
    private final List<DlcItem> items = new ArrayList<>();

    public DlcEntryList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
        super(minecraft, width, height, top, bottom, itemHeight);
        setRenderBackground(false);
    }

    public void setEntries(List<Entry> entries) {
        clearEntries();
        items.clear();
        for (Entry entry : entries) {
            addEntry(entry);
            if (entry instanceof DlcItemEntry itemEntry) {
                items.add(itemEntry.getItem());
            }
        }
    }

    public List<DlcItem> getItems() {
        return items;
    }

    @Override
    protected int getScrollbarPosition() {
        return width - 6;
    }

    public abstract static class Entry extends ObjectSelectionList.Entry<Entry> {
        @Override
        public Component getNarration() {
            return Component.empty();
        }
    }

    public static class GroupHeaderEntry extends Entry {
        private final Component label;

        public GroupHeaderEntry(Component label) {
            this.label = label;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int color = 0xFFD0D0D0;
            guiGraphics.drawString(Minecraft.getInstance().font, label, x + 4, y + 6, color, false);
        }
    }

    public static class PathHeaderEntry extends Entry {
        private final Component label;

        public PathHeaderEntry(Component label) {
            this.label = label;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int color = 0xFF9AA7B1;
            guiGraphics.drawString(Minecraft.getInstance().font, label, x + 14, y + 6, color, false);
        }
    }

    public static class DlcItemEntry extends Entry {
        private final DlcItem item;
        private final Checkbox checkbox;

        public DlcItemEntry(DlcItem item) {
            this.item = item;
            this.checkbox = new Checkbox(0, 0, 20, 20, Component.empty(), item.isEnabled());
        }

        public DlcItem getItem() {
            return item;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            checkbox.setX(x + 6);
            checkbox.setY(y + 3);
            checkbox.render(guiGraphics, mouseX, mouseY, partialTick);

            int color = 0xFFE0E0E0;
            guiGraphics.drawString(Minecraft.getInstance().font, item.getName(), x + 30, y + 7, color, false);

            if (hovered) {
                int infoColor = 0xFF7AA2FF;
                guiGraphics.drawString(Minecraft.getInstance().font, Component.translatable(item.getType().getDisplayKey()), x + width - 110, y + 7, infoColor, false);
            }

            item.setEnabled(checkbox.selected());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (checkbox.mouseClicked(mouseX, mouseY, button)) {
                item.setEnabled(checkbox.selected());
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }
}
