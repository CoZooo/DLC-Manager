package com.cozooo.dlc_manager.client.screen

import fi.dy.masa.malilib.gui.GuiListBase
import fi.dy.masa.malilib.gui.widgets.WidgetListBase
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase

abstract class DlcMaLiLibCompat<TYPE, WIDGET : WidgetListEntryBase<TYPE>, WIDGETLIST : WidgetListBase<TYPE, WIDGET>>(
    listX: Int,
    listY: Int,
) : GuiListBase<TYPE, WIDGET, WIDGETLIST>(listX, listY) {
    //? if >=1.21.1 {
    override fun mouseScrolled(mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double): Boolean {
        return handleCompatibleMouseScrolled(mouseX, mouseY, deltaX, deltaY)
    }
    //?} else {
    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        return handleCompatibleMouseScrolled(mouseX, mouseY, 0.0, delta)
    }
    //?}

    protected abstract fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double): Boolean
}
