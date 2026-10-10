package com.cozooo.dlc_manager.client;

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.screen.DlcManagerScreen;
import com.cozooo.dlc_manager.client.screen.DlcApricityScreen;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.core.DlcConfig;
import dev.architectury.event.EventResult;
import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientScreenInputEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class DlcClientEvents {
    private static boolean registered = false;
    private static boolean resourcePackSynced = false;
    private static Screen pendingAlignScreen;

    private DlcClientEvents() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        registerCommands();
        registerGuiEvents();
        registerInputEvents();
        registerTickEvents();
    }

    private static void registerCommands() {
        ClientCommandRegistrationEvent.EVENT.register(
            //? if >=1.19.2 {
            (dispatcher, context) ->
            //?}
            //? if <1.19.2 {
            /*dispatcher ->
            *///?}
            dispatcher.register(
            ClientCommandRegistrationEvent.literal("dlcmanager")
                .executes(ctx -> {
                    DlcClientRuntime.openManagerFromCommand();
                    return 1;
                })
                .then(ClientCommandRegistrationEvent.literal("selfupdate").executes(ctx -> {
                    DlcClientRuntime.openSelfUpdateFromCommand();
                    return 1;
                }))
                .then(ClientCommandRegistrationEvent.literal("updatemanager").executes(ctx -> {
                    DlcClientRuntime.openUpdateManagerFromCommand();
                    return 1;
                }))
                .then(ClientCommandRegistrationEvent.literal("update").executes(ctx -> {
                    DlcClientRuntime.openUpdatePromptFromCommand();
                    return 1;
                }))
                .then(ClientCommandRegistrationEvent.literal("server").executes(ctx -> {
                    DlcClientRuntime.openServerManagerFromCommand();
                    return 1;
                }))
        ));
    }

    private static void registerGuiEvents() {
        ClientGuiEvent.SET_SCREEN.register(screen -> {
            DlcApricityScreen.beforeScreenChange(screen);
            return CompoundEventResult.pass();
        });
        ClientGuiEvent.INIT_POST.register((screen, access) -> {
            if (screen instanceof PauseScreen) {
                addPauseScreenButton(screen, access);
                return;
            }
            if (screen instanceof TitleScreen) {
                addTitleScreenButtons(screen, access);
            }
        });
        ClientGuiEvent.RENDER_POST.register((screen, guiGraphics, mouseX, mouseY, delta) -> {
            if (screen instanceof DlcApricityScreen dlc) dlc.finishUiFrame(guiGraphics);
            if (screen instanceof TitleScreen) {
                DlcClientRuntime.renderTitleNoticePanel(screen, guiGraphics, mouseX, mouseY);
            }
        });
    }

    private static void registerInputEvents() {
        //? if >=26.1 {
        /*return;
        *///?}
        //? if <26.1 {
        ClientScreenInputEvent.MOUSE_CLICKED_PRE.register((client, screen, mouseX, mouseY, button) -> {
            if (screen instanceof TitleScreen && DlcClientRuntime.handleTitleNoticePanelClick(screen, mouseX, mouseY, button)) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
        ClientScreenInputEvent.MOUSE_RELEASED_PRE.register((client, screen, mouseX, mouseY, button) -> {
            if (screen instanceof TitleScreen && DlcClientRuntime.handleTitleNoticePanelRelease(screen, mouseX, mouseY, button)) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
        ClientScreenInputEvent.MOUSE_DRAGGED_PRE.register((client, screen, mouseX, mouseY, button, dragX, dragY) -> {
            if (screen instanceof TitleScreen && DlcClientRuntime.handleTitleNoticePanelDrag(screen, mouseX, mouseY, button, dragX, dragY)) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
        //? if >=1.21.1 {
        /*ClientScreenInputEvent.MOUSE_SCROLLED_PRE.register((client, screen, mouseX, mouseY, amountX, amountY) -> {
            if (screen instanceof TitleScreen && DlcClientRuntime.handleTitleNoticePanelScroll(screen, mouseX, mouseY, amountX, amountY)) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
        *///?} else {
        ClientScreenInputEvent.MOUSE_SCROLLED_PRE.register((client, screen, mouseX, mouseY, amount) -> {
            if (screen instanceof TitleScreen && DlcClientRuntime.handleTitleNoticePanelScroll(screen, mouseX, mouseY, 0.0D, amount)) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
        //?}
        //?}
    }

    private static void registerTickEvents() {
        ClientTickEvent.CLIENT_POST.register(client -> {
            DlcApricityScreen.cancelAbandonedNavigation(client.screen);
            if (pendingAlignScreen != null) {
                if (client.screen == pendingAlignScreen) {
                    alignTitleButtons(pendingAlignScreen);
                }
                pendingAlignScreen = null;
            }
            if (resourcePackSynced || client.player == null) {
                return;
            }
            resourcePackSynced = true;
            client.execute(DlcClientRuntime::syncEnabledResourcePacks);
        });
    }

    private static void addTitleScreenButtons(Screen screen, ScreenAccess access) {
        int x = screen.width / 2 - 100;
        int y = screen.height / 4 + 32 + 24 * 2;
        int w = 200;
        int h = 20;
        Button button = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.title"), btn -> {
            Screen managerScreen = new DlcManagerScreen(screen);
            if (DlcRemoteManager.tryShowFromManager(managerScreen)) {
                return;
            }
            Minecraft.getInstance().setScreen(managerScreen);
        }).bounds(x, y, w, h).build();
        Button updateButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.update.import_icon"),
                btn -> DlcUpdateClient.openUpdateManager(screen))
            .bounds(x + w + 4, y, 20, h)
            .build();
        Button packButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.pack.quick_icon"),
                btn -> DlcClientRuntime.openPackManagerFromTitle(screen))
            .bounds(x + w + 28, y, 20, h)
            .build();
        int noticeWidth = Math.max(48, Minecraft.getInstance().font.width(DlcTextCompat.translatable("dlc_manager.notice")) + 8);
        int noticeHeight = 20;
        int noticeDefaultX = screen.width - noticeWidth - 8;
        int noticeDefaultY = screen.height - noticeHeight - 8;
        int[] noticePos = DlcConfig.computeNoticeButtonPosition(
            screen.width, screen.height, noticeWidth, noticeHeight, noticeDefaultX, noticeDefaultY
        );
        Button noticeButton = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.notice"),
                btn -> DlcClientRuntime.openNoticeFromTitle(screen))
            .bounds(noticePos[0], noticePos[1], noticeWidth, noticeHeight)
            .build();

        if (findButtonByKey(screen, "dlc_manager.title") == null) {
            access.addRenderableWidget(button);
        }
        if (DlcConfig.isUButtomShown() && DlcConfig.isUpdateManagerOn()
            && findButtonByKey(screen, "dlc_manager.update.import_icon") == null) {
            access.addRenderableWidget(updateButton);
        }
        if (DlcConfig.isPButtomShown() && DlcConfig.isPackFuncOn()
            && findButtonByKey(screen, "dlc_manager.pack.quick_icon") == null) {
            access.addRenderableWidget(packButton);
        }
        if (DlcClientRuntime.shouldShowMainMenuNoticeButton()
            && findButtonByKey(screen, "dlc_manager.notice") == null) {
            access.addRenderableWidget(noticeButton);
        }
        alignTitleButtons(screen);
        pendingAlignScreen = screen;
        if (DlcRequiredManager.tryShowFromTitle(screen)) return;
        DlcClientRuntime.maybeOpenManagerOnFirstRun(screen);
        DlcClientRuntime.runStartupChecksAsync(screen);
    }

    private static void addPauseScreenButton(Screen screen, ScreenAccess access) {
        if (!DlcClientRuntime.canOpenServerManager() || findButtonByKey(screen, "dlc_manager.server.manage") != null) {
            return;
        }
        AbstractWidget anchor = findButtonByKey(screen, "menu.sendFeedback");
        if (anchor == null) {
            anchor = findButtonByKey(screen, "menu.feedback");
        }
        boolean feedbackAnchor = anchor != null;
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
            if (feedbackAnchor) {
                x = DlcScreenCompat.getX(anchor);
                y = DlcScreenCompat.getY(anchor);
                w = anchor.getWidth();
                h = anchor.getHeight();
            } else if (anchor == findButtonByKey(screen, "menu.mods")) {
                int gap = 4;
                int originalWidth = anchor.getWidth();
                int halfWidth = Math.max(20, (originalWidth - gap) / 2);
                anchor.setWidth(halfWidth);
                x = DlcScreenCompat.getX(anchor) + halfWidth + gap;
                y = DlcScreenCompat.getY(anchor);
                w = Math.max(20, originalWidth - halfWidth - gap);
                h = anchor.getHeight();
            } else {
                x = DlcScreenCompat.getX(anchor);
                y = DlcScreenCompat.getY(anchor) + anchor.getHeight() + 4;
                w = anchor.getWidth();
                h = anchor.getHeight();
            }
        }
        Button button = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.server.manage"),
                btn -> DlcClientRuntime.openServerManagerFromIngame(screen))
            .bounds(x, y, w, h)
            .build();
        access.addRenderableWidget(button);
    }

    private static AbstractWidget findRealmsButton(Screen screen) {
        return findRealmsButton(screen.children());
    }

    private static AbstractWidget findRealmsButton(List<? extends GuiEventListener> listeners) {
        for (GuiEventListener child : listeners) {
            if (child instanceof AbstractWidget widget && isRealmsButton(widget)) {
                return widget;
            }
        }
        return null;
    }

    private static boolean isRealmsButton(AbstractWidget widget) {
        String key = DlcScreenCompat.translationKey(widget.getMessage());
        return "menu.online".equals(key) || "menu.realms".equals(key);
    }

    private static AbstractWidget findButtonByKey(Screen screen, String key) {
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget)) {
                continue;
            }
            if (key.equals(DlcScreenCompat.translationKey(widget.getMessage()))) {
                return widget;
            }
        }
        return null;
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

    private static void moveBottomRow(Screen screen, int targetY) {
        AbstractWidget optionsWidget = findButtonByKey(screen, "menu.options");
        if (optionsWidget != null) {
            int currentY = DlcScreenCompat.getY(optionsWidget);
            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget && Math.abs(DlcScreenCompat.getY(widget) - currentY) <= 1) {
                    DlcScreenCompat.setY(widget, targetY);
                }
            }
            return;
        }
        for (String key : new String[] {"menu.options", "menu.quit", "options.language", "options.accessibility"}) {
            AbstractWidget widget = findButtonByKey(screen, key);
            if (widget != null) {
                DlcScreenCompat.setY(widget, targetY);
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
            rowX = DlcScreenCompat.getX(anchor);
            rowW = anchor.getWidth();
            rowH = anchor.getHeight();
            rowY = DlcScreenCompat.getY(anchor) + anchor.getHeight() + rowGap;
            if (anchor == realmsWidget && multiplayerWidget == null) {
                rowY = DlcScreenCompat.getY(anchor);
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
            DlcScreenCompat.setX(modsWidget, rowX);
            DlcScreenCompat.setY(modsWidget, rowY);
            modsWidget.setWidth(halfWidth);
            //? if >=1.21.1 {
            /*modsWidget.setHeight(rowH);
            *///?}
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
                /*updateWidget.setHeight(rowH);
                *///?}
                DlcScreenCompat.setX(updateWidget, updatePos[0]);
                DlcScreenCompat.setY(updateWidget, updatePos[1]);
            }
            if (packWidget != null) {
                packWidget.setWidth(iconWidth);
                //? if >=1.21.1 {
                /*packWidget.setHeight(rowH);
                *///?}
                DlcScreenCompat.setX(packWidget, updatePos[0] + iconWidth + iconGap);
                DlcScreenCompat.setY(packWidget, updatePos[1]);
            }
            DlcScreenCompat.setX(dlcWidget, dlcX);
            DlcScreenCompat.setY(dlcWidget, rowY);
            dlcWidget.setWidth(dlcWidth);
            //? if >=1.21.1 {
            /*dlcWidget.setHeight(rowH);
            *///?}
            moveBottomRow(screen, rowY + rowStep);
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
                /*updateWidget.setHeight(rowH);
                *///?}
                DlcScreenCompat.setX(updateWidget, updatePos[0]);
                DlcScreenCompat.setY(updateWidget, updatePos[1]);
            }
            if (packWidget != null) {
                packWidget.setWidth(iconWidth);
                //? if >=1.21.1 {
                /*packWidget.setHeight(rowH);
                *///?}
                DlcScreenCompat.setX(packWidget, updatePos[0] + iconWidth + iconGap);
                DlcScreenCompat.setY(packWidget, updatePos[1]);
            }
            DlcScreenCompat.setX(dlcWidget, rowX);
            DlcScreenCompat.setY(dlcWidget, rowY);
            dlcWidget.setWidth(dlcWidth);
            //? if >=1.21.1 {
            /*dlcWidget.setHeight(rowH);
            *///?}
        }
    }
}
