package com.cozooo.dlc_manager.client.screen

import fi.dy.masa.malilib.gui.GuiListBase
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.components.toasts.ToastComponent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.lang.reflect.Field

object DlcScreenCompat {
    private val RENDERABLES_FIELD: Field? = findField(Screen::class.java, "renderables")

    abstract class MouseScroll(title: Component) : Screen(title) {
        override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
            return handleCompatibleMouseScrolled(mouseX, mouseY, delta)
        }

        protected abstract fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean
    }

    @JvmStatic
    fun renderBackground(screen: Screen, guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        screen.renderBackground(guiGraphics)
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
        SystemToast.addOrUpdate(toasts, SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, description)
    }

    @JvmStatic
    fun createCheckbox(font: Font, x: Int, y: Int, width: Int, height: Int, label: Component, selected: Boolean): Checkbox {
        return Checkbox(x, y, width, height, label, selected)
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
        if (renderedAny || RENDERABLES_FIELD == null) {
            return
        }
        try {
            val value = RENDERABLES_FIELD.get(screen)
            if (value !is Iterable<*>) {
                return
            }
            for (candidate in value) {
                if (candidate is Renderable) {
                    candidate.render(guiGraphics, mouseX, mouseY, partialTick)
                }
            }
        } catch (ex: ReflectiveOperationException) {
            throw IllegalStateException("Failed to render compatible widgets", ex)
        }
    }

    @JvmStatic
    fun invokeCompatibleMouseScroll(target: GuiListBase<*, *, *>, mouseX: Int, mouseY: Int, deltaX: Double, deltaY: Double): Boolean {
        return target.onMouseScrolled(mouseX, mouseY, deltaY)
    }

    private fun findField(owner: Class<*>, name: String): Field? {
        return try {
            val field = owner.getDeclaredField(name)
            field.isAccessible = true
            field
        } catch (_: ReflectiveOperationException) {
            null
        }
    }
}
