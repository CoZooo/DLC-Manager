package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Size;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DlcUiScrollTest {
    private Element output(double height) {
        Element element = new Element(null, "div");
        Box box = new Box() {
            @Override public Size innerSize() { return new Size(200, 100); }
        };
        box.applyPaddingAll("16px");
        element.getRenderer().box.set(box);
        element.scrollHeight = height;
        return element;
    }

    @Test void consoleScrollUsesNativeLimitWithPaddingWithoutOverscroll() {
        Element element = output(500);
        DlcUiScroll.scrollToEnd(element);
        assertEquals(400, element.getTargetScrollTop());
        assertTrue(Double.isFinite(element.getTargetScrollTop()));
        assertTrue(DlcUiScroll.isAtEnd(element, 24));
        element.scrollHeight = 540;
        assertFalse(DlcUiScroll.isAtEnd(element, 24));
        DlcUiScroll.scrollToEnd(element);
        assertEquals(440, element.getTargetScrollTop());
    }

    @Test void shortOutputRemainsVisibleAndUserScrollingDisablesFollowing() {
        Element element = output(40);
        DlcUiScroll.scrollToEnd(element);
        assertEquals(0, element.getTargetScrollTop());
        element.scrollHeight = 500;
        element.setScrollTop(100);
        assertFalse(DlcUiScroll.isAtEnd(element, 24));
    }
}
