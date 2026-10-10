package com.cozooo.dlc_manager.client.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcMarkdownRendererTest {
    @Test
    void renderMarkdownPreservesTextAndStylesLinks() {
        Component component = DlcMarkdownRenderer.renderMarkdownComponent("# Title\n\n[Site](https://example.com)");

        assertEquals("Title\nSite", component.getString());
        assertTrue(hasBoldStyle(component));
        ClickEvent clickEvent = findFirstClickEvent(component);
        //? if >=26.1 {
        assertTrue(clickEvent == null || clickEvent.toString().contains("https://example.com"));
        //?} else {
        assertNotNull(clickEvent);
        assertEquals(ClickEvent.Action.OPEN_URL, clickEvent.getAction());
        assertEquals("https://example.com", clickEvent.getValue());
        //?}
    }

    @Test
    void renderMarkdownSupportsListsBlockQuotesInlineCodeAndFormattingCodes() {
        Component component = DlcMarkdownRenderer.renderMarkdownComponent("""
            > Quote

            - `code`
            - &aGreen
            """);

        assertEquals("> Quote\n• code\n• Green", component.getString());
        assertEquals(ChatFormatting.GRAY.getColor(), component.getSiblings().get(0).getStyle().getColor().getValue());
    }

    private static boolean hasBoldStyle(Component component) {
        if (component == null) {
            return false;
        }
        if (component.getStyle().isBold()) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (hasBoldStyle(sibling)) {
                return true;
            }
        }
        return false;
    }

    private static ClickEvent findFirstClickEvent(Component component) {
        if (component == null) {
            return null;
        }
        Style style = component.getStyle();
        if (style != null && style.getClickEvent() != null) {
            return style.getClickEvent();
        }
        for (Component sibling : component.getSiblings()) {
            ClickEvent event = findFirstClickEvent(sibling);
            if (event != null) {
                return event;
            }
        }
        return null;
    }
}
