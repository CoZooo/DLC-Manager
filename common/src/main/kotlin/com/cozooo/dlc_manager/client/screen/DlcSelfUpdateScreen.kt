package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService.SelfUpdateResult
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class DlcSelfUpdateScreen @JvmOverloads constructor(
    private val parent: Screen?,
    preset: SelfUpdateResult?,
    private val showSkipThisUpdateButton: Boolean = false,
) : Screen(Component.translatable("dlc_manager.self_update.title")) {
    private var result: SelfUpdateResult? = preset
    private var downloadButton: Button? = null
    private var skipThisUpdateButton: Button? = null
    private var backButton: Button? = null
    private var checking = false
    private var downloading = false
    private var statusText: String? = null
    private var errorText: String? = null

    override fun init() {
        val buttonWidth = if (showSkipThisUpdateButton) 110 else 140
        val buttonHeight = 20
        val gap = 6
        val y = height - 40
        val buttonCount = if (showSkipThisUpdateButton) 3 else 2
        val totalWidth = buttonWidth * buttonCount + gap * (buttonCount - 1)
        val startX = width / 2 - totalWidth / 2

        downloadButton = Button.builder(Component.translatable("dlc_manager.self_update.download")) {
            startDownload()
        }
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build()

        skipThisUpdateButton = if (showSkipThisUpdateButton) {
            Button.builder(Component.translatable("dlc_manager.self_update.skip_this_update")) {
                skipThisUpdate()
            }
                .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
                .build()
        } else {
            null
        }

        backButton = Button.builder(Component.translatable("dlc_manager.self_update.skip")) {
            Minecraft.getInstance().setScreen(parent)
        }
            .bounds(startX + (buttonWidth + gap) * (buttonCount - 1), y, buttonWidth, buttonHeight)
            .build()

        downloadButton?.let { addRenderableWidget(it) }
        skipThisUpdateButton?.let { addRenderableWidget(it) }
        backButton?.let { addRenderableWidget(it) }
        updateButtons()
        if (result == null) {
            startCheck()
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        var y = 30
        guiGraphics.drawCenteredString(font, title, centerX, y, 0xFFFFFF)
        y += 18

        val current = result
        if (current != null && !current.currentVersion.isNullOrBlank()) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("dlc_manager.self_update.current", current.currentVersion),
                centerX,
                y,
                0xC8C8C8,
            )
            y += 14
        }
        if (current != null && !current.latestVersion.isNullOrBlank() && current.hasUpdate) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("dlc_manager.self_update.latest", current.latestVersion),
                centerX,
                y,
                0xC8C8C8,
            )
            y += 14
        }
        when {
            checking -> guiGraphics.drawCenteredString(
                font,
                Component.translatable("dlc_manager.self_update.checking"),
                centerX,
                y,
                0xE6E6E6,
            )
            downloading -> guiGraphics.drawCenteredString(font, statusText ?: "", centerX, y, 0xE6E6E6)
            !errorText.isNullOrBlank() -> guiGraphics.drawCenteredString(font, errorText ?: "", centerX, y, 0xFF6B6B)
            current != null && !current.hasUpdate -> guiGraphics.drawCenteredString(
                font,
                Component.translatable("dlc_manager.self_update.none"),
                centerX,
                y,
                0xC8C8C8,
            )
        }
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun startCheck() {
        if (checking) {
            return
        }
        checking = true
        errorText = null
        statusText = null
        updateButtons()
        Thread({
            val check = DlcSelfUpdateService.checkForUpdate()
            Minecraft.getInstance().execute {
                checking = false
                result = check
                updateButtons()
            }
        }, "dlc-self-update-check").start()
    }

    private fun startDownload() {
        val current = result
        if (downloading || checking || current == null || !current.hasUpdate) {
            return
        }
        downloading = true
        errorText = null
        statusText = Component.translatable("dlc_manager.self_update.downloading").string
        updateButtons()
        Thread({
            val download = DlcSelfUpdateService.downloadUpdate(current)
            Minecraft.getInstance().execute {
                downloading = false
                if (download.success) {
                    showRestartPrompt()
                } else {
                    errorText = Component.translatable("dlc_manager.self_update.failed").string
                }
                updateButtons()
            }
        }, "dlc-self-update-download").start()
    }

    private fun showRestartPrompt() {
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { choice ->
                    if (choice) {
                        Minecraft.getInstance().stop()
                    } else {
                        Minecraft.getInstance().setScreen(parent)
                    }
                },
                Component.translatable("dlc_manager.self_update.title"),
                Component.translatable("dlc_manager.self_update.restart"),
                Component.translatable("dlc_manager.restart_now"),
                Component.translatable("dlc_manager.restart_later"),
            )
        )
    }

    private fun skipThisUpdate() {
        val current = result
        if (current != null && current.hasUpdate) {
            DlcSelfUpdateService.skipPromptForUpdate(current)
        }
        Minecraft.getInstance().setScreen(parent)
    }

    private fun updateButtons() {
        downloadButton?.let {
            it.active = !checking && !downloading && result != null && result!!.hasUpdate
            it.visible = true
        }
        skipThisUpdateButton?.let {
            it.active = !downloading && !checking && result != null && result!!.hasUpdate
            it.visible = true
        }
        backButton?.active = !downloading && !checking
    }
}
