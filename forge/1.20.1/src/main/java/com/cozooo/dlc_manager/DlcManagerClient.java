package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.client.DlcClientRuntime;
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen;
import com.cozooo.dlc_manager.client.screen.DlcConfigScreen;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = DlcManager.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DlcManagerClient {
    public static void init(IEventBus modBus) {
        modBus.addListener(DlcManagerClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        DlcUpdateService.ensureUpdateDir();
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new DlcConfigScreen(parent)));
    }

    @Mod.EventBusSubscriber(modid = DlcManager.MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {
        private static boolean resourcePackSynced = false;

        @SubscribeEvent
        public static void onTitleScreenInit(ScreenEvent.Init.Post event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                return;
            }
            int x = screen.width / 2 - 100;
            int y = screen.height / 4 + 48;
            int w = 200;
            int h = 20;
            Button button = Button.builder(Component.translatable("dlc_manager.title"), btn -> {
                Screen managerScreen = new DlcMaLiLibScreen(screen);
                if (DlcRemoteManager.tryShowFromManager(managerScreen)) {
                    return;
                }
                Minecraft.getInstance().setScreen(managerScreen);
            }).bounds(x, y, w, h).build();
            boolean replaced = false;
            for (var child : event.getScreen().children()) {
                if (child instanceof AbstractWidget widget) {
                    boolean isRealms = false;
                    if (widget.getMessage().getContents() instanceof TranslatableContents contents) {
                        isRealms = "menu.online".equals(contents.getKey());
                    }
                    if (isRealms) {
                        widget.visible = false;
                        widget.active = false;
                        x = widget.getX();
                        y = widget.getY();
                        w = widget.getWidth();
                        h = widget.getHeight();
                        replaced = true;
                        break;
                    }
                }
            }
            if (replaced) {
                button.setWidth(w);
                button.setHeight(h);
            }
            int[] pos = DlcPathConfig.computeTitleButtonPosition(screen.width, screen.height, w, h, x, y);
            x = pos[0];
            y = pos[1];
            button.setX(x);
            button.setY(y);
            event.addListener(button);
            int iconSize = Math.min(20, h);
            int iconX = button.getX() + button.getWidth() + 4;
            if (DlcPathConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                iconX = button.getX() - iconSize - 4;
            }
            int iconY = button.getY();
            int[] updatePos = DlcPathConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconSize, h, iconX, iconY);
            Button updateButton = Button.builder(Component.translatable("dlc_manager.update.import_icon"), btn -> DlcUpdateClient.openUpdateManager(screen))
                .bounds(updatePos[0], updatePos[1], iconSize, h)
                .build();
            Button packButton = Button.builder(Component.translatable("dlc_manager.pack.quick_icon"), btn -> DlcClientRuntime.openPackManagerFromTitle(screen))
                .bounds(updatePos[0] + iconSize + 4, updatePos[1], iconSize, h)
                .build();
            int noticeWidth = Math.max(48, Minecraft.getInstance().font.width(Component.translatable("dlc_manager.notice")) + 8);
            int noticeHeight = 20;
            int noticeDefaultX = screen.width - noticeWidth - 8;
            int noticeDefaultY = screen.height - noticeHeight - 8;
            int[] noticePos = DlcPathConfig.computeNoticeButtonPosition(
                screen.width, screen.height, noticeWidth, noticeHeight, noticeDefaultX, noticeDefaultY
            );
            Button noticeButton = Button.builder(Component.translatable("dlc_manager.notice"),
                    btn -> DlcClientRuntime.openNoticeFromTitle(screen))
                .bounds(noticePos[0], noticePos[1], noticeWidth, noticeHeight)
                .build();
            if (DlcPathConfig.isUButtomShown() && DlcPathConfig.isUpdateManagerOn()) {
                event.addListener(updateButton);
            }
            if (DlcPathConfig.isPButtomShown() && DlcPathConfig.isPackFuncOn()) {
                event.addListener(packButton);
            }
            if (DlcClientRuntime.shouldShowMainMenuNoticeButton()
                && findButtonByKey(screen, "dlc_manager.notice") == null) {
                event.addListener(noticeButton);
            }
            DlcClientRuntime.maybeOpenManagerOnFirstRun(screen);
            DlcClientRuntime.runStartupChecksAsync(screen);
        }

        @SubscribeEvent
        public static void onTitleScreenRender(ScreenEvent.Render.Post event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                return;
            }
            DlcClientRuntime.renderTitleNoticePanel(screen, event.getGuiGraphics());
        }

        @SubscribeEvent
        public static void onTitleScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                return;
            }
            if (DlcClientRuntime.handleTitleNoticePanelClick(
                screen, event.getMouseX(), event.getMouseY(), event.getButton()
            )) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onTitleScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                return;
            }
            if (DlcClientRuntime.handleTitleNoticePanelRelease(screen, event.getMouseX(), event.getMouseY(), event.getButton())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onTitleScreenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                return;
            }
            if (DlcClientRuntime.handleTitleNoticePanelDrag(
                screen, event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY()
            )) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onTitleScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                return;
            }
            if (DlcClientRuntime.handleTitleNoticePanelScroll(screen, event.getMouseX(), event.getMouseY(), 0.0D, event.getScrollDelta())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("dlcmanager")
                .executes(ctx -> {
                    DlcClientRuntime.openManagerFromCommand();
                    return 1;
                })
                .then(Commands.literal("selfupdate").executes(ctx -> {
                    DlcClientRuntime.openSelfUpdateFromCommand();
                    return 1;
                }))
                .then(Commands.literal("updatemanager").executes(ctx -> {
                    DlcClientRuntime.openUpdateManagerFromCommand();
                    return 1;
                }))
                .then(Commands.literal("update").executes(ctx -> {
                    DlcClientRuntime.openUpdatePromptFromCommand();
                    return 1;
                }))
                .then(Commands.literal("server").executes(ctx -> {
                    DlcClientRuntime.openServerManagerFromCommand();
                    return 1;
                }))
            );
        }

        @SubscribeEvent
        public static void onIngameMenuInit(ScreenEvent.Init.Post event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof PauseScreen)) {
                return;
            }
            if (!DlcClientRuntime.canOpenServerManager()) {
                return;
            }
            if (DlcClientRuntime.findButtonByKey(screen, "dlc_manager.server.manage") != null) {
                return;
            }
            AbstractWidget anchor = DlcClientRuntime.findButtonByKey(screen, "menu.sendFeedback");
            if (anchor == null) {
                anchor = DlcClientRuntime.findButtonByKey(screen, "menu.feedback");
            }
            if (anchor != null) {
                anchor.visible = false;
                anchor.active = false;
            }
            if (anchor == null) {
                anchor = DlcClientRuntime.findButtonByKey(screen, "menu.mods");
            }
            if (anchor == null) {
                anchor = DlcClientRuntime.findButtonByKey(screen, "menu.options");
            }
            int x = screen.width / 2 - 100;
            int y = screen.height / 4 + 120;
            int w = 200;
            int h = 20;
                if (anchor != null) {
                if (DlcClientRuntime.findButtonByKey(screen, "menu.sendFeedback") != null || DlcClientRuntime.findButtonByKey(screen, "menu.feedback") != null) {
                    x = anchor.getX();
                    y = anchor.getY();
                    w = anchor.getWidth();
                    h = anchor.getHeight();
                } else if (anchor == DlcClientRuntime.findButtonByKey(screen, "menu.mods")) {
                    int gap = 4;
                    int originalWidth = anchor.getWidth();
                    int halfWidth = Math.max(20, (originalWidth - gap) / 2);
                    anchor.setWidth(halfWidth);
                    x = anchor.getX() + halfWidth + gap;
                    y = anchor.getY();
                    w = Math.max(20, originalWidth - halfWidth - gap);
                    h = anchor.getHeight();
                } else {
                    x = anchor.getX();
                    y = anchor.getY() + anchor.getHeight() + 4;
                    w = anchor.getWidth();
                    h = anchor.getHeight();
                }
            }
            Button button = Button.builder(Component.translatable("dlc_manager.server.manage"),
                btn -> DlcClientRuntime.openServerManagerFromIngame(screen))
                .bounds(x, y, w, h).build();
            event.addListener(button);
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || resourcePackSynced) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }
            resourcePackSynced = true;
            minecraft.execute(DlcClientRuntime::syncEnabledResourcePacks);
        }

        private static void syncEnabledResourcePacks() {
            DlcClientRuntime.syncEnabledResourcePacks();
        }

        private static AbstractWidget findButtonByKey(Screen screen, String key) {
            return DlcClientRuntime.findButtonByKey(screen, key);
        }

        private static boolean canOpenServerManager() {
            return DlcClientRuntime.canOpenServerManager();
        }

        private static void openManagerFromCommand() {
            DlcClientRuntime.openManagerFromCommand();
        }

        private static void openSelfUpdateFromCommand() {
            DlcClientRuntime.openSelfUpdateFromCommand();
        }

        private static void openUpdateManagerFromCommand() {
            DlcClientRuntime.openUpdateManagerFromCommand();
        }

        private static void openUpdatePromptFromCommand() {
            DlcClientRuntime.openUpdatePromptFromCommand();
        }

        private static void openServerManagerFromCommand() {
            DlcClientRuntime.openServerManagerFromCommand();
        }

        private static void openServerManagerFromIngame(Screen parent) {
            DlcClientRuntime.openServerManagerFromIngame(parent);
        }

    }

    private static void runStartupChecksAsync(Screen screen) {
        DlcClientRuntime.runStartupChecksAsync(screen);
    }
}
