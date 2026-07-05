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
