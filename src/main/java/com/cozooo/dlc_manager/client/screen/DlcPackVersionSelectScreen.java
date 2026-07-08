package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DlcPackVersionSelectScreen extends DlcScreenCompat.MouseScroll {
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_HEIGHT = 26;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLL_STEP = 24;

    private final DlcPackManagerScreen parent;
    private final String identifier;
    private final Component displayName;
    private final List<String> versions;
    private final String selectedKey;
    private final String installedVersion;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private double scrollbarDragOffset;

    public DlcPackVersionSelectScreen(
        DlcPackManagerScreen parent,
        String identifier,
        Component displayName,
        List<String> versions,
        String selectedKey,
        String installedVersion
    ) {
        super(Component.translatable("dlc_manager.pack.version_select_title"));
        this.parent = parent;
        this.identifier = identifier;
        this.displayName = displayName;
        this.versions = versions;
        this.selectedKey = selectedKey;
        this.installedVersion = installedVersion;
    }

    @Override
    protected void init() {
        clearWidgets();
        int buttonWidth = 180;
        clampScroll();
        addRenderableWidget(Button.builder(Component.translatable("dlc_manager.pack.back"), ignored -> Minecraft.getInstance().setScreen(parent))
            .bounds(this.width / 2 - buttonWidth / 2, this.height - 28, buttonWidth, BUTTON_HEIGHT)
            .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, displayName, this.width / 2, 32, 0xC8C8C8);
        renderVersionList(guiGraphics, mouseX, mouseY);
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (mouseY < getListTop() || mouseY > getListBottom()) {
            return false;
        }
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return false;
        }
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (deltaY > 0 ? -SCROLL_STEP : SCROLL_STEP)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isInsideScrollbar(mouseX, mouseY)) {
                draggingScrollbar = true;
                scrollbarDragOffset = scrollbarDragOffset(mouseY);
                updateScrollFromMouse(mouseY);
                return true;
            }
            int contentRight = getListRight() - SCROLLBAR_WIDTH - 4;
            if (mouseX >= getListLeft() && mouseX < contentRight && mouseY >= getListTop() && mouseY < getListBottom()) {
                int index = (int) ((mouseY - getListTop() + scrollOffset) / ROW_HEIGHT);
                if (index >= 0 && index < versions.size()) {
                    String versionKey = versions.get(index);
                    if (!versionKey.equals(selectedKey)) {
                        parent.selectPackVersion(identifier, versionKey);
                    }
                    Minecraft.getInstance().setScreen(parent);
                    return true;
                }
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

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void renderVersionList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        clampScroll();
        int contentRight = getListRight() - SCROLLBAR_WIDTH - 4;
        guiGraphics.enableScissor(getListLeft(), getListTop(), contentRight, getListBottom());
        int y = getListTop() - scrollOffset;
        for (int i = 0; i < versions.size(); i++) {
            String versionKey = versions.get(i);
            if (y + BUTTON_HEIGHT > getListTop() && y < getListBottom()) {
                boolean hover = mouseX >= getListLeft() && mouseX < contentRight && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
                boolean selected = versionKey.equals(selectedKey);
                int color = hover ? 0xFF5A5A5A : selected ? 0xFF454545 : 0xFF303030;
                guiGraphics.fill(getListLeft(), y, contentRight, y + BUTTON_HEIGHT, color);
                guiGraphics.drawCenteredString(this.font, versionLabel(versionKey), (getListLeft() + contentRight) / 2, y + 6, selected ? 0xAFC8FF : 0xFFFFFF);
            }
            y += ROW_HEIGHT;
            if (y > getListBottom()) {
                break;
            }
        }
        guiGraphics.disableScissor();
        drawScrollBar(guiGraphics);
    }

    private Component versionLabel(String versionKey) {
        String version = versionKey.substring(versionKey.lastIndexOf('@') + 1);
        return version.equals(installedVersion)
            ? Component.translatable("dlc_manager.pack.version_installed", version)
            : Component.literal(version);
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(getMaxScroll(), scrollOffset));
    }

    private int getMaxScroll() {
        return Math.max(0, getContentHeight() - (getListBottom() - getListTop()));
    }

    private int getContentHeight() {
        return versions.size() * ROW_HEIGHT;
    }

    private boolean isInsideScrollbar(double mouseX, double mouseY) {
        if (getMaxScroll() <= 0) {
            return false;
        }
        int x = getListRight() - SCROLLBAR_WIDTH;
        return mouseX >= x && mouseX <= x + SCROLLBAR_WIDTH && mouseY >= getListTop() && mouseY <= getListBottom();
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            scrollOffset = 0;
            return;
        }
        int travel = getListBottom() - getListTop() - scrollbarHeight();
        double ratio = Math.max(0.0, Math.min(1.0, (mouseY - getListTop() - scrollbarDragOffset) / travel));
        scrollOffset = Math.max(0, Math.min(maxScroll, (int) (ratio * maxScroll)));
    }

    private double scrollbarDragOffset(double mouseY) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return 0.0;
        }
        int travel = getListBottom() - getListTop() - scrollbarHeight();
        int barY = getListTop() + (int) (travel * (scrollOffset / (double) maxScroll));
        return mouseY - barY;
    }

    private void drawScrollBar(GuiGraphics guiGraphics) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int barHeight = scrollbarHeight();
        int travel = getListBottom() - getListTop() - barHeight;
        int barY = getListTop() + (int) (travel * (scrollOffset / (double) maxScroll));
        int x = getListRight() - SCROLLBAR_WIDTH;
        guiGraphics.fill(x, getListTop(), x + SCROLLBAR_WIDTH, getListBottom(), 0x663A3F48);
        guiGraphics.fill(x, barY, x + SCROLLBAR_WIDTH, barY + barHeight, 0xFF8A95A8);
    }

    private int scrollbarHeight() {
        int viewport = getListBottom() - getListTop();
        return Math.max(20, (int) (viewport * (viewport / (double) getContentHeight())));
    }

    private int getListLeft() {
        return this.width / 2 - 110;
    }

    private int getListRight() {
        return this.width / 2 + 110;
    }

    private int getListTop() {
        return 48;
    }

    private int getListBottom() {
        return Math.max(getListTop() + BUTTON_HEIGHT, this.height - 40);
    }
}
