package com.cozooo.dlc_manager.client.screen

import fi.dy.masa.malilib.gui.GuiListBase
import fi.dy.masa.malilib.gui.widgets.WidgetListBase
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase

abstract class DlcMaLiLibCompat<TYPE, WIDGET : WidgetListEntryBase<TYPE>, WIDGETLIST : WidgetListBase<TYPE, WIDGET>>(
    listX: Int,
    listY: Int,
) : GuiListBase<TYPE, WIDGET, WIDGETLIST>(listX, listY) {
    override fun mouseScrolled(mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double): Boolean {
        return handleCompatibleMouseScrolled(mouseX, mouseY, deltaX, deltaY)
    }

    protected abstract fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double): Boolean
}
