package com.cozooo.dlc_manager.client.screen;

import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;

public abstract class DlcMaLiLibCompat<TYPE, WIDGET extends WidgetListEntryBase<TYPE>, WIDGETLIST extends WidgetListBase<TYPE, WIDGET>>
    extends GuiListBase<TYPE, WIDGET, WIDGETLIST> {
    protected DlcMaLiLibCompat(int listX, int listY) {
        super(listX, listY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return handleCompatibleMouseScrolled(mouseX, mouseY, 0.0, delta);
    }

    protected abstract boolean handleCompatibleMouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY);
}
