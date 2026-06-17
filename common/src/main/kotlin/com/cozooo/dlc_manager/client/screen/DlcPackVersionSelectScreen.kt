package com.cozooo.dlc_manager.client.screen

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.max

class DlcPackVersionSelectScreen(
    private val parent: DlcPackManagerScreen,
    private val identifier: String,
    private val displayName: Component,
    private val versions: List<String>,
    private val selectedKey: String,
    private val installedVersion: String?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.pack.version_select_title")) {
    override fun init() {
        clearWidgets()
        val buttonWidth = 180
        val buttonHeight = 20
        var y = 48
        for (versionKey in versions) {
            val version = versionKey.substringAfterLast('@', versionKey)
            val label = if (version == installedVersion) {
                Component.translatable("dlc_manager.pack.version_installed", version)
            } else {
                Component.literal(version)
            }
            val button = Button.builder(label) {
                parent.selectPackVersion(identifier, versionKey)
                Minecraft.getInstance().setScreen(parent)
            }.bounds(width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight).build()
            button.active = versionKey != selectedKey
            addRenderableWidget(button)
            y += buttonHeight + 6
        }
        addRenderableWidget(
            Button.builder(Component.translatable("dlc_manager.pack.back")) {
                Minecraft.getInstance().setScreen(parent)
            }.bounds(width / 2 - buttonWidth / 2, max(y + 8, height - 32), buttonWidth, buttonHeight).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF)
        guiGraphics.drawCenteredString(font, displayName, width / 2, 32, 0xC8C8C8)
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return false
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }
}
