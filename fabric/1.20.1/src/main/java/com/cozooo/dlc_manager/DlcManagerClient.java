package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.DlcClientRuntime;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.mojang.logging.LogUtils;
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
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;
import java.nio.file.Path;

public class DlcManagerClient implements ClientModInitializer {
    private static boolean resourcePackSynced = false;
    private static Screen pendingAlignScreen;
    private static volatile boolean startupCheckRunning = false;
    private static volatile boolean selfUpdatePrompted = false;
    private static final Logger LOGGER = LogUtils.getLogger();

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
            Button updateButton = Button.builder(Component.translatable("dlc_manager.update.import_icon"),
                btn -> DlcUpdateClient.openUpdateManager(screen))
                .bounds(x + w + 4, y, 20, h)
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

            if (!hasDlcButton(screen)) {
                Screens.getButtons(screen).add(button);
            }
            if (DlcPathConfig.isUButtomShown() && DlcPathConfig.isUpdateManagerOn()
                && findButtonByKey(screen, "dlc_manager.update.import_icon") == null) {
                Screens.getButtons(screen).add(updateButton);
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

    private static boolean isRealmsButton(AbstractWidget widget) {
        if (widget.getMessage().getContents() instanceof TranslatableContents contents) {
            return "menu.online".equals(contents.getKey());
        }
        return false;
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

    private static DlcServerEntry createEntryFromServer(ServerData data) {
            DlcServerEntry entry = new DlcServerEntry();
            entry.name = data.name;
            String ip = data.ip == null ? "" : data.ip;
            String host = ip;
            int port = DlcPathConfig.getServerPort();
            int index = ip.lastIndexOf(':');
            if (index > 0 && index < ip.length() - 1) {
                if (ip.startsWith("[") && ip.contains("]")) {
                    int end = ip.indexOf(']');
                    if (end > 1) {
                        host = ip.substring(1, end);
                    }
                } else {
                    host = ip.substring(0, index);
                }
            }
            entry.host = host;
            entry.port = port;
            entry.useTcp = true;
            return entry;
        }
    private static void openServerManagerFromIngame(Screen parent) {
        DlcClientRuntime.openServerManagerFromIngame(parent);
    }

    private static DlcServerInfo fetchServerInfo(DlcServerEntry entry) {
        try {
            return DlcServerConnection.fetchServerInfo(entry, 5000,
                CrossPlatformRuntime.playerName(), CrossPlatformRuntime.playerUuid());
        } catch (Exception ignored) {
        }
        return null;
    }

    private static DlcServerEntry findEntryByKey(String key) {
        if (key == null) {
            return null;
        }
        for (DlcServerEntry entry : DlcServerList.getEntries()) {
            if (entry != null && key.equals(entry.getKey())) {
                return entry;
            }
        }
        return null;
    }

    private static void saveEntryToList(DlcServerEntry entry) {
        DlcServerList.load();
        java.util.List<DlcServerEntry> list = new java.util.ArrayList<>(DlcServerList.getEntries());
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            DlcServerEntry existing = list.get(i);
            if (existing != null && entry.getKey().equals(existing.getKey())) {
                existing.password = entry.password;
                if (entry.name != null && !entry.name.isBlank()) {
                    existing.name = entry.name;
                }
                found = true;
                break;
            }
        }
        if (!found) {
            list.add(entry);
        }
        DlcServerList.setEntries(list);
        DlcServerList.setLastSelectedKey(entry.getKey());
        DlcServerList.save();
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

    private static AbstractWidget findRealmsButton(Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget && isRealmsButton(widget)) {
                return widget;
            }
        }
        return null;
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

    private static void alignTitleButtons(Screen screen) {
        AbstractWidget realmsWidget = findRealmsButton(screen);
        AbstractWidget multiplayerWidget = findButtonByKey(screen, "menu.multiplayer");
        AbstractWidget modsWidget = findModsButton(screen);
        AbstractWidget dlcWidget = findButtonByKey(screen, "dlc_manager.title");
        AbstractWidget updateWidget = findButtonByKey(screen, "dlc_manager.update.import_icon");
        if (realmsWidget != null) {
            realmsWidget.visible = false;
            realmsWidget.active = false;
        }
        if (dlcWidget == null) {
            return;
        }
        int rowStep = 24;
        int rowX = screen.width / 2 - 100;
        int rowY = screen.height / 4 + 48;
        int rowW = 200;
        int rowH = 20;
        AbstractWidget anchor = multiplayerWidget != null ? multiplayerWidget : realmsWidget;
        if (anchor != null) {
            rowStep = anchor.getHeight() + 4;
            rowX = anchor.getX();
            rowW = anchor.getWidth();
            rowH = anchor.getHeight();
            rowY = anchor.getY() + rowStep;
            if (anchor == realmsWidget && multiplayerWidget == null) {
                rowY = anchor.getY();
            }
        }
        int[] pos = DlcPathConfig.computeTitleButtonPosition(screen.width, screen.height, rowW, rowH, rowX, rowY);
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
            setWidgetHeight(modsWidget, rowH);
            int dlcX = rowX + halfWidth + gap;
            int dlcWidth = halfWidth;
            if (updateWidget != null) {
                int defaultUpdateX = dlcX + dlcWidth + iconGap;
                if (DlcPathConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                    defaultUpdateX = dlcX - iconGap - iconWidth;
                }
                int[] updatePos = DlcPathConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconWidth, rowH, defaultUpdateX, rowY);
                updateWidget.setWidth(iconWidth);
                updateWidget.setX(updatePos[0]);
                updateWidget.setY(updatePos[1]);
                setWidgetHeight(updateWidget, rowH);
            }
            dlcWidget.setX(dlcX);
            dlcWidget.setY(rowY);
            dlcWidget.setWidth(dlcWidth);
            setWidgetHeight(dlcWidget, rowH);

            int optionsRowY = rowY + rowStep;
            moveBottomRow(screen, optionsRowY);
        } else {
            int iconGap = 4;
            int iconWidth = 20;
            int dlcWidth = rowW;
            if (updateWidget != null) {
                int defaultUpdateX = rowX + dlcWidth + iconGap;
                if (DlcPathConfig.shouldPlaceTitleUpdateButtonLeftOfTitle()) {
                    defaultUpdateX = rowX - iconGap - iconWidth;
                }
                int[] updatePos = DlcPathConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconWidth, rowH, defaultUpdateX, rowY);
                updateWidget.setWidth(iconWidth);
                updateWidget.setX(updatePos[0]);
                updateWidget.setY(updatePos[1]);
                setWidgetHeight(updateWidget, rowH);
            }
            dlcWidget.setX(rowX);
            dlcWidget.setY(rowY);
            dlcWidget.setWidth(dlcWidth);
            setWidgetHeight(dlcWidget, rowH);
        }
    }

    private static void setWidgetHeight(AbstractWidget widget, int height) {
        if (widget == null) {
            return;
        }
        try {
            java.lang.reflect.Method method = widget.getClass().getMethod("setHeight", int.class);
            method.invoke(widget, height);
            return;
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Field field = AbstractWidget.class.getDeclaredField("height");
            field.setAccessible(true);
            field.setInt(widget, height);
        } catch (Exception ignored) {
        }
    }

    private static void syncEnabledResourcePacks() {
        DlcClientRuntime.syncEnabledResourcePacks();
    }

    private static String resolvePackId(PackRepository repo, String fileName) {
        String fileId = "file/" + fileName;
        if (repo.isAvailable(fileId)) {
            return fileId;
        }
        if (repo.isAvailable(fileName)) {
            return fileName;
        }
        for (String id : repo.getAvailableIds()) {
            if (id.endsWith("/" + fileName) || id.endsWith(fileName)) {
                return id;
            }
        }
        return null;
    }

    private static boolean isPackSelected(PackRepository repo, java.util.Set<String> selected, String fileName) {
        String fileId = "file/" + fileName;
        for (String id : selected) {
            if (!repo.isAvailable(id)) {
                continue;
            }
            if (id.equals(fileId) || id.equals(fileName) || id.endsWith("/" + fileName) || id.endsWith(fileName)) {
                return true;
            }
        }
        return false;
    }

    private static void copyRecursive(java.nio.file.Path source, java.nio.file.Path target) throws java.io.IOException {
        if (java.nio.file.Files.isDirectory(source)) {
            java.nio.file.Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(java.nio.file.Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                    java.nio.file.Path relative = source.relativize(dir);
                    java.nio.file.Files.createDirectories(target.resolve(relative));
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                    java.nio.file.Path relative = source.relativize(file);
                    java.nio.file.Files.copy(file, target.resolve(relative), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } else {
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
