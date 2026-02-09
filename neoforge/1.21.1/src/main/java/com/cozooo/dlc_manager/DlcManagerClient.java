package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.client.DlcClientRuntime;
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen;
import com.cozooo.dlc_manager.client.screen.DlcPathConfigScreen;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

public class DlcManagerClient {
    private static volatile boolean startupCheckRunning = false;
    private static volatile boolean selfUpdatePrompted = false;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static void init(IEventBus modBus) {
        modBus.addListener(DlcManagerClient::onClientSetup);
        NeoForge.EVENT_BUS.register(new ClientEvents());
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        DlcUpdateService.ensureUpdateDir();
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
            () -> (modContainer, parent) -> new DlcPathConfigScreen(parent));
    }

    public static class ClientEvents {
        private boolean resourcePackSynced = false;
        private volatile boolean startupCheckRunning = false;

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
            int desiredW = DlcPathConfig.getTitleWidth();
            int desiredH = DlcPathConfig.getTitleHeight();
            if (desiredW > 0) {
                w = desiredW;
            }
            if (desiredH > 0) {
                h = desiredH;
            }
            button.setWidth(w);
            button.setHeight(h);
            int[] pos = DlcPathConfig.computeTitleButtonPosition(screen.width, screen.height, w, h, x, y);
            x = pos[0];
            y = pos[1];
            button.setX(x);
            button.setY(y);
            event.addListener(button);
            int iconSize = Math.min(20, h);
            int iconX = button.getX() + button.getWidth() + 4;
            int iconY = button.getY();
            Button updateButton = Button.builder(Component.translatable("dlc_manager.update.import_icon"), btn -> DlcUpdateClient.openUpdateManager(screen))
                .bounds(iconX, iconY, iconSize, h)
                .build();
            if (DlcPathConfig.isUButtomShown() && DlcPathConfig.isUpdateManagerOn()) {
                event.addListener(updateButton);
            }
            runStartupChecksAsync(screen);
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
