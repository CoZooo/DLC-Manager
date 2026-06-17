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
            modsWidget.setHeight(rowH);
            int dlcX = rowX + halfWidth + gap;
            int dlcWidth = halfWidth;
            int defaultUpdateX = dlcX + dlcWidth + iconGap;
            if (DlcConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                defaultUpdateX = dlcX - iconGap - iconWidth;
            }
            int[] updatePos = DlcConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconWidth, rowH, defaultUpdateX, rowY);
            if (updateWidget != null) {
                updateWidget.setWidth(iconWidth);
                updateWidget.setHeight(rowH);
                updateWidget.setX(updatePos[0]);
                updateWidget.setY(updatePos[1]);
            }
            if (packWidget != null) {
                packWidget.setWidth(iconWidth);
                packWidget.setHeight(rowH);
                packWidget.setX(updatePos[0] + iconWidth + iconGap);
                packWidget.setY(updatePos[1]);
            }
            dlcWidget.setX(dlcX);
            dlcWidget.setY(rowY);
            dlcWidget.setWidth(dlcWidth);
            dlcWidget.setHeight(rowH);

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
                updateWidget.setHeight(rowH);
                updateWidget.setX(updatePos[0]);
                updateWidget.setY(updatePos[1]);
            }
            if (packWidget != null) {
                packWidget.setWidth(iconWidth);
                packWidget.setHeight(rowH);
                packWidget.setX(updatePos[0] + iconWidth + iconGap);
                packWidget.setY(updatePos[1]);
            }
            dlcWidget.setX(rowX);
            dlcWidget.setY(rowY);
            dlcWidget.setWidth(dlcWidth);
            dlcWidget.setHeight(rowH);
        }
    }

    private static void syncEnabledResourcePacks() {
        DlcClientRuntime.syncEnabledResourcePacks();
    }
}
