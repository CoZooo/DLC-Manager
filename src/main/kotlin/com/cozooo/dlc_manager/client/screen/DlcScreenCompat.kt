package com.cozooo.dlc_manager.client.screen

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.TextureManager
import com.mojang.blaze3d.platform.NativeImage
//? if >=1.20.1 <26.1 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <26.1 {
import net.minecraft.client.gui.components.toasts.ToastComponent
//?}
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.toasts.ToastManager
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.resources.Identifier
*///?}
//? if <1.20.1 {
/*import net.minecraft.client.gui.GuiComponent
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.systems.RenderSystem
*///?}
//? if <26.1 {
import net.minecraft.resources.ResourceLocation
//?}
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

object DlcScreenCompat {
    @JvmStatic
    fun toastManager(minecraft: Minecraft): Any {
        //? if >=26.1 {
        /*return minecraft.getToastManager()
        *///?}
        //? if <26.1 {
        return minecraft.getToasts()
        //?}
    }

    @JvmStatic
    fun openUri(uri: String) {
        //? if >=26.1 {
        /*net.minecraft.util.Util.getPlatform().openUri(uri)
        *///?}
        //? if <26.1 {
        net.minecraft.Util.getPlatform().openUri(uri)
        //?}
    }

    @JvmStatic
    fun openClickEvent(click: ClickEvent?) {
        if (click == null) return
        //? if >=26.1 {
        /*if (click is ClickEvent.OpenUrl) net.minecraft.util.Util.getPlatform().openUri(click.uri())
        *///?}
        //? if <26.1 {
        if (click.action == ClickEvent.Action.OPEN_URL && !click.value.isNullOrBlank()) {
            net.minecraft.Util.getPlatform().openUri(click.value)
        }
        //?}
    }

    @JvmStatic
    fun handleClick(screen: Screen, style: net.minecraft.network.chat.Style) {
        val click = style.clickEvent ?: return
        //? if >=26.1 {
        /*try {
            val modern = Screen::class.java.getDeclaredMethod(
                "defaultHandleClickEvent",
                ClickEvent::class.java,
                Minecraft::class.java,
                Screen::class.java,
            )
            modern.isAccessible = true
            modern.invoke(null, click, Minecraft.getInstance(), screen)
        } catch (_: ReflectiveOperationException) {
        }
        *///?}
        //? if <26.1 {
        screen.handleComponentClicked(style)
        //?}
    }

    @JvmStatic
    fun hasCommandPermission(player: Any?, level: Int): Boolean {
        if (player == null) return false
        //? if >=26.1 {
        /*val local = player as? net.minecraft.client.player.LocalPlayer ?: return false
        return local.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)
        *///?}
        //? if <26.1 {
        val local = player as? net.minecraft.client.player.LocalPlayer ?: return false
        return local.hasPermissions(level)
        //?}
    }

    @JvmStatic
    fun createDynamicTexture(name: String, image: NativeImage): DynamicTexture {
        //? if >=26.1 {
        /*return DynamicTexture({ name }, image)
        *///?}
        //? if <26.1 {
        return DynamicTexture(image)
        //?}
    }

    @JvmStatic
    fun registerTexture(manager: TextureManager, name: String, texture: DynamicTexture): Any {
        //? if >=26.1 {
        /*val identifier = Identifier.parse(name)
        manager.register(identifier, texture)
        return identifier
        *///?}
        //? if <26.1 {
        return manager.register(name, texture)
        //?}
    }

    @JvmStatic
    fun releaseTexture(manager: TextureManager, location: Any) {
        //? if >=26.1 {
        /*manager.release(location as Identifier)
        *///?}
        //? if <26.1 {
        manager.release(location as ResourceLocation)
        //?}
    }

    @JvmStatic
    fun addOrUpdatePeriodicNotification(toasts: Any, title: Component, description: Component) {
        //? if >=26.1 {
        /*if (toasts !is ToastManager) throw IllegalStateException("Failed to resolve ToastManager for periodic notification")
        SystemToast.addOrUpdate(toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description)
        *///?}
        //? if <1.21.1 {
        if (toasts !is ToastComponent) throw IllegalStateException("Failed to resolve ToastComponent for periodic notification")
        SystemToast.addOrUpdate(toasts, SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, description)
        //?}
        //? if >=1.21.1 <26.1 {
        /*if (toasts !is ToastComponent) throw IllegalStateException("Failed to resolve ToastComponent for periodic notification")
        SystemToast.addOrUpdate(toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description)
        *///?}
    }

