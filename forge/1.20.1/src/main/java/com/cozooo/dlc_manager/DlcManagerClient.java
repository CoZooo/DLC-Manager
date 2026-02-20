package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.DlcClientRuntime;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen;
import com.cozooo.dlc_manager.client.screen.DlcPathConfigScreen;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.repository.PackRepository;
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
import org.slf4j.Logger;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = DlcManager.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DlcManagerClient {
    private static volatile boolean startupCheckRunning = false;
    private static volatile boolean selfUpdatePrompted = false;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static void init(IEventBus modBus) {
        modBus.addListener(DlcManagerClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        DlcUpdateService.ensureUpdateDir();
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new DlcPathConfigScreen(parent)));
    }

    @Mod.EventBusSubscriber(modid = DlcManager.MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {
        private static boolean resourcePackSynced = false;
        private static volatile boolean startupCheckRunning = false;

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
            int iconY = button.getY();
            int[] updatePos = DlcPathConfig.computeTitleUpdateButtonPosition(screen.width, screen.height, iconSize, h, iconX, iconY);
            Button updateButton = Button.builder(Component.translatable("dlc_manager.update.import_icon"), btn -> DlcUpdateClient.openUpdateManager(screen))
                .bounds(updatePos[0], updatePos[1], iconSize, h)
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
            if (DlcClientRuntime.shouldShowMainMenuNoticeButton()
                && findButtonByKey(screen, "dlc_manager.notice") == null) {
                event.addListener(noticeButton);
            }
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
            double mouseX = readEventDouble(event, "getMouseX");
            double mouseY = readEventDouble(event, "getMouseY");
            int button = readEventInt(event, "getButton");
            if (DlcClientRuntime.handleTitleNoticePanelRelease(screen, mouseX, mouseY, button)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onTitleScreenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                return;
            }
            double mouseX = readEventDouble(event, "getMouseX");
            double mouseY = readEventDouble(event, "getMouseY");
            int button = readEventInt(event, "getMouseButton", "getButton");
            double dragX = readEventDouble(event, "getDragX");
            double dragY = readEventDouble(event, "getDragY");
            if (DlcClientRuntime.handleTitleNoticePanelDrag(screen, mouseX, mouseY, button, dragX, dragY)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onTitleScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
            Screen screen = event.getScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                return;
            }
            double mouseX = readEventDouble(event, "getMouseX");
            double mouseY = readEventDouble(event, "getMouseY");
            double horizontal = readEventDouble(event, "getScrollDeltaX", "getHorizontalScrollDelta");
            double vertical = readEventDouble(event, "getScrollDeltaY", "getScrollDelta");
            if (DlcClientRuntime.handleTitleNoticePanelScroll(screen, mouseX, mouseY, horizontal, vertical)) {
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

        private static double readEventDouble(Object event, String... methodNames) {
            for (String methodName : methodNames) {
                try {
                    Object value = event.getClass().getMethod(methodName).invoke(event);
                    if (value instanceof Number number) {
                        return number.doubleValue();
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
            return 0.0D;
        }

        private static int readEventInt(Object event, String... methodNames) {
            for (String methodName : methodNames) {
                try {
                    Object value = event.getClass().getMethod(methodName).invoke(event);
                    if (value instanceof Number number) {
                        return number.intValue();
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
            return 0;
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
    }

    private static void runStartupChecksAsync(Screen screen) {
        DlcClientRuntime.runStartupChecksAsync(screen);
    }
}
