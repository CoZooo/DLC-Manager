//? if fabric {
package com.cozooo.dlc_manager

import com.cozooo.dlc_manager.client.DlcClientRuntime
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen
import com.cozooo.dlc_manager.client.update.DlcUpdateClient
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.core.DlcConfig
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
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
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
            val y = screen.height / 4 + 32 + 24 * 2
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
            val packButton = Button.builder(Component.translatable("dlc_manager.pack.quick_icon")) {
                DlcClientRuntime.openPackManagerFromTitle(screen)
            }.bounds(x + w + 28, y, 20, h).build()
            val noticeWidth = max(48, Minecraft.getInstance().font.width(Component.translatable("dlc_manager.notice")) + 8)
            val noticeHeight = 20
            val noticeDefaultX = screen.width - noticeWidth - 8
            val noticeDefaultY = screen.height - noticeHeight - 8
            val noticePos = DlcConfig.computeNoticeButtonPosition(
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
            if (DlcConfig.isUButtomShown() &&
                DlcConfig.isUpdateManagerOn() &&
                findButtonByKey(screen, "dlc_manager.update.import_icon") == null
            ) {
                Screens.getButtons(screen).add(updateButton)
            }
            if (DlcConfig.isPButtomShown() &&
                DlcConfig.isPackFuncOn() &&
                findButtonByKey(screen, "dlc_manager.pack.quick_icon") == null
            ) {
                Screens.getButtons(screen).add(packButton)
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

        private fun hasDlcButton(screen: Screen): Boolean {
            for (widget in Screens.getButtons(screen)) {
                val contents = widget.message.contents
                if (contents is TranslatableContents && contents.key == "dlc_manager.title") {
                    return true
                }
            }
            return false
        }

        private fun findRealmsButton(screen: Screen): AbstractWidget? {
            return findRealmsButton(screen.children())
        }

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
            return findButtonByKey(screen.children(), key)
        }

        private fun findButtonByKey(listeners: List<GuiEventListener>, key: String): AbstractWidget? {
            for (child in listeners) {
                if (child !is AbstractWidget) {
                    continue
                }
                val contents = child.message.contents
                if (contents is TranslatableContents && key == contents.key) {
                    return child
                }
            }
            return null
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

        private fun alignTitleButtons(screen: Screen) {
            val realmsWidget = findRealmsButton(screen)
            val multiplayerWidget = findButtonByKey(screen, "menu.multiplayer")
            val modsWidget = findModsButton(screen)
            val dlcWidget = findButtonByKey(screen, "dlc_manager.title") ?: return
            val updateWidget = findButtonByKey(screen, "dlc_manager.update.import_icon")
            val packWidget = findButtonByKey(screen, "dlc_manager.pack.quick_icon")
            if (realmsWidget != null) {
                realmsWidget.visible = false
                realmsWidget.active = false
            }
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
                modsWidget.height = rowH
                //?}
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
                    updateWidget.height = rowH
                    //?}
                    updateWidget.x = updatePos[0]
                    updateWidget.y = updatePos[1]
                }
                if (packWidget != null) {
                    packWidget.width = iconWidth
                    //? if >=1.21.1 {
                    packWidget.height = rowH
                    //?}
                    packWidget.x = updatePos[0] + iconWidth + iconGap
                    packWidget.y = updatePos[1]
                }
                dlcWidget.x = dlcX
                dlcWidget.y = rowY
                dlcWidget.width = dlcWidth
                //? if >=1.21.1 {
                dlcWidget.height = rowH
                //?}

                val optionsRowY = rowY + rowStep
                moveBottomRow(screen, optionsRowY)
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
                    updateWidget.height = rowH
                    //?}
                    updateWidget.x = updatePos[0]
                    updateWidget.y = updatePos[1]
                }
                if (packWidget != null) {
                    packWidget.width = iconWidth
                    //? if >=1.21.1 {
                    packWidget.height = rowH
                    //?}
                    packWidget.x = updatePos[0] + iconWidth + iconGap
                    packWidget.y = updatePos[1]
                }
                dlcWidget.x = rowX
                dlcWidget.y = rowY
                dlcWidget.width = dlcWidth
                //? if >=1.21.1 {
                dlcWidget.height = rowH
                //?}
            }
        }

        private fun syncEnabledResourcePacks() {
            DlcClientRuntime.syncEnabledResourcePacks()
        }
    }
}
//?} else if forge {
package com.cozooo.dlc_manager

