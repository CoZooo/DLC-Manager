package com.cozooo.dlc_manager.client.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcTextFormatterTest {
    @Test
    void parseSupportsLegacyColorAndFormattingCodes() {
        Component component = DlcTextFormatter.parse("&aGreen &lBold");

        assertEquals("Green Bold", component.getString());
        Style green = component.getSiblings().get(0).getStyle();
        Style bold = component.getSiblings().get(1).getStyle();
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), green.getColor());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), bold.getColor());
        assertTrue(bold.isBold());
    }

    @Test
    void parseSupportsHexColorsAndResetDropsBaseStyle() {
        Style base = Style.EMPTY.withItalic(true);
        Component component = DlcTextFormatter.parse("Base &#336699Hex &rPlain", base);

        assertEquals("Base Hex Plain", component.getString());
        assertTrue(component.getSiblings().get(0).getStyle().isItalic());
        assertEquals(TextColor.fromRgb(0x336699), component.getSiblings().get(1).getStyle().getColor());
        assertFalse(component.getSiblings().get(2).getStyle().isItalic());
        assertNull(component.getSiblings().get(2).getStyle().getColor());
    }

    @Test
    void parseLeavesUnknownMarkersAsText() {
        Component component = DlcTextFormatter.parse("&x keep");

        assertEquals("&x keep", component.getString());
    }
}
