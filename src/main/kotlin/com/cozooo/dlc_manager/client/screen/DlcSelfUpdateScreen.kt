package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat

import com.cozooo.dlc_manager.platform.DlcTextCompat

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService.SelfUpdateResult
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class DlcSelfUpdateScreen @JvmOverloads constructor(
    private val parent: Screen?,
    preset: SelfUpdateResult?,
    private val showSkipThisUpdateButton: Boolean = false,
) : DlcPageScreen(DlcUiPage.SELF_UPDATE, parent, DlcTextCompat.translatable("dlc_manager.self_update.title")) {
    private var result: SelfUpdateResult? = preset
    private var downloadButton: Button? = null
    private var skipThisUpdateButton: Button? = null
    private var backButton: Button? = null
    private var checking = false
    private var downloading = false
    private var statusText: String? = null
    private var errorText: String? = null

    private fun initControls() {
        val buttonWidth = if (showSkipThisUpdateButton) 110 else 140
        val buttonHeight = 20
        val gap = 6
        val y = height - 40
        val buttonCount = if (showSkipThisUpdateButton) 3 else 2
        val totalWidth = buttonWidth * buttonCount + gap * (buttonCount - 1)
        val startX = width / 2 - totalWidth / 2

        downloadButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.self_update.download")) {
            startDownload()
        }
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build()

        skipThisUpdateButton = if (showSkipThisUpdateButton) {
            DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.self_update.skip_this_update")) {
                skipThisUpdate()
            }
                .bounds(startX + buttonWidth + gap, y, buttonWidth, buttonHeight)
                .build()
        } else {
            null
        }

        backButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.self_update.skip")) {
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
        statusText = DlcTextCompat.translatable("dlc_manager.self_update.downloading").string
        updateButtons()
        Thread({
            val download = DlcSelfUpdateService.downloadUpdate(current)
            Minecraft.getInstance().execute {
                downloading = false
                if (download.success) {
                    showRestartPrompt()
                } else {
                    errorText = DlcTextCompat.translatable("dlc_manager.self_update.failed").string
                }
                updateButtons()
            }
        }, "dlc-self-update-download").start()
    }

    private fun showRestartPrompt() {
        Minecraft.getInstance().setScreen(
            DlcConfirmScreen(
                { choice ->
                    if (choice) {
                        Minecraft.getInstance().stop()
                    } else {
                        Minecraft.getInstance().setScreen(parent)
                    }
                },
                DlcTextCompat.translatable("dlc_manager.self_update.title"),
                DlcTextCompat.translatable("dlc_manager.self_update.restart"),
                DlcTextCompat.translatable("dlc_manager.restart_now"),
                DlcTextCompat.translatable("dlc_manager.restart_later"),
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

    override fun buildPage() {
        initControls()
        val body = node(content, "div", "page-dialog")
        live(body, { result?.currentVersion?.let { tr("dlc_manager.self_update.current", it) }.orEmpty() }, "page-description")
        live(body, { result?.takeIf { it.hasUpdate }?.latestVersion?.let { tr("dlc_manager.self_update.latest", it) }.orEmpty() }, "section-title")
        live(body, { when { checking -> tr("dlc_manager.self_update.checking"); downloading -> statusText; errorText != null -> errorText; result?.hasUpdate == false -> tr("dlc_manager.self_update.none"); else -> "" } }, "page-message")
        backButton?.let { mount(footer, it) }
        skipThisUpdateButton?.let { mount(footer, it) }
        downloadButton?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }
    }
    override fun updatePage() { updateButtons() }

}
