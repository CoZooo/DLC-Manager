package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

final class DlcTooltipContext {
    private static List<FormattedCharSequence> lines;
    private static int mouseX;
    private static int mouseY;

    private DlcTooltipContext() {
    }

    static void clear() {
        lines = null;
    }

    static void set(List<FormattedCharSequence> tooltipLines, int x, int y) {
        if (tooltipLines == null || tooltipLines.isEmpty()) {
            return;
        }
        lines = tooltipLines;
        mouseX = x;
        mouseY = y;
    }

    static void render(GuiGraphics guiGraphics, Font font) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        guiGraphics.renderTooltip(font, lines, mouseX, mouseY);
    }
}
