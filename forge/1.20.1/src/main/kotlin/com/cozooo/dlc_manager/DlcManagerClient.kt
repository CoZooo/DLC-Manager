package com.cozooo.dlc_manager

import com.cozooo.dlc_manager.client.DlcClientRuntime
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.screen.DlcConfigScreen
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen
import com.cozooo.dlc_manager.client.update.DlcUpdateClient
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.core.DlcPathConfig
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
            val pos = DlcPathConfig.computeTitleButtonPosition(screen.width, screen.height, w, h, x, y)
            x = pos[0]
            y = pos[1]
            button.x = x
            button.y = y
            event.addListener(button)
            val iconSize = minOf(20, h)
            var iconX = button.x + button.width + 4
            if (DlcPathConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                iconX = button.x - iconSize - 4
            }
            val iconY = button.y
            val updatePos = DlcPathConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconSize, h, iconX, iconY)
            val updateButton = Button.builder(Component.translatable("dlc_manager.update.import_icon")) {
                DlcUpdateClient.openUpdateManager(screen)
            }.bounds(updatePos[0], updatePos[1], iconSize, h).build()
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
            if (DlcPathConfig.isUButtomShown() && DlcPathConfig.isUpdateManagerOn()) {
                event.addListener(updateButton)
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
