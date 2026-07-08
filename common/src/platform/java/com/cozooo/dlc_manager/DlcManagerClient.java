//? if fabric {
package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.client.DlcClientRuntime;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.core.DlcConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public class DlcManagerClient implements ClientModInitializer {
    private static boolean resourcePackSynced = false;
    private static Screen pendingAlignScreen;

    @Override
    public void onInitializeClient() {
        DlcUpdateService.ensureUpdateDir();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("dlcmanager")
                .executes(ctx -> {
                    openManagerFromCommand();
                    return 1;
                })
                .then(ClientCommandManager.literal("selfupdate").executes(ctx -> {
                    openSelfUpdateFromCommand();
                    return 1;
                }))
                .then(ClientCommandManager.literal("updatemanager").executes(ctx -> {
                    openUpdateManagerFromCommand();
                    return 1;
                }))
                .then(ClientCommandManager.literal("update").executes(ctx -> {
                    openUpdatePromptFromCommand();
                    return 1;
                }))
                .then(ClientCommandManager.literal("server").executes(ctx -> {
                    openServerManagerFromCommand();
                    return 1;
                }))
        ));
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen) {
                if (!canOpenServerManager()) {
                    return;
                }
                if (findButtonByKey(screen, "dlc_manager.server.manage") != null) {
                    return;
                }
                AbstractWidget anchor = findButtonByKey(screen, "menu.sendFeedback");
                if (anchor == null) {
                    anchor = findButtonByKey(screen, "menu.feedback");
                }
                if (anchor != null) {
                    anchor.visible = false;
                    anchor.active = false;
                }
                if (anchor == null) {
                    anchor = findButtonByKey(screen, "menu.mods");
                }
                if (anchor == null) {
                    anchor = findButtonByKey(screen, "menu.options");
                }
                int x = screen.width / 2 - 100;
                int y = screen.height / 4 + 120;
                int w = 200;
                int h = 20;
                if (anchor != null) {
                    if (findButtonByKey(screen, "menu.sendFeedback") != null || findButtonByKey(screen, "menu.feedback") != null) {
                        x = anchor.getX();
                        y = anchor.getY();
                        w = anchor.getWidth();
                        h = anchor.getHeight();
                    } else if (anchor == findButtonByKey(screen, "menu.mods")) {
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
                        btn -> openServerManagerFromIngame(screen))
                    .bounds(x, y, w, h)
                    .build();
                Screens.getButtons(screen).add(button);
            }
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                return;
            }

            int x = screen.width / 2 - 100;
            int y = screen.height / 4 + 32 + 24 * 2;
            int w = 200;
            int h = 20;
            Button button = Button.builder(Component.translatable("dlc_manager.title"), btn -> {
                Screen managerScreen = new DlcMaLiLibScreen(screen);
                if (DlcRemoteManager.tryShowFromManager(managerScreen)) {
                    return;
                }
                Minecraft.getInstance().setScreen(managerScreen);
            }).bounds(x, y, w, h).build();
            Button updateButton = Button.builder(Component.translatable("dlc_manager.update.import_icon"),
                btn -> DlcUpdateClient.openUpdateManager(screen))
                .bounds(x + w + 4, y, 20, h)
                .build();
            Button packButton = Button.builder(Component.translatable("dlc_manager.pack.quick_icon"),
                btn -> DlcClientRuntime.openPackManagerFromTitle(screen))
                .bounds(x + w + 28, y, 20, h)
                .build();
            int noticeWidth = Math.max(48, Minecraft.getInstance().font.width(Component.translatable("dlc_manager.notice")) + 8);
            int noticeHeight = 20;
            int noticeDefaultX = screen.width - noticeWidth - 8;
            int noticeDefaultY = screen.height - noticeHeight - 8;
            int[] noticePos = DlcConfig.computeNoticeButtonPosition(
                screen.width, screen.height, noticeWidth, noticeHeight, noticeDefaultX, noticeDefaultY
            );
            Button noticeButton = Button.builder(Component.translatable("dlc_manager.notice"),
                    btn -> DlcClientRuntime.openNoticeFromTitle(screen))
                .bounds(noticePos[0], noticePos[1], noticeWidth, noticeHeight)
                .build();

            if (!hasDlcButton(screen)) {
                Screens.getButtons(screen).add(button);
            }
            if (DlcConfig.isUButtomShown() && DlcConfig.isUpdateManagerOn()
                && findButtonByKey(screen, "dlc_manager.update.import_icon") == null) {
                Screens.getButtons(screen).add(updateButton);
            }
            if (DlcConfig.isPButtomShown() && DlcConfig.isPackFuncOn()
                && findButtonByKey(screen, "dlc_manager.pack.quick_icon") == null) {
                Screens.getButtons(screen).add(packButton);
            }
            if (DlcClientRuntime.shouldShowMainMenuNoticeButton()
                && findButtonByKey(screen, "dlc_manager.notice") == null) {
                Screens.getButtons(screen).add(noticeButton);
            }
            if (DlcClientRuntime.shouldShowMainMenuNoticePanel()) {
                ScreenEvents.afterRender(screen).register((currentScreen, guiGraphics, mouseX, mouseY, delta) ->
                    DlcClientRuntime.renderTitleNoticePanel(currentScreen, guiGraphics, mouseX, mouseY));
                ScreenMouseEvents.allowMouseClick(screen).register((currentScreen, mouseX, mouseY, mouseButton) ->
                    !DlcClientRuntime.handleTitleNoticePanelClick(currentScreen, mouseX, mouseY, mouseButton));
                ScreenMouseEvents.allowMouseScroll(screen).register((currentScreen, mouseX, mouseY, horizontalAmount, verticalAmount) ->
                    !DlcClientRuntime.handleTitleNoticePanelScroll(currentScreen, mouseX, mouseY, horizontalAmount, verticalAmount));
                ScreenMouseEvents.allowMouseRelease(screen).register((currentScreen, mouseX, mouseY, mouseButton) ->
                    !DlcClientRuntime.handleTitleNoticePanelRelease(currentScreen, mouseX, mouseY, mouseButton));
            }
            alignTitleButtons(screen);
            pendingAlignScreen = screen;
            DlcClientRuntime.maybeOpenManagerOnFirstRun(screen);
            runStartupChecksAsync(screen);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingAlignScreen != null) {
                if (client.screen == pendingAlignScreen) {
                    alignTitleButtons(pendingAlignScreen);
                }
                pendingAlignScreen = null;
            }
            if (resourcePackSynced) {
                return;
            }
            if (client.player == null) {
                return;
            }
            resourcePackSynced = true;
            client.execute(DlcManagerClient::syncEnabledResourcePacks);
        });
    }

    private static void runStartupChecksAsync(Screen screen) {
        DlcClientRuntime.runStartupChecksAsync(screen);
    }

    private static boolean hasDlcButton(Screen screen) {
        for (AbstractWidget widget : Screens.getButtons(screen)) {
            if (widget.getMessage().getContents() instanceof TranslatableContents contents) {
                if ("dlc_manager.title".equals(contents.getKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AbstractWidget findRealmsButton(Screen screen) {
        return findRealmsButton(screen.children());
    }

    private static AbstractWidget findRealmsButton(java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> listeners) {
        for (var child : listeners) {
            if (child instanceof AbstractWidget widget && isRealmsButton(widget)) {
                return widget;
            }
        }
        return null;
    }

    private static boolean isRealmsButton(AbstractWidget widget) {
        if (widget.getMessage().getContents() instanceof TranslatableContents contents) {
            String key = contents.getKey();
            return "menu.online".equals(key) || "menu.realms".equals(key);
        }
        return false;
    }

    private static AbstractWidget findButtonByKey(Screen screen, String key) {
        return findButtonByKey(screen.children(), key);
    }

    private static AbstractWidget findButtonByKey(
        java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> listeners,
        String key
    ) {
        for (var child : listeners) {
            if (!(child instanceof AbstractWidget widget)) {
                continue;
            }
            if (widget.getMessage().getContents() instanceof TranslatableContents contents) {
                if (key.equals(contents.getKey())) {
                    return widget;
                }
            }
        }
        return null;
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

    private static AbstractWidget findModsButton(Screen screen) {
        AbstractWidget widget = findButtonByKey(screen, "modmenu.title");
        if (widget != null) {
            return widget;
        }
        widget = findButtonByKey(screen, "menu.mods");
        if (widget != null) {
            return widget;
        }
        return findButtonByKey(screen, "fml.menu.mods");
    }


    private static void openServerManagerFromIngame(Screen parent) {
        DlcClientRuntime.openServerManagerFromIngame(parent);
    }

    private static void moveBottomRow(Screen screen, int targetY) {
        AbstractWidget optionsWidget = findButtonByKey(screen, "menu.options");
        if (optionsWidget != null) {
            int currentY = optionsWidget.getY();
            for (var child : screen.children()) {
                if (child instanceof AbstractWidget widget) {
                    if (Math.abs(widget.getY() - currentY) <= 1) {
                        widget.setY(targetY);
                    }
                }
            }
            return;
        }
        for (String key : new String[] {"menu.options", "menu.quit", "options.language", "options.accessibility"}) {
            AbstractWidget widget = findButtonByKey(screen, key);
            if (widget != null) {
                widget.setY(targetY);
            }
        }
    }

    private static void alignTitleButtons(Screen screen) {
        AbstractWidget realmsWidget = findRealmsButton(screen);
        AbstractWidget multiplayerWidget = findButtonByKey(screen, "menu.multiplayer");
        AbstractWidget modsWidget = findModsButton(screen);
        AbstractWidget dlcWidget = findButtonByKey(screen, "dlc_manager.title");
        AbstractWidget updateWidget = findButtonByKey(screen, "dlc_manager.update.import_icon");
        AbstractWidget packWidget = findButtonByKey(screen, "dlc_manager.pack.quick_icon");
        if (realmsWidget != null) {
            realmsWidget.visible = false;
            realmsWidget.active = false;
        }
        if (dlcWidget == null) {
            return;
        }
        int rowStep = 24;
        int rowGap = 4;
        int rowX = screen.width / 2 - 100;
        int rowY = screen.height / 4 + 48;
        int rowW = 200;
        int rowH = 20;
        AbstractWidget anchor = multiplayerWidget != null ? multiplayerWidget : realmsWidget;
        if (anchor != null) {
            rowGap = 2;
            rowStep = anchor.getHeight() + 4;
            rowX = anchor.getX();
            rowW = anchor.getWidth();
            rowH = anchor.getHeight();
            rowY = anchor.getY() + anchor.getHeight() + rowGap;
            if (anchor == realmsWidget && multiplayerWidget == null) {
                rowY = anchor.getY();
            }
        }
        int[] pos = DlcConfig.computeTitleButtonPosition(screen.width, screen.height, rowW, rowH, rowX, rowY);
        rowX = pos[0];
        rowY = pos[1];

        if (modsWidget != null) {
            int gap = 4;
            int halfWidth = Math.max(40, (rowW - gap) / 2);
            int iconGap = 4;
            int iconWidth = 20;
            modsWidget.setX(rowX);
            modsWidget.setY(rowY);
            modsWidget.setWidth(halfWidth);
            //? if >=1.21.1 {
            modsWidget.setHeight(rowH);
            //?}
            int dlcX = rowX + halfWidth + gap;
            int dlcWidth = halfWidth;
            int defaultUpdateX = dlcX + dlcWidth + iconGap;
            if (DlcConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                defaultUpdateX = dlcX - iconGap - iconWidth;
            }
            int[] updatePos = DlcConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconWidth, rowH, defaultUpdateX, rowY);
            if (updateWidget != null) {
                updateWidget.setWidth(iconWidth);
                //? if >=1.21.1 {
                updateWidget.setHeight(rowH);
                //?}
                updateWidget.setX(updatePos[0]);
                updateWidget.setY(updatePos[1]);
            }
            if (packWidget != null) {
                packWidget.setWidth(iconWidth);
                //? if >=1.21.1 {
                packWidget.setHeight(rowH);
                //?}
                packWidget.setX(updatePos[0] + iconWidth + iconGap);
                packWidget.setY(updatePos[1]);
            }
            dlcWidget.setX(dlcX);
            dlcWidget.setY(rowY);
            dlcWidget.setWidth(dlcWidth);
            //? if >=1.21.1 {
            dlcWidget.setHeight(rowH);
            //?}

            int optionsRowY = rowY + rowStep;
            moveBottomRow(screen, optionsRowY);
        } else {
            int iconGap = 4;
            int iconWidth = 20;
            int dlcWidth = rowW;
            int defaultUpdateX = rowX + dlcWidth + iconGap;
            if (DlcConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                defaultUpdateX = rowX - iconGap - iconWidth;
            }
            int[] updatePos = DlcConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconWidth, rowH, defaultUpdateX, rowY);
            if (updateWidget != null) {
                updateWidget.setWidth(iconWidth);
                //? if >=1.21.1 {
                updateWidget.setHeight(rowH);
                //?}
                updateWidget.setX(updatePos[0]);
                updateWidget.setY(updatePos[1]);
            }
            if (packWidget != null) {
                packWidget.setWidth(iconWidth);
                //? if >=1.21.1 {
                packWidget.setHeight(rowH);
                //?}
                packWidget.setX(updatePos[0] + iconWidth + iconGap);
                packWidget.setY(updatePos[1]);
            }
            dlcWidget.setX(rowX);
            dlcWidget.setY(rowY);
            dlcWidget.setWidth(dlcWidth);
            //? if >=1.21.1 {
            dlcWidget.setHeight(rowH);
            //?}
        }
    }

    private static void syncEnabledResourcePacks() {
        DlcClientRuntime.syncEnabledResourcePacks();
    }
}
//?} else if forge {
package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.client.DlcClientRuntime;
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen;
import com.cozooo.dlc_manager.client.screen.DlcConfigScreen;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.core.DlcConfig;
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
            int[] pos = DlcConfig.computeTitleButtonPosition(screen.width, screen.height, w, h, x, y);
            x = pos[0];
            y = pos[1];
            button.setX(x);
            button.setY(y);
            event.addListener(button);
            int iconSize = Math.min(20, h);
            int iconX = button.getX() + button.getWidth() + 4;
            if (DlcConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                iconX = button.getX() - iconSize - 4;
            }
            int iconY = button.getY();
            int[] updatePos = DlcConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconSize, h, iconX, iconY);
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
            int[] noticePos = DlcConfig.computeNoticeButtonPosition(
                screen.width, screen.height, noticeWidth, noticeHeight, noticeDefaultX, noticeDefaultY
            );
            Button noticeButton = Button.builder(Component.translatable("dlc_manager.notice"),
                    btn -> DlcClientRuntime.openNoticeFromTitle(screen))
                .bounds(noticePos[0], noticePos[1], noticeWidth, noticeHeight)
                .build();
            if (DlcConfig.isUButtomShown() && DlcConfig.isUpdateManagerOn()) {
                event.addListener(updateButton);
            }
            if (DlcConfig.isPButtomShown() && DlcConfig.isPackFuncOn()) {
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
//?} else if neoforge {
package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.client.DlcClientRuntime;
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen;
import com.cozooo.dlc_manager.client.screen.DlcConfigScreen;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.core.DlcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public class DlcManagerClient {
    public static void init(IEventBus modBus) {
        modBus.addListener(DlcManagerClient::onClientSetup);
        NeoForge.EVENT_BUS.register(new ClientEvents());
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        DlcUpdateService.ensureUpdateDir();
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
            () -> (modContainer, parent) -> new DlcConfigScreen(parent));
    }

    public static class ClientEvents {
        private boolean resourcePackSynced = false;

        @SubscribeEvent
        public void onTitleScreenInit(ScreenEvent.Init.Post event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                return;
            }

            int x = screen.width / 2 - 100;
            int y = screen.height / 4 + 32 + 24 * 2;
            int w = 200;
            int h = 20;
            Button button = Button.builder(Component.translatable("dlc_manager.title"), btn -> {
                Screen managerScreen = new DlcMaLiLibScreen(screen);
                if (DlcRemoteManager.tryShowFromManager(managerScreen)) {
                    return;
                }
                Minecraft.getInstance().setScreen(managerScreen);
            }).bounds(x, y, w, h).build();
            AbstractWidget realmsWidget = findRealmsButton(screen);
            if (realmsWidget != null) {
                realmsWidget.visible = false;
                realmsWidget.active = false;
            }

            boolean anchored = false;
            AbstractWidget modsWidget = findModsButton(screen);
            if (modsWidget != null) {
                if (realmsWidget != null) {
                    int rowStep = modsWidget.getHeight() + 4;
                    modsWidget.setY(modsWidget.getY() - rowStep);
                    shiftButtonRow(screen, rowStep, "menu.options", "menu.quit", "options.language", "options.accessibility");
                }
                int gap = 4;
                int fullWidth = modsWidget.getWidth();
                int halfWidth = Math.max(40, (fullWidth - gap) / 2);
                modsWidget.setWidth(halfWidth);
                x = modsWidget.getX() + halfWidth + gap;
                y = modsWidget.getY();
                w = halfWidth;
                h = modsWidget.getHeight();
                button.setX(x);
                button.setY(y);
                button.setWidth(w);
                button.setHeight(h);
                anchored = true;
            }

            if (!anchored) {
                if (realmsWidget != null) {
                    x = realmsWidget.getX();
                    y = realmsWidget.getY();
                    w = realmsWidget.getWidth();
                    h = realmsWidget.getHeight();
                    button.setX(x);
                    button.setY(y);
                    button.setWidth(w);
                    button.setHeight(h);
                } else {
                    AbstractWidget multiplayerWidget = findButtonByKey(screen, "menu.multiplayer");
                    if (multiplayerWidget != null) {
                        int gap = 4;
                        x = multiplayerWidget.getX();
                        y = multiplayerWidget.getY() + multiplayerWidget.getHeight() + gap;
                        w = multiplayerWidget.getWidth();
                        h = multiplayerWidget.getHeight();
                        button.setX(x);
                        button.setY(y);
                        button.setWidth(w);
                        button.setHeight(h);
                    }
                }
            }
            button.setWidth(w);
            button.setHeight(h);
            int[] pos = DlcConfig.computeTitleButtonPosition(screen.width, screen.height, w, h, x, y);
            x = pos[0];
            y = pos[1];
            button.setX(x);
            button.setY(y);
            event.addListener(button);
            int iconSize = Math.min(20, h);
            int iconX = button.getX() + button.getWidth() + 4;
            if (DlcConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                iconX = button.getX() - iconSize - 4;
            }
            int iconY = button.getY();
            int[] updatePos = DlcConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconSize, h, iconX, iconY);
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
            int[] noticePos = DlcConfig.computeNoticeButtonPosition(
                screen.width, screen.height, noticeWidth, noticeHeight, noticeDefaultX, noticeDefaultY
            );
            Button noticeButton = Button.builder(Component.translatable("dlc_manager.notice"),
                    btn -> DlcClientRuntime.openNoticeFromTitle(screen))
                .bounds(noticePos[0], noticePos[1], noticeWidth, noticeHeight)
                .build();
            if (DlcConfig.isUButtomShown() && DlcConfig.isUpdateManagerOn()) {
                event.addListener(updateButton);
            }
            if (DlcConfig.isPButtomShown() && DlcConfig.isPackFuncOn()) {
                event.addListener(packButton);
            }
            if (DlcClientRuntime.shouldShowMainMenuNoticeButton()
                && findButtonByKey(screen, "dlc_manager.notice") == null) {
                event.addListener(noticeButton);
            }
            DlcClientRuntime.maybeOpenManagerOnFirstRun(screen);
            runStartupChecksAsync(screen);
        }

        @SubscribeEvent
        public void onTitleScreenRender(ScreenEvent.Render.Post event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                return;
            }
            DlcClientRuntime.renderTitleNoticePanel(screen, event.getGuiGraphics());
        }

        @SubscribeEvent
        public void onTitleScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
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
        public void onTitleScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                return;
            }
            if (DlcClientRuntime.handleTitleNoticePanelRelease(screen, event.getMouseX(), event.getMouseY(), event.getButton())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onTitleScreenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
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
        public void onTitleScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                return;
            }
            if (DlcClientRuntime.handleTitleNoticePanelScroll(
                screen, event.getMouseX(), event.getMouseY(), event.getScrollDeltaX(), event.getScrollDeltaY()
            )) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("dlcmanager")
                .executes(ctx -> {
                    openManagerFromCommand();
                    return 1;
                })
                .then(Commands.literal("selfupdate").executes(ctx -> {
                    openSelfUpdateFromCommand();
                    return 1;
                }))
                .then(Commands.literal("updatemanager").executes(ctx -> {
                    openUpdateManagerFromCommand();
                    return 1;
                }))
                .then(Commands.literal("update").executes(ctx -> {
                    openUpdatePromptFromCommand();
                    return 1;
                }))
                .then(Commands.literal("server").executes(ctx -> {
                    openServerManagerFromCommand();
                    return 1;
                }))
            );
        }

        @SubscribeEvent
        public void onIngameMenuInit(ScreenEvent.Init.Post event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof PauseScreen)) {
                return;
            }
            if (!canOpenServerManager()) {
                return;
            }
            if (findButtonByKey(screen, "dlc_manager.server.manage") != null) {
                return;
            }
            AbstractWidget anchor = findButtonByKey(screen, "menu.sendFeedback");
            if (anchor == null) {
                anchor = findButtonByKey(screen, "menu.feedback");
            }
            if (anchor != null) {
                anchor.visible = false;
                anchor.active = false;
            }
            if (anchor == null) {
                anchor = findButtonByKey(screen, "menu.mods");
            }
            if (anchor == null) {
                anchor = findButtonByKey(screen, "menu.options");
            }
            int x = screen.width / 2 - 100;
            int y = screen.height / 4 + 120;
            int w = 200;
            int h = 20;
            if (anchor != null) {
                if (findButtonByKey(screen, "menu.sendFeedback") != null || findButtonByKey(screen, "menu.feedback") != null) {
                    x = anchor.getX();
                    y = anchor.getY();
                    w = anchor.getWidth();
                    h = anchor.getHeight();
                } else if (anchor == findButtonByKey(screen, "menu.mods")) {
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
                    btn -> openServerManagerFromIngame(screen))
                .bounds(x, y, w, h)
                .build();
            event.addListener(button);
        }

        @SubscribeEvent
        public void onClientTick(ClientTickEvent.Post event) {
            if (resourcePackSynced) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }
            resourcePackSynced = true;
            minecraft.execute(ClientEvents::syncEnabledResourcePacks);
        }

        private static void syncEnabledResourcePacks() {
            DlcClientRuntime.syncEnabledResourcePacks();
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
    }

    private static AbstractWidget findRealmsButton(Screen screen) {
        return findRealmsButton(screen.children());
    }

    private static AbstractWidget findRealmsButton(java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> listeners) {
        for (var child : listeners) {
            if (child instanceof AbstractWidget widget && isRealmsButton(widget)) {
                return widget;
            }
        }
        return null;
    }

    private static boolean isRealmsButton(AbstractWidget widget) {
        if (widget.getMessage().getContents() instanceof TranslatableContents contents) {
            String key = contents.getKey();
            return "menu.online".equals(key) || "menu.realms".equals(key);
        }
        return false;
    }

    private static AbstractWidget findButtonByKey(Screen screen, String key) {
        return findButtonByKey(screen.children(), key);
    }

    private static AbstractWidget findButtonByKey(
        java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> listeners,
        String key
    ) {
        for (var child : listeners) {
            if (!(child instanceof AbstractWidget widget)) {
                continue;
            }
            if (widget.getMessage().getContents() instanceof TranslatableContents contents) {
                if (key.equals(contents.getKey())) {
                    return widget;
                }
            }
        }
        return null;
    }

    private static AbstractWidget findModsButton(Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof net.neoforged.neoforge.client.gui.widget.ModsButton widget) {
                return widget;
            }
        }
        return findButtonByKey(screen, "fml.menu.mods");
    }
    private static void openServerManagerFromIngame(Screen parent) {
        DlcClientRuntime.openServerManagerFromIngame(parent);
    }

    private static void shiftButtonRow(Screen screen, int deltaY, String... keys) {
        for (String key : keys) {
            AbstractWidget widget = findButtonByKey(screen, key);
            if (widget != null) {
                widget.setY(widget.getY() - deltaY);
            }
        }
    }

    private static void runStartupChecksAsync(Screen screen) {
        DlcClientRuntime.runStartupChecksAsync(screen);
    }
}
//?}