    fun interface DlcButtonPress { fun press(button: Button) }

    @JvmStatic
    fun buttonBuilder(message: Component, onPress: DlcButtonPress): ButtonBuilder = ButtonBuilder(message, onPress)

    @JvmStatic
    fun press(button: Button) {
        //? if >=26.1 {
        /*button.onPress(object : InputWithModifiers {
            override fun input(): Int = 257
            override fun modifiers(): Int = 0
        })
        *///?}
        //? if <26.1 {
        button.onPress()
        //?}
    }

    class ButtonBuilder internal constructor(
        private val message: Component,
        private val onPress: DlcButtonPress
    ) {
        private var x = 0
        private var y = 0
        private var width = 0
        private var height = 0

        fun bounds(x: Int, y: Int, width: Int, height: Int): ButtonBuilder {
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            return this
        }

        fun build(): Button {
            //? if >=1.19.3 {
            return Button.builder(message, Button.OnPress { onPress.press(it) }).bounds(x, y, width, height).build()
            //?}
            //? if <1.19.3 {
            /*return Button(x, y, width, height, message) { onPress.press(it) }
            *///?}
        }
    }

    @JvmStatic
    fun translationKey(component: Component?): String? {
        if (component == null) return null
        //? if >=1.19.2 {
        val contents = component.contents
        if (contents is net.minecraft.network.chat.contents.TranslatableContents) return contents.key
        //?}
        //? if <1.19.2 {
        /*if (component is net.minecraft.network.chat.TranslatableComponent) {
            return component.key
        }
        *///?}
        return null
    }

    @JvmStatic
    fun isFocused(widget: AbstractWidget): Boolean {
        return widget.isFocused
    }

    @JvmStatic
    fun getX(widget: AbstractWidget): Int {
        //? if >=1.19.3 {
        return widget.x
        //?} else {
        /*return widget.x
        *///?}
    }

    @JvmStatic
    fun getY(widget: AbstractWidget): Int {
        //? if >=1.19.3 {
        return widget.y
        //?} else {
        /*return widget.y
        *///?}
    }

    @JvmStatic
    fun setX(widget: AbstractWidget, x: Int) {
        //? if >=1.19.3 {
        widget.x = x
        //?} else {
        /*widget.x = x
        *///?}
    }

    @JvmStatic
    fun setY(widget: AbstractWidget, y: Int) {
        //? if >=1.19.3 {
        widget.y = y
        //?} else {
        /*widget.y = y
        *///?}
    }

    @JvmStatic
    fun setNumericFilter(field: net.minecraft.client.gui.components.EditBox) {
        //? if >=26.1 {
        field.setResponder { value ->
            val filtered = value.filter { it.isDigit() }
            if (filtered != value) field.value = filtered
        }
        //?}
        //? if <26.1 {
        /*field.setFilter { value -> value.chars().allMatch { codePoint -> Character.isDigit(codePoint) } }
        *///?}
    }

    @JvmStatic
    fun setFocused(widget: AbstractWidget, focused: Boolean) {
        //? if >=1.19.3 {
        widget.setFocused(focused)
        //?} else {
        /*try {
            val method = AbstractWidget::class.java.getDeclaredMethod("setFocused", Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(widget, focused)
        } catch (_: ReflectiveOperationException) {
        }
        *///?}
    }

    @JvmStatic
    fun fill(graphics: Any, left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        //? if >=26.1 {
        /*(graphics as GuiGraphicsExtractor).fill(left, top, right, bottom, color)
        *///?}
        //? if >=1.20.1 <26.1 {
        (graphics as GuiGraphics).fill(left, top, right, bottom, color)
        //?}
        //? if <1.20.1 {
        /*GuiComponent.fill(graphics as PoseStack, left, top, right, bottom, color)
        *///?}
    }

