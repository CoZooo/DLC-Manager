package com.cozooo.dlc_manager.client.screen

import com.sighs.apricityui.init.Element
import com.sighs.apricityui.layout.Box

internal object DlcUiScroll {
    data class State(val top: Double, val target: Double, val height: Double)
    @JvmStatic fun capture(element: Element) = State(element.scrollTop, element.targetScrollTop, element.scrollHeight)
    @JvmStatic fun restore(element: Element, state: State) {
        element.scrollHeight = state.height
        element.scrollTop = state.top
        element.setScrollTop(state.target)
    }

    @JvmStatic fun scrollToEnd(element: Element) {
        element.setScrollTop(limit(element))
    }
    @JvmStatic fun isAtEnd(element: Element, tolerance: Double): Boolean = element.targetScrollTop >= limit(element) - tolerance

    private fun limit(element: Element): Double = (element.scrollHeight - Box.of(element).innerSize().height()).coerceAtLeast(0.0)
}
