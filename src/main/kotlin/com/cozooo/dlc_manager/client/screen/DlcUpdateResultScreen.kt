package com.cozooo.dlc_manager.client.screen

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class DlcUpdateResultScreen(
    private val parent: Screen?,
    private val success: Boolean,
    private val message: Component,
) : Screen(Component.translatable("dlc_manager.update.title")) {
    override fun init() {
        val buttonWidth = 120
        val buttonHeight = 20
        val y = height - 40
        if (success) {
            val restartNow = Button.builder(Component.translatable("dlc_manager.restart_now")) { Minecraft.getInstance().stop() }
                .bounds(width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
                .build()
            val restartLater = Button.builder(Component.translatable("dlc_manager.restart_later")) {
                if (parent is DlcUpdateManagerScreen) {
                    parent.refreshData()
                }
                Minecraft.getInstance().setScreen(parent)
            }.bounds(width / 2 + 6, y, buttonWidth, buttonHeight)
                .build()
            addRenderableWidget(restartNow)
            addRenderableWidget(restartLater)
        } else {
            val back = Button.builder(Component.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
                .bounds(width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight)
                .build()
            addRenderableWidget(back)
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        var y = 40
        val titleX = (width - font.width(title)) / 2
        guiGraphics.drawString(font, title, titleX, y, 0xFFFFFF, false)
        y += 20
        val color = if (success) 0x9BE17A else 0xFF6B6B
        for (line in font.split(message, width - 40)) {
            guiGraphics.drawCenteredString(font, line, centerX, y, color)
            y += 10
        }
        if (success) {
            y += 14
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.update.restart"), centerX, y, 0xC8C8C8)
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }
}
