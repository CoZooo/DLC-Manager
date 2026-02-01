package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DlcPresetManagerScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int CHECKBOX_SIZE = 14;
    private static final int CHECKBOX_X = 24;
    private static final int LABEL_GAP = 8;

    private final Screen parent;
    private List<DlcPresetEntry> presets = List.of();
    private final Map<String, PresetToggleButton> entryButtons = new HashMap<>();
    private int scrollIndex;
    private List<String> descLines = List.of();
    private Button backButton;

    public DlcPresetManagerScreen(Screen parent) {
        super(Component.translatable("dlc_manager.preset.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reloadPresets();
        int buttonWidth = 140;
        int buttonHeight = 20;
        int y = this.height - 40;
        backButton = Button.builder(Component.translatable("dlc_manager.back"), button -> handleBack())
            .bounds(this.width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight)
            .build();
        addRenderableWidget(backButton);
        buildEntryButtons();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int centerX = this.width / 2;
        int y = 20;
        guiGraphics.drawCenteredString(this.font, getTitle(), centerX, y, 0xFFFFFF);
        y += 18;
        for (String line : descLines) {
            guiGraphics.drawCenteredString(this.font, line, centerX, y, 0xC8C8C8);
            y += this.font.lineHeight + 2;
        }
        y += 10;

        int listTop = y;
        int listBottom = this.height - 70;
        int maxVisible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int totalLines = presets.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }

        int rowY = listTop;
        String lang = Minecraft.getInstance().options.languageCode;
        for (int i = 0; i < presets.size(); i++) {
            DlcPresetEntry entry = presets.get(i);
            PresetToggleButton button = entryButtons.get(entry.getId());
            boolean visible = i >= scrollIndex && i < scrollIndex + maxVisible;
            if (!visible) {
                if (button != null) {
                    button.visible = false;
                }
                continue;
            }
            if (button != null) {
                button.visible = true;
                button.setY(rowY + (ROW_HEIGHT - CHECKBOX_SIZE) / 2);
                button.setX(CHECKBOX_X);
                updateButtonState(button, entry);
            }
            boolean blocked = DlcPresetRuntimeState.isPresetBlocked(entry.getId());
            int color = blocked ? 0x888888 : 0xE6E6E6;
            int labelY = rowY + (ROW_HEIGHT - this.font.lineHeight) / 2;
            guiGraphics.drawString(this.font, entry.resolveDisplayName(lang), CHECKBOX_X + CHECKBOX_SIZE + LABEL_GAP, labelY, color, false);
            if (isMouseOverRow(mouseX, mouseY, rowY, ROW_HEIGHT) && !isMouseOverButton(button, mouseX, mouseY)) {
                renderTooltip(guiGraphics, entry, mouseX, mouseY, lang);
            }
            rowY += ROW_HEIGHT;
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listTop = getListTop();
        int listBottom = this.height - 70;
        int maxVisible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int totalLines = presets.size();
        int maxScroll = Math.max(0, totalLines - maxVisible);
        if (maxScroll > 0) {
            int step = delta > 0 ? -1 : 1;
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        clearWidgets();
        entryButtons.clear();
        init();
    }

    private void reloadPresets() {
        DlcPresetRuntimeState.reload();
        presets = DlcPresetRuntimeState.getPresets();
        descLines = splitLines(Component.translatable("dlc_manager.preset.desc").getString());
    }

    private void buildEntryButtons() {
        entryButtons.clear();
        for (DlcPresetEntry entry : presets) {
            if (entry == null) {
                continue;
            }
            PresetToggleButton button = new PresetToggleButton(entry.getId(), btn -> togglePreset(entry));
            button.setX(CHECKBOX_X);
            button.setY(0);
            entryButtons.put(entry.getId(), button);
            addRenderableWidget(button);
        }
    }

    private void togglePreset(DlcPresetEntry entry) {
        if (entry == null) {
            return;
        }
        if (DlcPresetRuntimeState.isPresetBlocked(entry.getId())) {
            return;
        }
        boolean enabled = !DlcPresetRuntimeState.isPresetEnabled(entry.getId());
        DlcPresetRuntimeState.setPresetEnabled(entry.getId(), enabled);
        if (parent instanceof DlcMaLiLibScreen screen) {
            screen.refreshAfterStateChange();
        } else if (parent instanceof DlcServerMaLiLibScreen server) {
            server.refreshAfterPresetChange();
        }
    }

    private void updateButtonState(PresetToggleButton button, DlcPresetEntry entry) {
        boolean blocked = DlcPresetRuntimeState.isPresetBlocked(entry.getId());
        button.setMessage(Component.empty());
        button.active = !blocked;
    }

    private void renderTooltip(GuiGraphics guiGraphics, DlcPresetEntry entry, int mouseX, int mouseY, String lang) {
        if (entry == null) {
            return;
        }
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        int maxWidth = Math.min(260, this.width - 40);
        String tip = entry.resolveTip(lang);
        if (tip != null && !tip.isBlank()) {
            for (String line : tip.split("\\R", -1)) {
                if (line.isEmpty()) {
                    lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
                    continue;
                }
                lines.addAll(this.font.split(Component.literal(line), maxWidth));
            }
        }
        List<String> blockers = DlcPresetRuntimeState.getPresetBlockerNames(entry.getId(), lang);
        if (!blockers.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            }
            for (String blocker : blockers) {
                Component line = Component.translatable("dlc_manager.preset.conflict", blocker)
                    .withStyle(ChatFormatting.DARK_RED);
                lines.addAll(this.font.split(line, maxWidth));
            }
        }
        if (!lines.isEmpty()) {
            guiGraphics.renderTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (text.contains("\n")) {
            String[] parts = text.split("\\n");
            List<String> lines = new ArrayList<>();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    lines.add(part);
                }
            }
            if (!lines.isEmpty()) {
                return lines;
            }
        }
        return new ArrayList<>(List.of(text));
    }

    private boolean isMouseOverRow(int mouseX, int mouseY, int rowY, int lineHeight) {
        return mouseX >= 20 && mouseX <= this.width - 20 && mouseY >= rowY && mouseY < rowY + lineHeight;
    }

    private boolean isMouseOverButton(Button button, int mouseX, int mouseY) {
        if (button == null || !button.visible) {
            return false;
        }
        int bx = button.getX();
        int by = button.getY();
        int bw = button.getWidth();
        int bh = button.getHeight();
        return mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
    }

    private int getListTop() {
        int y = 20 + 18;
        y += descLines.size() * (this.font.lineHeight + 2);
        y += 10;
        return y;
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
        float scrollRatio = maxScroll == 0 ? 0 : (float) scrollIndex / (float) maxScroll;
        int barY = top + Math.round(scrollRatio * maxTrack);
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF22262C);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF8A9099);
    }

    private void handleBack() {
        Minecraft.getInstance().setScreen(parent);
    }

    private static int checkColor(boolean enabled, boolean blocked) {
        if (blocked) {
            return 0xFF5E5E5E;
        }
        return enabled ? 0xFF7BFF4A : 0xFFE0E0E0;
    }

    private static int borderColor(boolean blocked, boolean hovered) {
        if (blocked) {
            return 0xFF3A3A3A;
        }
        return hovered ? 0xFFFFFFFF : 0xFF9A9A9A;
    }

    private class PresetToggleButton extends Button {
        private final String presetId;

        private PresetToggleButton(String presetId, OnPress onPress) {
            super(0, 0, CHECKBOX_SIZE, CHECKBOX_SIZE, Component.empty(), onPress, DEFAULT_NARRATION);
            this.presetId = presetId;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            DlcPresetStatus status = resolvePresetStatus(presetId);
            boolean blocked = DlcPresetRuntimeState.isPresetBlocked(presetId);
            boolean hovered = this.isHoveredOrFocused();
            int x0 = getX();
            int y0 = getY();
            int x1 = x0 + getWidth();
            int y1 = y0 + getHeight();
            int border = borderColor(blocked, hovered);
            int bg = blocked ? 0xFF1C1C1C : (hovered ? 0xFF2A2A2A : 0xFF151515);
            guiGraphics.fill(x0, y0, x1, y1, bg);
            guiGraphics.fill(x0, y0, x1, y0 + 1, border);
            guiGraphics.fill(x0, y1 - 1, x1, y1, border);
            guiGraphics.fill(x0, y0, x0 + 1, y1, border);
            guiGraphics.fill(x1 - 1, y0, x1, y1, border);
            if (status == DlcPresetStatus.ON) {
                int color = checkColor(true, blocked);
                int size = 8;
                int cx = x0 + (getWidth() - size) / 2;
                int cy = y0 + (getHeight() - size) / 2;
                guiGraphics.fill(cx, cy + 4, cx + 2, cy + 6, color);
                guiGraphics.fill(cx + 2, cy + 6, cx + 4, cy + 8, color);
                guiGraphics.fill(cx + 4, cy + 4, cx + 6, cy + 6, color);
                guiGraphics.fill(cx + 6, cy + 2, cx + 8, cy + 4, color);
            } else if (status == DlcPresetStatus.PART) {
                int color = checkColor(true, blocked);
                int size = 6;
                int cx = x0 + (getWidth() - size) / 2;
                int cy = y0 + (getHeight() - size) / 2;
                guiGraphics.fill(cx, cy, cx + size, cy + size, color);
            }
        }
    }

    private DlcPresetStatus resolvePresetStatus(String presetId) {
        if (parent instanceof DlcServerMaLiLibScreen server) {
            return server.getPresetStatus(presetId);
        }
        return DlcPresetRuntimeState.getPresetStatus(presetId);
    }
}
