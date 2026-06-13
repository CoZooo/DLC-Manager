package com.cozooo.dlc_manager.client.screen

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
import java.lang.reflect.Method

object DlcScreenCompat {
    private val RENDERABLES_FIELD: Field? = findField(Screen::class.java, "renderables")

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
    fun invokeCompatibleMouseScroll(target: Any, methodName: String, mouseX: Int, mouseY: Int, deltaX: Double, deltaY: Double): Boolean {
        val method = findDeclaredMethod(
            target.javaClass,
            methodName,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Double::class.javaPrimitiveType!!,
            Double::class.javaPrimitiveType!!,
        ) ?: throw IllegalStateException("Failed to resolve compatible mouse scroll handler: $methodName")
        return try {
            val value = method.invoke(target, mouseX, mouseY, deltaX, deltaY)
            value is Boolean && value
        } catch (ex: ReflectiveOperationException) {
            throw IllegalStateException("Failed to invoke compatible mouse scroll handler", ex)
        }
    }

    private fun findDeclaredMethod(owner: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? {
        var current: Class<*>? = owner
        while (current != null) {
            try {
                val method = current.getDeclaredMethod(name, *parameterTypes)
                method.isAccessible = true
                return method
            } catch (_: ReflectiveOperationException) {
                current = current.superclass
            }
        }
        return null
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
