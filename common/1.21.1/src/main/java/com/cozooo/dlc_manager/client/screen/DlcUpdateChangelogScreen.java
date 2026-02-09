package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;

public class DlcUpdateChangelogScreen extends Screen {
    private final Screen parent;
    private final String changelog;
    private final Runnable onDone;
    private Button okButton;
    private List<FormattedCharSequence> lines = List.of();
    private int scrollIndex;

    public DlcUpdateChangelogScreen(Screen parent, String changelog, Runnable onDone) {
        super(Component.translatable("dlc_manager.update.changelog_title"));
        this.parent = parent;
        this.changelog = changelog == null ? "" : changelog;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int y = this.height - 40;
        okButton = Button.builder(Component.translatable("dlc_manager.update.changelog_button"), button -> finish())
            .bounds(this.width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(okButton);
        rebuildLines();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        rebuildLines();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int y = 20;
        int titleX = (this.width - this.font.width(getTitle())) / 2;
        guiGraphics.drawString(this.font, getTitle(), titleX, y, 0xFFFFFF, false);
        y += 18;
        drawLines(guiGraphics, y);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawLines(GuiGraphics guiGraphics, int startY) {
        int listTop = startY;
        int listBottom = this.height - 60;
        int lineHeight = 10;
        int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
        int totalLines = lines.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }
        int y = listTop;
        int end = Math.min(totalLines, scrollIndex + maxVisible);
        for (int i = scrollIndex; i < end; i++) {
            FormattedCharSequence line = lines.get(i);
            guiGraphics.drawString(this.font, line, 24, y, 0xE6E6E6, false);
            y += lineHeight;
        }
    }

    private void rebuildLines() {
        List<FormattedCharSequence> built = new ArrayList<>();
        int maxWidth = Math.max(40, this.width - 48);
        String[] parts = changelog.split("\\R", -1);
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                built.add(FormattedCharSequence.EMPTY);
                continue;
            }
            built.addAll(this.font.split(Component.literal(part), maxWidth));
        }
        lines = built;
        scrollIndex = 0;
    }

    private void finish() {
        if (onDone != null) {
            onDone.run();
            if (Minecraft.getInstance().screen == this) {
                Minecraft.getInstance().setScreen(parent);
            }
        } else {
            Minecraft.getInstance().setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        finish();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && okButton != null && okButton.isMouseOver(mouseX, mouseY)) {
            finish();
            return true;
        }
        if (okButton != null && okButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int step = deltaY > 0 ? -1 : 1;
        scrollIndex = Math.max(0, scrollIndex + step);
        return true;
    }
}
