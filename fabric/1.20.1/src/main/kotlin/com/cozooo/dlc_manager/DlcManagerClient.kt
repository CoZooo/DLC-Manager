package com.cozooo.dlc_manager

import com.cozooo.dlc_manager.client.DlcClientRuntime
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen
import com.cozooo.dlc_manager.client.update.DlcUpdateClient
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.core.DlcPathConfig
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.max

class DlcManagerClient : ClientModInitializer {
    override fun onInitializeClient() {
        DlcUpdateService.ensureUpdateDir()
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("dlcmanager")
                    .executes {
                        openManagerFromCommand()
                        1
                    }
                    .then(
                        ClientCommandManager.literal("selfupdate").executes {
                            openSelfUpdateFromCommand()
                            1
                        },
                    )
                    .then(
                        ClientCommandManager.literal("updatemanager").executes {
                            openUpdateManagerFromCommand()
                            1
                        },
                    )
                    .then(
                        ClientCommandManager.literal("update").executes {
                            openUpdatePromptFromCommand()
                            1
                        },
                    )
                    .then(
                        ClientCommandManager.literal("server").executes {
                            openServerManagerFromCommand()
                            1
                        },
                    ),
            )
        }
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is PauseScreen) {
                addIngameServerButton(screen)
            }
            if (screen !is net.minecraft.client.gui.screens.TitleScreen) {
                return@register
            }

            val x = screen.width / 2 - 100
            val y = screen.height / 4 + 48
            val w = 200
            val h = 20

            val button = Button.builder(Component.translatable("dlc_manager.title")) {
                val managerScreen: Screen = DlcMaLiLibScreen(screen)
                if (DlcRemoteManager.tryShowFromManager(managerScreen)) {
                    return@builder
                }
                Minecraft.getInstance().setScreen(managerScreen)
            }.bounds(x, y, w, h).build()
            val updateButton = Button.builder(Component.translatable("dlc_manager.update.import_icon")) {
                DlcUpdateClient.openUpdateManager(screen)
            }.bounds(x + w + 4, y, 20, h).build()
            val noticeWidth = max(48, Minecraft.getInstance().font.width(Component.translatable("dlc_manager.notice")) + 8)
            val noticeHeight = 20
            val noticeDefaultX = screen.width - noticeWidth - 8
            val noticeDefaultY = screen.height - noticeHeight - 8
            val noticePos = DlcPathConfig.computeNoticeButtonPosition(
                screen.width,
                screen.height,
                noticeWidth,
                noticeHeight,
                noticeDefaultX,
                noticeDefaultY,
            )
            val noticeButton = Button.builder(Component.translatable("dlc_manager.notice")) {
                DlcClientRuntime.openNoticeFromTitle(screen)
            }.bounds(noticePos[0], noticePos[1], noticeWidth, noticeHeight).build()

            if (!hasDlcButton(screen)) {
                Screens.getButtons(screen).add(button)
            }
            if (DlcPathConfig.isUButtomShown() &&
                DlcPathConfig.isUpdateManagerOn() &&
                findButtonByKey(screen, "dlc_manager.update.import_icon") == null
            ) {
                Screens.getButtons(screen).add(updateButton)
            }
            if (DlcClientRuntime.shouldShowMainMenuNoticeButton() &&
                findButtonByKey(screen, "dlc_manager.notice") == null
            ) {
                Screens.getButtons(screen).add(noticeButton)
            }
            if (DlcClientRuntime.shouldShowMainMenuNoticePanel()) {
                ScreenEvents.afterRender(screen).register { currentScreen, guiGraphics, mouseX, mouseY, _ ->
                    DlcClientRuntime.renderTitleNoticePanel(currentScreen, guiGraphics, mouseX, mouseY)
                }
                ScreenMouseEvents.allowMouseClick(screen).register { currentScreen, mouseX, mouseY, mouseButton ->
                    !DlcClientRuntime.handleTitleNoticePanelClick(currentScreen, mouseX, mouseY, mouseButton)
                }
                ScreenMouseEvents.allowMouseScroll(screen).register { currentScreen, mouseX, mouseY, horizontalAmount, verticalAmount ->
                    !DlcClientRuntime.handleTitleNoticePanelScroll(currentScreen, mouseX, mouseY, horizontalAmount, verticalAmount)
                }
                ScreenMouseEvents.allowMouseRelease(screen).register { currentScreen, mouseX, mouseY, mouseButton ->
                    !DlcClientRuntime.handleTitleNoticePanelRelease(currentScreen, mouseX, mouseY, mouseButton)
                }
            }
            alignTitleButtons(screen)
            pendingAlignScreen = screen
            DlcClientRuntime.maybeOpenManagerOnFirstRun(screen)
            runStartupChecksAsync(screen)
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            pendingAlignScreen?.let { screen ->
                if (client.screen === screen) {
                    alignTitleButtons(screen)
                }
                pendingAlignScreen = null
            }
            if (resourcePackSynced) {
                return@register
            }
            if (client.player == null) {
                return@register
            }
            resourcePackSynced = true
            client.execute { syncEnabledResourcePacks() }
        }
    }

    companion object {
        private var resourcePackSynced = false
        private var pendingAlignScreen: Screen? = null

        private fun addIngameServerButton(screen: Screen) {
            if (!canOpenServerManager()) {
                return
            }
            if (findButtonByKey(screen, "dlc_manager.server.manage") != null) {
                return
            }
            var anchor = findButtonByKey(screen, "menu.sendFeedback")
            if (anchor == null) {
                anchor = findButtonByKey(screen, "menu.feedback")
            }
            if (anchor != null) {
                anchor.visible = false
                anchor.active = false
            }
            if (anchor == null) {
                anchor = findButtonByKey(screen, "menu.mods")
            }
            if (anchor == null) {
                anchor = findButtonByKey(screen, "menu.options")
            }
            var x = screen.width / 2 - 100
            var y = screen.height / 4 + 120
            var w = 200
            var h = 20
            if (anchor != null) {
                if (findButtonByKey(screen, "menu.sendFeedback") != null || findButtonByKey(screen, "menu.feedback") != null) {
                    x = anchor.x
                    y = anchor.y
                    w = anchor.width
                    h = anchor.height
                } else if (anchor === findButtonByKey(screen, "menu.mods")) {
                    val gap = 4
                    val originalWidth = anchor.width
                    val halfWidth = max(20, (originalWidth - gap) / 2)
                    anchor.width = halfWidth
                    x = anchor.x + halfWidth + gap
                    y = anchor.y
                    w = max(20, originalWidth - halfWidth - gap)
                    h = anchor.height
                } else {
                    x = anchor.x
                    y = anchor.y + anchor.height + 4
                    w = anchor.width
                    h = anchor.height
                }
            }
            val button = Button.builder(Component.translatable("dlc_manager.server.manage")) {
                openServerManagerFromIngame(screen)
            }.bounds(x, y, w, h).build()
            Screens.getButtons(screen).add(button)
        }

        private fun runStartupChecksAsync(screen: Screen) {
            DlcClientRuntime.runStartupChecksAsync(screen)
        }

        private fun isRealmsButton(widget: AbstractWidget): Boolean {
            val contents = widget.message.contents
            return contents is TranslatableContents && contents.key == "menu.online"
        }

        private fun findButtonByKey(screen: Screen, key: String): AbstractWidget? {
            return DlcClientRuntime.findButtonByKey(screen, key)
        }

        private fun canOpenServerManager(): Boolean {
            return DlcClientRuntime.canOpenServerManager()
        }

        private fun openManagerFromCommand() {
            DlcClientRuntime.openManagerFromCommand()
        }

        private fun openSelfUpdateFromCommand() {
            DlcClientRuntime.openSelfUpdateFromCommand()
        }

        private fun openUpdateManagerFromCommand() {
            DlcClientRuntime.openUpdateManagerFromCommand()
        }

        private fun openUpdatePromptFromCommand() {
            DlcClientRuntime.openUpdatePromptFromCommand()
        }

        private fun openServerManagerFromCommand() {
            DlcClientRuntime.openServerManagerFromCommand()
        }

        private fun findModsButton(screen: Screen): AbstractWidget? {
            var widget = findButtonByKey(screen, "modmenu.title")
            if (widget != null) {
                return widget
            }
            widget = findButtonByKey(screen, "menu.mods")
            if (widget != null) {
                return widget
            }
            return findButtonByKey(screen, "fml.menu.mods")
        }

        private fun openServerManagerFromIngame(parent: Screen) {
            DlcClientRuntime.openServerManagerFromIngame(parent)
        }

        private fun moveBottomRow(screen: Screen, targetY: Int) {
            val optionsWidget = findButtonByKey(screen, "menu.options")
            if (optionsWidget != null) {
                val currentY = optionsWidget.y
                for (child in screen.children()) {
                    if (child is AbstractWidget && abs(child.y - currentY) <= 1) {
                        child.y = targetY
                    }
                }
                return
            }
            for (key in arrayOf("menu.options", "menu.quit", "options.language", "options.accessibility")) {
                findButtonByKey(screen, key)?.y = targetY
            }
        }

        private fun findRealmsButton(screen: Screen): AbstractWidget? {
            for (child in screen.children()) {
                if (child is AbstractWidget && isRealmsButton(child)) {
                    return child
                }
            }
            return null
        }

        private fun hasDlcButton(screen: Screen): Boolean {
            for (widget in Screens.getButtons(screen)) {
                val contents = widget.message.contents
                if (contents is TranslatableContents && contents.key == "dlc_manager.title") {
                    return true
                }
            }
            return false
        }

        private fun alignTitleButtons(screen: Screen) {
            val realmsWidget = findRealmsButton(screen)
            val multiplayerWidget = findButtonByKey(screen, "menu.multiplayer")
            val modsWidget = findModsButton(screen)
            val dlcWidget = findButtonByKey(screen, "dlc_manager.title") ?: return
            val updateWidget = findButtonByKey(screen, "dlc_manager.update.import_icon")
            if (realmsWidget != null) {
                realmsWidget.visible = false
                realmsWidget.active = false
            }
            var rowStep = 24
            var rowX = screen.width / 2 - 100
            var rowY = screen.height / 4 + 48
            var rowW = 200
            var rowH = 20
            val anchor = multiplayerWidget ?: realmsWidget
            if (anchor != null) {
                rowStep = anchor.height + 4
                rowX = anchor.x
                rowW = anchor.width
                rowH = anchor.height
                rowY = anchor.y + rowStep
                if (anchor === realmsWidget && multiplayerWidget == null) {
                    rowY = anchor.y
                }
            }
            val pos = DlcPathConfig.computeTitleButtonPosition(screen.width, screen.height, rowW, rowH, rowX, rowY)
            rowX = pos[0]
            rowY = pos[1]

            if (modsWidget != null) {
                val gap = 4
                val halfWidth = max(40, (rowW - gap) / 2)
                val iconGap = 4
                val iconWidth = 20
                modsWidget.x = rowX
                modsWidget.y = rowY
                modsWidget.width = halfWidth
                setWidgetHeight(modsWidget, rowH)
                val dlcX = rowX + halfWidth + gap
                val dlcWidth = halfWidth
                if (updateWidget != null) {
                    var defaultUpdateX = dlcX + dlcWidth + iconGap
                    if (DlcPathConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                        defaultUpdateX = dlcX - iconGap - iconWidth
                    }
                    val updatePos = DlcPathConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconWidth, rowH, defaultUpdateX, rowY)
                    updateWidget.width = iconWidth
                    updateWidget.x = updatePos[0]
                    updateWidget.y = updatePos[1]
                    setWidgetHeight(updateWidget, rowH)
                }
                dlcWidget.x = dlcX
                dlcWidget.y = rowY
                dlcWidget.width = dlcWidth
                setWidgetHeight(dlcWidget, rowH)

                val optionsRowY = rowY + rowStep
                moveBottomRow(screen, optionsRowY)
            } else {
                val iconGap = 4
                val iconWidth = 20
                val dlcWidth = rowW
                if (updateWidget != null) {
                    var defaultUpdateX = rowX + dlcWidth + iconGap
                    if (DlcPathConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                        defaultUpdateX = rowX - iconGap - iconWidth
                    }
                    val updatePos = DlcPathConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconWidth, rowH, defaultUpdateX, rowY)
                    updateWidget.width = iconWidth
                    updateWidget.x = updatePos[0]
                    updateWidget.y = updatePos[1]
                    setWidgetHeight(updateWidget, rowH)
                }
                dlcWidget.x = rowX
                dlcWidget.y = rowY
                dlcWidget.width = dlcWidth
                setWidgetHeight(dlcWidget, rowH)
            }
        }

        private fun setWidgetHeight(widget: AbstractWidget?, height: Int) {
            if (widget == null) {
                return
            }
            try {
                val method: Method = widget.javaClass.getMethod("setHeight", Int::class.javaPrimitiveType)
                method.invoke(widget, height)
                return
            } catch (_: Exception) {
            }
            try {
                val field: Field = AbstractWidget::class.java.getDeclaredField("height")
                field.isAccessible = true
                field.setInt(widget, height)
            } catch (_: Exception) {
            }
        }

        private fun syncEnabledResourcePacks() {
            DlcClientRuntime.syncEnabledResourcePacks()
        }
    }
}