import com.cozooo.dlc_manager.client.DlcClientRuntime
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.screen.DlcConfigScreen
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen
import com.cozooo.dlc_manager.client.update.DlcUpdateClient
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.core.DlcConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.ConfigScreenHandler
import net.minecraftforge.client.event.RegisterClientCommandsEvent
import net.minecraftforge.client.event.ScreenEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import kotlin.math.max

@Mod.EventBusSubscriber(modid = DlcManager.MOD_ID, value = [Dist.CLIENT], bus = Mod.EventBusSubscriber.Bus.MOD)
class DlcManagerClient {
    companion object {
        @JvmStatic
        fun init(modBus: IEventBus) {
            modBus.addListener(::onClientSetup)
        }

        private fun onClientSetup(event: FMLClientSetupEvent) {
            DlcUpdateService.ensureUpdateDir()
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory::class.java) {
                ConfigScreenHandler.ConfigScreenFactory { _, parent -> DlcConfigScreen(parent) }
            }
        }

        private fun runStartupChecksAsync(screen: Screen) {
            DlcClientRuntime.runStartupChecksAsync(screen)
        }
    }

    @Mod.EventBusSubscriber(modid = DlcManager.MOD_ID, value = [Dist.CLIENT])
    object ClientEvents {
        private var resourcePackSynced = false

        @SubscribeEvent
        @JvmStatic
        fun onTitleScreenInit(event: ScreenEvent.Init.Post) {
            val screen = event.screen
            if (screen !is net.minecraft.client.gui.screens.TitleScreen) {
                return
            }
            var x = screen.width / 2 - 100
            var y = screen.height / 4 + 48
            var w = 200
            var h = 20
            val button = Button.builder(Component.translatable("dlc_manager.title")) {
                val managerScreen: Screen = DlcMaLiLibScreen(screen)
                if (DlcRemoteManager.tryShowFromManager(managerScreen)) {
                    return@builder
                }
                Minecraft.getInstance().setScreen(managerScreen)
            }.bounds(x, y, w, h).build()
            var replaced = false
            for (child in event.screen.children()) {
                if (child is AbstractWidget) {
                    val contents = child.message.contents
                    val isRealms = contents is TranslatableContents && contents.key == "menu.online"
                    if (isRealms) {
                        child.visible = false
                        child.active = false
                        x = child.x
                        y = child.y
                        w = child.width
                        h = child.height
                        replaced = true
                        break
                    }
                }
            }
            if (replaced) {
                button.width = w
                button.height = h
            }
            val pos = DlcConfig.computeTitleButtonPosition(screen.width, screen.height, w, h, x, y)
            x = pos[0]
            y = pos[1]
            button.x = x
            button.y = y
            event.addListener(button)
            val iconSize = minOf(20, h)
            var iconX = button.x + button.width + 4
            if (DlcConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                iconX = button.x - iconSize - 4
            }
            val iconY = button.y
            val updatePos = DlcConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconSize, h, iconX, iconY)
            val updateButton = Button.builder(Component.translatable("dlc_manager.update.import_icon")) {
                DlcUpdateClient.openUpdateManager(screen)
            }.bounds(updatePos[0], updatePos[1], iconSize, h).build()
            val packButton = Button.builder(Component.translatable("dlc_manager.pack.quick_icon")) {
                DlcClientRuntime.openPackManagerFromTitle(screen)
            }.bounds(updatePos[0] + iconSize + 4, updatePos[1], iconSize, h).build()
            val noticeWidth = max(48, Minecraft.getInstance().font.width(Component.translatable("dlc_manager.notice")) + 8)
            val noticeHeight = 20
            val noticeDefaultX = screen.width - noticeWidth - 8
            val noticeDefaultY = screen.height - noticeHeight - 8
            val noticePos = DlcConfig.computeNoticeButtonPosition(
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
            if (DlcConfig.isUButtomShown() && DlcConfig.isUpdateManagerOn()) {
                event.addListener(updateButton)
            }
            if (DlcConfig.isPButtomShown() && DlcConfig.isPackFuncOn()) {
                event.addListener(packButton)
            }
            if (DlcClientRuntime.shouldShowMainMenuNoticeButton() &&
                findButtonByKey(screen, "dlc_manager.notice") == null
            ) {
                event.addListener(noticeButton)
            }
            DlcClientRuntime.maybeOpenManagerOnFirstRun(screen)
            DlcClientRuntime.runStartupChecksAsync(screen)
        }

        @SubscribeEvent
        @JvmStatic
        fun onTitleScreenRender(event: ScreenEvent.Render.Post) {
            val screen = event.screen
            if (screen !is net.minecraft.client.gui.screens.TitleScreen) {
                return
            }
            DlcClientRuntime.renderTitleNoticePanel(screen, event.guiGraphics)
        }

        @SubscribeEvent
        @JvmStatic
        fun onTitleScreenMousePressed(event: ScreenEvent.MouseButtonPressed.Pre) {
            val screen = event.screen
            if (screen !is net.minecraft.client.gui.screens.TitleScreen) {
                return
            }
            if (DlcClientRuntime.handleTitleNoticePanelClick(screen, event.mouseX, event.mouseY, event.button)) {
                event.isCanceled = true
            }
        }

        @SubscribeEvent
        @JvmStatic
        fun onTitleScreenMouseReleased(event: ScreenEvent.MouseButtonReleased.Pre) {
            val screen = event.screen
            if (screen !is net.minecraft.client.gui.screens.TitleScreen) {
                return
            }
            if (DlcClientRuntime.handleTitleNoticePanelRelease(screen, event.mouseX, event.mouseY, event.button)) {
                event.isCanceled = true
            }
        }

        @SubscribeEvent
        @JvmStatic
        fun onTitleScreenMouseDragged(event: ScreenEvent.MouseDragged.Pre) {
            val screen = event.screen
            if (screen !is net.minecraft.client.gui.screens.TitleScreen) {
                return
            }
            if (DlcClientRuntime.handleTitleNoticePanelDrag(
                    screen,
                    event.mouseX,
                    event.mouseY,
                    event.mouseButton,
                    event.dragX,
                    event.dragY,
                )
            ) {
                event.isCanceled = true
            }
        }

        @SubscribeEvent
        @JvmStatic
        fun onTitleScreenMouseScrolled(event: ScreenEvent.MouseScrolled.Pre) {
            val screen = event.screen
            if (screen !is net.minecraft.client.gui.screens.TitleScreen) {
                return
            }
            if (DlcClientRuntime.handleTitleNoticePanelScroll(screen, event.mouseX, event.mouseY, 0.0, event.scrollDelta)) {
                event.isCanceled = true
            }
        }

        @SubscribeEvent
        @JvmStatic
        fun onRegisterClientCommands(event: RegisterClientCommandsEvent) {
            event.dispatcher.register(
                Commands.literal("dlcmanager")
                    .executes {
                        DlcClientRuntime.openManagerFromCommand()
                        1
                    }
                    .then(
                        Commands.literal("selfupdate").executes {
                            DlcClientRuntime.openSelfUpdateFromCommand()
                            1
                        },
                    )
                    .then(
                        Commands.literal("updatemanager").executes {
                            DlcClientRuntime.openUpdateManagerFromCommand()
                            1
                        },
                    )
                    .then(
                        Commands.literal("update").executes {
                            DlcClientRuntime.openUpdatePromptFromCommand()
                            1
                        },
                    )
                    .then(
                        Commands.literal("server").executes {
                            DlcClientRuntime.openServerManagerFromCommand()
                            1
                        },
                    ),
            )
        }

        @SubscribeEvent
        @JvmStatic
        fun onIngameMenuInit(event: ScreenEvent.Init.Post) {
            val screen = event.screen
            if (screen !is PauseScreen) {
                return
            }
            if (!DlcClientRuntime.canOpenServerManager()) {
                return
            }
            if (DlcClientRuntime.findButtonByKey(screen, "dlc_manager.server.manage") != null) {
                return
            }
            var anchor = DlcClientRuntime.findButtonByKey(screen, "menu.sendFeedback")
            if (anchor == null) {
                anchor = DlcClientRuntime.findButtonByKey(screen, "menu.feedback")
            }
            if (anchor != null) {
                anchor.visible = false
                anchor.active = false
            }
            if (anchor == null) {
                anchor = DlcClientRuntime.findButtonByKey(screen, "menu.mods")
            }
            if (anchor == null) {
                anchor = DlcClientRuntime.findButtonByKey(screen, "menu.options")
            }
            var x = screen.width / 2 - 100
            var y = screen.height / 4 + 120
            var w = 200
            var h = 20
            if (anchor != null) {
                if (DlcClientRuntime.findButtonByKey(screen, "menu.sendFeedback") != null ||
                    DlcClientRuntime.findButtonByKey(screen, "menu.feedback") != null
                ) {
                    x = anchor.x
                    y = anchor.y
                    w = anchor.width
                    h = anchor.height
                } else if (anchor === DlcClientRuntime.findButtonByKey(screen, "menu.mods")) {
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
            event.addListener(button)
        }

        @SubscribeEvent
        @JvmStatic
        fun onClientTick(event: TickEvent.ClientTickEvent) {
            if (event.phase != TickEvent.Phase.END || resourcePackSynced) {
                return
            }
            val minecraft = Minecraft.getInstance()
            if (minecraft.player == null) {
                return
            }
            resourcePackSynced = true
            minecraft.execute { syncEnabledResourcePacks() }
        }

        private fun syncEnabledResourcePacks() {
            DlcClientRuntime.syncEnabledResourcePacks()
        }

        private fun findButtonByKey(screen: Screen, key: String): AbstractWidget? {
            return DlcClientRuntime.findButtonByKey(screen, key)
        }
    }
}
//?} else if neoforge {
package com.cozooo.dlc_manager

import com.cozooo.dlc_manager.client.DlcClientRuntime
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.screen.DlcConfigScreen
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen
import com.cozooo.dlc_manager.client.update.DlcUpdateClient
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.core.DlcConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge
import kotlin.math.max

class DlcManagerClient {
    companion object {
        @JvmStatic
        fun init(modBus: IEventBus) {
            modBus.addListener(::onClientSetup)
            NeoForge.EVENT_BUS.register(ClientEvents())
        }

        private fun onClientSetup(event: FMLClientSetupEvent) {
            DlcUpdateService.ensureUpdateDir()
            ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory::class.java) {
                IConfigScreenFactory { _, parent -> DlcConfigScreen(parent) }
            }
        }

        private fun findRealmsButton(screen: Screen): AbstractWidget? {
            return findRealmsButton(screen.children())
        }

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
            return findButtonByKey(screen.children(), key)
        }

        private fun findButtonByKey(listeners: List<GuiEventListener>, key: String): AbstractWidget? {
            for (child in listeners) {
                if (child !is AbstractWidget) {
                    continue
                }
                val contents = child.message.contents
                if (contents is TranslatableContents && key == contents.key) {
                    return child
                }
            }
            return null
        }

        private fun findModsButton(screen: Screen): AbstractWidget? {
            for (child in screen.children()) {
                if (child is net.neoforged.neoforge.client.gui.widget.ModsButton) {
                    return child
                }
            }
            return findButtonByKey(screen, "fml.menu.mods")
        }

        private fun openServerManagerFromIngame(parent: Screen) {
            DlcClientRuntime.openServerManagerFromIngame(parent)
        }

        private fun shiftButtonRow(screen: Screen, deltaY: Int, vararg keys: String) {
            for (key in keys) {
                val widget = findButtonByKey(screen, key)
                if (widget != null) {
                    widget.y = widget.y - deltaY
                }
            }
        }

        private fun runStartupChecksAsync(screen: Screen) {
            DlcClientRuntime.runStartupChecksAsync(screen)
        }
    }

    class ClientEvents {
        private var resourcePackSynced = false

        @SubscribeEvent
        fun onTitleScreenInit(event: ScreenEvent.Init.Post) {
            val screen = event.screen
            if (screen !is net.minecraft.client.gui.screens.TitleScreen) {
                return
            }

            var x = screen.width / 2 - 100
            var y = screen.height / 4 + 32 + 24 * 2
            var w = 200
            var h = 20
            val button = Button.builder(Component.translatable("dlc_manager.title")) {
                val managerScreen: Screen = DlcMaLiLibScreen(screen)
                if (DlcRemoteManager.tryShowFromManager(managerScreen)) {
                    return@builder
                }
                Minecraft.getInstance().setScreen(managerScreen)
            }.bounds(x, y, w, h).build()
            val realmsWidget = findRealmsButton(screen)
            if (realmsWidget != null) {
                realmsWidget.visible = false
                realmsWidget.active = false
            }

            var anchored = false
            val modsWidget = findModsButton(screen)
            if (modsWidget != null) {
                if (realmsWidget != null) {
                    val rowStep = modsWidget.height + 4
                    modsWidget.y = modsWidget.y - rowStep
                    shiftButtonRow(screen, rowStep, "menu.options", "menu.quit", "options.language", "options.accessibility")
                }
                val gap = 4
                val fullWidth = modsWidget.width
                val halfWidth = max(40, (fullWidth - gap) / 2)
                modsWidget.width = halfWidth
                x = modsWidget.x + halfWidth + gap
                y = modsWidget.y
                w = halfWidth
                h = modsWidget.height
                button.x = x
                button.y = y
                button.width = w
                button.height = h
                anchored = true
            }

            if (!anchored) {
                if (realmsWidget != null) {
                    x = realmsWidget.x
                    y = realmsWidget.y
                    w = realmsWidget.width
                    h = realmsWidget.height
                    button.x = x
                    button.y = y
                    button.width = w
                    button.height = h
                } else {
                    val multiplayerWidget = findButtonByKey(screen, "menu.multiplayer")
                    if (multiplayerWidget != null) {
                        val gap = 4
                        x = multiplayerWidget.x
                        y = multiplayerWidget.y + multiplayerWidget.height + gap
                        w = multiplayerWidget.width
                        h = multiplayerWidget.height
                        button.x = x
                        button.y = y
                        button.width = w
                        button.height = h
                    }
                }
            }
            button.width = w
            button.height = h
            val pos = DlcConfig.computeTitleButtonPosition(screen.width, screen.height, w, h, x, y)
            x = pos[0]
            y = pos[1]
            button.x = x
            button.y = y
            event.addListener(button)
            val iconSize = minOf(20, h)
            var iconX = button.x + button.width + 4
            if (DlcConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                iconX = button.x - iconSize - 4
            }
            val iconY = button.y
            val updatePos = DlcConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconSize, h, iconX, iconY)
            val updateButton = Button.builder(Component.translatable("dlc_manager.update.import_icon")) {
                DlcUpdateClient.openUpdateManager(screen)
            }.bounds(updatePos[0], updatePos[1], iconSize, h).build()
            val packButton = Button.builder(Component.translatable("dlc_manager.pack.quick_icon")) {
                DlcClientRuntime.openPackManagerFromTitle(screen)
            }.bounds(updatePos[0] + iconSize + 4, updatePos[1], iconSize, h).build()
            val noticeWidth = max(48, Minecraft.getInstance().font.width(Component.translatable("dlc_manager.notice")) + 8)
            val noticeHeight = 20
            val noticeDefaultX = screen.width - noticeWidth - 8
            val noticeDefaultY = screen.height - noticeHeight - 8
            val noticePos = DlcConfig.computeNoticeButtonPosition(
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
            if (DlcConfig.isUButtomShown() && DlcConfig.isUpdateManagerOn()) {
                event.addListener(updateButton)
            }
            if (DlcConfig.isPButtomShown() && DlcConfig.isPackFuncOn()) {
                event.addListener(packButton)
            }
            if (DlcClientRuntime.shouldShowMainMenuNoticeButton() &&
                findButtonByKey(screen, "dlc_manager.notice") == null
            ) {
                event.addListener(noticeButton)
            }
            DlcClientRuntime.maybeOpenManagerOnFirstRun(screen)
            runStartupChecksAsync(screen)
        }

        @SubscribeEvent
        fun onTitleScreenRender(event: ScreenEvent.Render.Post) {
            val screen = event.screen
            if (screen !is net.minecraft.client.gui.screens.TitleScreen) {
                return
            }
            DlcClientRuntime.renderTitleNoticePanel(screen, event.guiGraphics)
        }

        @SubscribeEvent
        fun onTitleScreenMousePressed(event: ScreenEvent.MouseButtonPressed.Pre) {
            val screen = event.screen
            if (screen !is net.minecraft.client.gui.screens.TitleScreen) {
                return
            }
            if (DlcClientRuntime.handleTitleNoticePanelClick(screen, event.mouseX, event.mouseY, event.button)) {
                event.isCanceled = true
            }
        }

        @SubscribeEvent
        fun onTitleScreenMouseReleased(event: ScreenEvent.MouseButtonReleased.Pre) {
            val screen = event.screen
            if (screen !is net.minecraft.client.gui.screens.TitleScreen) {
                return
            }
            if (DlcClientRuntime.handleTitleNoticePanelRelease(screen, event.mouseX, event.mouseY, event.button)) {
                event.isCanceled = true
            }
        }

        @SubscribeEvent
        fun onTitleScreenMouseDragged(event: ScreenEvent.MouseDragged.Pre) {
            val screen = event.screen
            if (screen !is net.minecraft.client.gui.screens.TitleScreen) {
                return
            }
            if (DlcClientRuntime.handleTitleNoticePanelDrag(
                    screen,
                    event.mouseX,
                    event.mouseY,
                    event.mouseButton,
                    event.dragX,
                    event.dragY,
                )
            ) {
                event.isCanceled = true
            }
        }

        @SubscribeEvent
        fun onTitleScreenMouseScrolled(event: ScreenEvent.MouseScrolled.Pre) {
            val screen = event.screen
            if (screen !is net.minecraft.client.gui.screens.TitleScreen) {
                return
            }
            if (DlcClientRuntime.handleTitleNoticePanelScroll(
                    screen,
                    event.mouseX,
                    event.mouseY,
                    event.scrollDeltaX,
                    event.scrollDeltaY,
                )
            ) {
                event.isCanceled = true
            }
        }

        @SubscribeEvent
        fun onRegisterClientCommands(event: RegisterClientCommandsEvent) {
            event.dispatcher.register(
                Commands.literal("dlcmanager")
                    .executes {
                        openManagerFromCommand()
                        1
                    }
                    .then(
                        Commands.literal("selfupdate").executes {
                            openSelfUpdateFromCommand()
                            1
                        },
                    )
                    .then(
                        Commands.literal("updatemanager").executes {
                            openUpdateManagerFromCommand()
                            1
                        },
                    )
                    .then(
                        Commands.literal("update").executes {
                            openUpdatePromptFromCommand()
                            1
                        },
                    )
                    .then(
                        Commands.literal("server").executes {
                            openServerManagerFromCommand()
                            1
                        },
                    ),
            )
        }

        @SubscribeEvent
        fun onIngameMenuInit(event: ScreenEvent.Init.Post) {
            val screen = event.screen
            if (screen !is PauseScreen) {
                return
            }
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
            event.addListener(button)
        }

        @SubscribeEvent
        fun onClientTick(event: ClientTickEvent.Post) {
            if (resourcePackSynced) {
                return
            }
            val minecraft = Minecraft.getInstance()
            if (minecraft.player == null) {
                return
            }
            resourcePackSynced = true
            minecraft.execute { syncEnabledResourcePacks() }
        }

        private fun syncEnabledResourcePacks() {
            DlcClientRuntime.syncEnabledResourcePacks()
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
    }
}
//?}
