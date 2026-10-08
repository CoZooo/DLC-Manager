package com.cozooo.dlc_manager.client

import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.required.DlcRequiredManager
import com.cozooo.dlc_manager.client.screen.DlcManagerScreen
import com.cozooo.dlc_manager.client.update.DlcUpdateClient
import com.cozooo.dlc_manager.core.DlcConfig
import dev.architectury.event.EventResult
import dev.architectury.event.events.client.ClientCommandRegistrationEvent
import dev.architectury.event.events.client.ClientGuiEvent
import dev.architectury.event.events.client.ClientScreenInputEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.hooks.client.screen.ScreenAccess
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import kotlin.math.abs
import kotlin.math.max

object DlcClientEvents {
    private var registered = false
    private var resourcePackSynced = false
    private var pendingAlignScreen: Screen? = null

    @JvmStatic
    fun register() {
        if (registered) return
        registered = true
        registerCommands()
        registerGuiEvents()
        registerInputEvents()
        registerTickEvents()
    }

    private fun registerCommands() {
        ClientCommandRegistrationEvent.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandRegistrationEvent.literal("dlcmanager")
                    .executes {
                        DlcClientRuntime.openManagerFromCommand()
                        1
                    }
                    .then(
                        ClientCommandRegistrationEvent.literal("selfupdate").executes {
                            DlcClientRuntime.openSelfUpdateFromCommand()
                            1
                        }
                    )
                    .then(
                        ClientCommandRegistrationEvent.literal("updatemanager").executes {
                            DlcClientRuntime.openUpdateManagerFromCommand()
                            1
                        }
                    )
                    .then(
                        ClientCommandRegistrationEvent.literal("update").executes {
                            DlcClientRuntime.openUpdatePromptFromCommand()
                            1
                        }
                    )
                    .then(
                        ClientCommandRegistrationEvent.literal("server").executes {
                            DlcClientRuntime.openServerManagerFromCommand()
                            1
                        }
                    )
            )
        }
    }

    private fun registerGuiEvents() {
        ClientGuiEvent.INIT_POST.register { screen, access ->
            when (screen) {
                is PauseScreen -> addPauseScreenButton(screen, access)
                is TitleScreen -> addTitleScreenButtons(screen, access)
            }
        }
        ClientGuiEvent.RENDER_POST.register { screen, guiGraphics, mouseX, mouseY, _ ->
            if (screen is TitleScreen) {
                DlcClientRuntime.renderTitleNoticePanel(screen, guiGraphics, mouseX, mouseY)
            }
        }
    }

    private fun registerInputEvents() {
        ClientScreenInputEvent.MOUSE_CLICKED_PRE.register { _, screen, mouseX, mouseY, button ->
            if (screen is TitleScreen && DlcClientRuntime.handleTitleNoticePanelClick(screen, mouseX, mouseY, button)) {
                EventResult.interruptFalse()
            } else {
                EventResult.pass()
            }
        }
        ClientScreenInputEvent.MOUSE_RELEASED_PRE.register { _, screen, mouseX, mouseY, button ->
            if (screen is TitleScreen && DlcClientRuntime.handleTitleNoticePanelRelease(screen, mouseX, mouseY, button)) {
                EventResult.interruptFalse()
            } else {
                EventResult.pass()
            }
        }
        ClientScreenInputEvent.MOUSE_DRAGGED_PRE.register { _, screen, mouseX, mouseY, button, dragX, dragY ->
            if (screen is TitleScreen && DlcClientRuntime.handleTitleNoticePanelDrag(screen, mouseX, mouseY, button, dragX, dragY)) {
                EventResult.interruptFalse()
            } else {
                EventResult.pass()
            }
        }
        //? if >=1.21.1 {
        /*ClientScreenInputEvent.MOUSE_SCROLLED_PRE.register { _, screen, mouseX, mouseY, amountX, amountY ->
            if (screen is TitleScreen && DlcClientRuntime.handleTitleNoticePanelScroll(screen, mouseX, mouseY, amountX, amountY)) {
                EventResult.interruptFalse()
            } else {
                EventResult.pass()
            }
        }
        *///?} else {
        ClientScreenInputEvent.MOUSE_SCROLLED_PRE.register { _, screen, mouseX, mouseY, amount ->
            if (screen is TitleScreen && DlcClientRuntime.handleTitleNoticePanelScroll(screen, mouseX, mouseY, 0.0, amount)) {
                EventResult.interruptFalse()
            } else {
                EventResult.pass()
            }
        }
        //?}
    }

    private fun registerTickEvents() {
        ClientTickEvent.CLIENT_POST.register { client ->
            pendingAlignScreen?.let { screen ->
                if (client.screen === screen) {
                    alignTitleButtons(screen)
                }
                pendingAlignScreen = null
            }
            if (resourcePackSynced || client.player == null) return@register
            resourcePackSynced = true
            client.execute(DlcClientRuntime::syncEnabledResourcePacks)
        }
    }

    private fun addTitleScreenButtons(screen: Screen, access: ScreenAccess) {
        val x = screen.width / 2 - 100
        val y = screen.height / 4 + 32 + 24 * 2
        val w = 200
        val h = 20
        val button = Button.builder(Component.translatable("dlc_manager.title")) {
            val managerScreen = DlcManagerScreen(screen)
            if (DlcRemoteManager.tryShowFromManager(managerScreen)) return@builder
            Minecraft.getInstance().setScreen(managerScreen)
        }.bounds(x, y, w, h).build()
        val updateButton = Button.builder(Component.translatable("dlc_manager.update.import_icon")) {
            DlcUpdateClient.openUpdateManager(screen)
        }.bounds(x + w + 4, y, 20, h).build()
        val packButton = Button.builder(Component.translatable("dlc_manager.pack.quick_icon")) {
            DlcClientRuntime.openPackManagerFromTitle(screen)
        }.bounds(x + w + 28, y, 20, h).build()
        val noticeWidth = max(48, Minecraft.getInstance().font.width(Component.translatable("dlc_manager.notice")) + 8)
        val noticeHeight = 20
        val noticeDefaultX = screen.width - noticeWidth - 8
        val noticeDefaultY = screen.height - noticeHeight - 8
        val noticePos = DlcConfig.computeNoticeButtonPosition(
            screen.width, screen.height, noticeWidth, noticeHeight, noticeDefaultX, noticeDefaultY
        )
        val noticeButton = Button.builder(Component.translatable("dlc_manager.notice")) {
            DlcClientRuntime.openNoticeFromTitle(screen)
        }.bounds(noticePos[0], noticePos[1], noticeWidth, noticeHeight).build()

        if (findButtonByKey(screen, "dlc_manager.title") == null) {
            access.addRenderableWidget(button)
        }
        if (DlcConfig.isUButtomShown() && DlcConfig.isUpdateManagerOn()
            && findButtonByKey(screen, "dlc_manager.update.import_icon") == null
        ) {
            access.addRenderableWidget(updateButton)
        }
        if (DlcConfig.isPButtomShown() && DlcConfig.isPackFuncOn()
            && findButtonByKey(screen, "dlc_manager.pack.quick_icon") == null
        ) {
            access.addRenderableWidget(packButton)
        }
        if (DlcClientRuntime.shouldShowMainMenuNoticeButton()
            && findButtonByKey(screen, "dlc_manager.notice") == null
        ) {
            access.addRenderableWidget(noticeButton)
        }
        alignTitleButtons(screen)
        pendingAlignScreen = screen
        if (DlcRequiredManager.tryShowFromTitle(screen)) return
        DlcClientRuntime.maybeOpenManagerOnFirstRun(screen)
        DlcClientRuntime.runStartupChecksAsync(screen)
    }

    private fun addPauseScreenButton(screen: Screen, access: ScreenAccess) {
        if (!DlcClientRuntime.canOpenServerManager() || findButtonByKey(screen, "dlc_manager.server.manage") != null) {
            return
        }
        var anchor = findButtonByKey(screen, "menu.sendFeedback")
        if (anchor == null) {
            anchor = findButtonByKey(screen, "menu.feedback")
        }
        val feedbackAnchor = anchor != null
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
            if (feedbackAnchor) {
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
            DlcClientRuntime.openServerManagerFromIngame(screen)
        }.bounds(x, y, w, h).build()
        access.addRenderableWidget(button)
    }

    private fun findRealmsButton(screen: Screen): AbstractWidget? = findRealmsButton(screen.children())

    private fun findRealmsButton(listeners: List<GuiEventListener>): AbstractWidget? {
        for (child in listeners) {
            if (child is AbstractWidget && isRealmsButton(child)) {
                return child
            }
        }
        return null
    }

    private fun isRealmsButton(widget: AbstractWidget): Boolean {
        val contents = widget.message.contents
        if (contents is TranslatableContents) {
            val key = contents.key
            return key == "menu.online" || key == "menu.realms"
        }
        return false
    }

    private fun findButtonByKey(screen: Screen, key: String): AbstractWidget? {
        for (child in screen.children()) {
            if (child !is AbstractWidget) continue
            val contents = child.message.contents
            if (contents is TranslatableContents && key == contents.key) {
                return child
            }
        }
        return null
    }

    private fun findModsButton(screen: Screen): AbstractWidget? {
        var widget = findButtonByKey(screen, "modmenu.title")
        if (widget != null) return widget
        widget = findButtonByKey(screen, "menu.mods")
        if (widget != null) return widget
        return findButtonByKey(screen, "fml.menu.mods")
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

    private fun alignTitleButtons(screen: Screen) {
        val realmsWidget = findRealmsButton(screen)
        val multiplayerWidget = findButtonByKey(screen, "menu.multiplayer")
        val modsWidget = findModsButton(screen)
        val dlcWidget = findButtonByKey(screen, "dlc_manager.title")
        val updateWidget = findButtonByKey(screen, "dlc_manager.update.import_icon")
        val packWidget = findButtonByKey(screen, "dlc_manager.pack.quick_icon")
        if (realmsWidget != null) {
            realmsWidget.visible = false
            realmsWidget.active = false
        }
        if (dlcWidget == null) return
        var rowStep = 24
        var rowGap = 4
        var rowX = screen.width / 2 - 100
        var rowY = screen.height / 4 + 48
        var rowW = 200
        var rowH = 20
        val anchor = multiplayerWidget ?: realmsWidget
        if (anchor != null) {
            rowGap = 2
            rowStep = anchor.height + 4
            rowX = anchor.x
            rowW = anchor.width
            rowH = anchor.height
            rowY = anchor.y + anchor.height + rowGap
            if (anchor === realmsWidget && multiplayerWidget == null) {
                rowY = anchor.y
            }
        }
        val pos = DlcConfig.computeTitleButtonPosition(screen.width, screen.height, rowW, rowH, rowX, rowY)
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
            //? if >=1.21.1 {
            /*modsWidget.height = rowH
            *///?}
            val dlcX = rowX + halfWidth + gap
            val dlcWidth = halfWidth
            var defaultUpdateX = dlcX + dlcWidth + iconGap
            if (DlcConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                defaultUpdateX = dlcX - iconGap - iconWidth
            }
            val updatePos = DlcConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconWidth, rowH, defaultUpdateX, rowY)
            if (updateWidget != null) {
                updateWidget.width = iconWidth
                //? if >=1.21.1 {
                /*updateWidget.height = rowH
                *///?}
                updateWidget.x = updatePos[0]
                updateWidget.y = updatePos[1]
            }
            if (packWidget != null) {
                packWidget.width = iconWidth
                //? if >=1.21.1 {
                /*packWidget.height = rowH
                *///?}
                packWidget.x = updatePos[0] + iconWidth + iconGap
                packWidget.y = updatePos[1]
            }
            dlcWidget.x = dlcX
            dlcWidget.y = rowY
            dlcWidget.width = dlcWidth
            //? if >=1.21.1 {
            /*dlcWidget.height = rowH
            *///?}
            moveBottomRow(screen, rowY + rowStep)
        } else {
            val iconGap = 4
            val iconWidth = 20
            val dlcWidth = rowW
            var defaultUpdateX = rowX + dlcWidth + iconGap
            if (DlcConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                defaultUpdateX = rowX - iconGap - iconWidth
            }
            val updatePos = DlcConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconWidth, rowH, defaultUpdateX, rowY)
            if (updateWidget != null) {
                updateWidget.width = iconWidth
                //? if >=1.21.1 {
                /*updateWidget.height = rowH
                *///?}
                updateWidget.x = updatePos[0]
                updateWidget.y = updatePos[1]
            }
            if (packWidget != null) {
                packWidget.width = iconWidth
                //? if >=1.21.1 {
                /*packWidget.height = rowH
                *///?}
                packWidget.x = updatePos[0] + iconWidth + iconGap
                packWidget.y = updatePos[1]
            }
            dlcWidget.x = rowX
            dlcWidget.y = rowY
            dlcWidget.width = dlcWidth
            //? if >=1.21.1 {
            /*dlcWidget.height = rowH
            *///?}
        }
    }
}
