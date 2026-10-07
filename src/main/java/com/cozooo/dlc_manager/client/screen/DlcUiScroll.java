package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;

final class DlcUiScroll {
    private DlcUiScroll() {}

    static void scrollToEnd(Element element) {
        element.setScrollTop(limit(element));
    }
    static boolean isAtEnd(Element element, double tolerance) {
        return element.getTargetScrollTop() >= limit(element) - tolerance;
    }

    private static double limit(Element element) {
        return Math.max(0, element.scrollHeight - Box.of(element).innerSize().height());
    }
}
