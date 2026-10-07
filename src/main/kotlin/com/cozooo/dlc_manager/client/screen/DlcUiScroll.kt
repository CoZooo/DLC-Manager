package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Element
import com.sighs.apricityui.layout.Box

internal object DlcUiScroll {
    @JvmStatic fun scrollToEnd(element: Element) {
        element.setScrollTop(limit(element))
    }
    @JvmStatic fun isAtEnd(element: Element, tolerance: Double): Boolean = element.targetScrollTop >= limit(element) - tolerance

    private fun limit(element: Element): Double = (element.scrollHeight - Box.of(element).innerSize().height()).coerceAtLeast(0.0)
}
