package com.cozooo.dlc_manager.client.screen

import fi.dy.masa.malilib.gui.GuiListBase
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.components.toasts.ToastComponent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

object DlcScreenCompat {
    abstract class MouseScroll(title: Component) : Screen(title) {
        override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
            return handleCompatibleMouseScrolled(mouseX, mouseY, vertical)
        }

        protected abstract fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean
    }

    @JvmStatic
    fun renderBackground(screen: Screen, guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        screen.renderBackground(guiGraphics, mouseX, mouseY, partialTick)
    }

    @JvmStatic
    fun renderModalBackground(screen: Screen, guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(screen, guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.fill(0, 0, screen.width, screen.height, 0xC0101010.toInt())
    }

    @JvmStatic
    fun addOrUpdatePeriodicNotification(toasts: Any?, title: Component, description: Component) {
        if (toasts !is ToastComponent) {
            throw IllegalStateException("Failed to resolve ToastComponent for periodic notification")
        }
        SystemToast.addOrUpdate(toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description)
    }

    @JvmStatic
    fun createCheckbox(font: Font, x: Int, y: Int, width: Int, height: Int, label: Component, selected: Boolean): Checkbox {
        return Checkbox.builder(label, font)
            .pos(x, y)
            .maxWidth(width)
            .selected(selected)
            .build()
    }

    @JvmStatic
    fun renderWidgets(screen: Screen, guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        var renderedAny = false
        for (child in screen.children()) {
            if (child is Renderable) {
                child.render(guiGraphics, mouseX, mouseY, partialTick)
                renderedAny = true
            }
        }
    }

    @JvmStatic
    fun invokeCompatibleMouseScroll(target: GuiListBase<*, *, *>, mouseX: Int, mouseY: Int, deltaX: Double, deltaY: Double): Boolean {
        return target.onMouseScrolled(mouseX, mouseY, deltaX, deltaY)
    }

}