    @JvmStatic
    fun drawCenteredString(graphics: Any, font: Font, text: Component, x: Int, y: Int, color: Int) {
        //? if >=26.1 {
        /*(graphics as GuiGraphicsExtractor).centeredText(font, text, x, y, color)
        *///?}
        //? if >=1.20.1 <26.1 {
        (graphics as GuiGraphics).drawCenteredString(font, text, x, y, color)
        //?}
        //? if <1.20.1 {
        /*GuiComponent.drawCenteredString(graphics as PoseStack, font, text, x, y, color)
        *///?}
    }

    @JvmStatic
    fun drawString(graphics: Any, font: Font, text: Component, x: Int, y: Int, color: Int, shadow: Boolean) {
        //? if >=26.1 {
        /*(graphics as GuiGraphicsExtractor).text(font, text, x, y, color, shadow)
        *///?}
        //? if >=1.20.1 <26.1 {
        (graphics as GuiGraphics).drawString(font, text, x, y, color, shadow)
        //?}
        //? if <1.20.1 {
        /*GuiComponent.drawString(graphics as PoseStack, font, text, x, y, color)
        *///?}
    }

    @JvmStatic
    fun drawString(graphics: Any, font: Font, text: FormattedCharSequence, x: Int, y: Int, color: Int, shadow: Boolean) {
        //? if >=26.1 {
        /*(graphics as GuiGraphicsExtractor).text(font, text, x, y, color, shadow)
        *///?}
        //? if >=1.20.1 <26.1 {
        (graphics as GuiGraphics).drawString(font, text, x, y, color, shadow)
        //?}
        //? if <1.20.1 {
        /*GuiComponent.drawString(graphics as PoseStack, font, text, x, y, color)
        *///?}
    }

    @JvmStatic
    fun enableScissor(graphics: Any, left: Int, top: Int, right: Int, bottom: Int) {
        //? if >=26.1 {
        /*(graphics as GuiGraphicsExtractor).enableScissor(left, top, right, bottom)
        *///?}
        //? if >=1.20.1 <26.1 {
        (graphics as GuiGraphics).enableScissor(left, top, right, bottom)
        //?}
        //? if >=1.19.2 <1.20.1 {
        /*GuiComponent.enableScissor(left, top, right, bottom)
        *///?}
        //? if <1.19.2 {
        /*RenderSystem.enableScissor(left, top, right, bottom)
        *///?}
    }

    @JvmStatic
    fun disableScissor(graphics: Any) {
        //? if >=26.1 {
        /*(graphics as GuiGraphicsExtractor).disableScissor()
        *///?}
        //? if >=1.20.1 <26.1 {
        (graphics as GuiGraphics).disableScissor()
        //?}
        //? if >=1.19.2 <1.20.1 {
        /*GuiComponent.disableScissor()
        *///?}
        //? if <1.19.2 {
        /*RenderSystem.disableScissor()
        *///?}
    }

    @JvmStatic
    fun pushPose(graphics: Any) {
        //? if >=26.1 {
        /*(graphics as GuiGraphicsExtractor).pose().pushMatrix()
        *///?}
        //? if >=1.20.1 <26.1 {
        (graphics as GuiGraphics).pose().pushPose()
        //?}
        //? if <1.20.1 {
        /*(graphics as PoseStack).pushPose()
        *///?}
    }

    @JvmStatic
    fun scale(graphics: Any, x: Float, y: Float, z: Float) {
        //? if >=26.1 {
        /*(graphics as GuiGraphicsExtractor).pose().scale(x, y)
        *///?}
        //? if >=1.20.1 <26.1 {
        (graphics as GuiGraphics).pose().scale(x, y, z)
        //?}
        //? if <1.20.1 {
        /*(graphics as PoseStack).scale(x, y, z)
        *///?}
    }

    @JvmStatic
    fun popPose(graphics: Any) {
        //? if >=26.1 {
        /*(graphics as GuiGraphicsExtractor).pose().popMatrix()
        *///?}
        //? if >=1.20.1 <26.1 {
        (graphics as GuiGraphics).pose().popPose()
        //?}
        //? if <1.20.1 {
        /*(graphics as PoseStack).popPose()
        *///?}
    }
}
