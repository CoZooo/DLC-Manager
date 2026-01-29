package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.client.notice.DlcNoticeManager;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen;
import com.cozooo.dlc_manager.client.screen.DlcServerMaLiLibScreen;
import com.cozooo.dlc_manager.client.screen.DlcServerMismatchScreen;
import com.cozooo.dlc_manager.client.screen.DlcServerPasswordScreen;
import com.cozooo.dlc_manager.client.screen.DlcServerListScreen;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.client.screen.DlcSelfUpdateScreen;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.client.server.DlcServerVersionUtil;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
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
    private static long lastJoinClickMs = 0L;
    private static String lastJoinClickAddress = null;
    private static JoinMultiplayerScreen lastJoinClickScreen = null;
    private static final Map<JoinMultiplayerScreen, Button> JOIN_BUTTONS = new WeakHashMap<>();
    private static final Map<JoinMultiplayerScreen, Button> JOIN_PROXIES = new WeakHashMap<>();

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
            if (screen instanceof JoinMultiplayerScreen joinScreen) {
                AbstractWidget joinButton = findButtonByKey(screen, "selectServer.select");
                if (joinButton != null && (joinButton.visible || joinButton.active)) {
                    if (joinButton instanceof Button button) {
                        JOIN_BUTTONS.put(joinScreen, button);
                    }
                    joinButton.visible = false;
                    joinButton.active = false;
                    Button proxy = Button.builder(Component.translatable("selectServer.select"), btn -> handleJoin(joinScreen))
                        .bounds(joinButton.getX(), joinButton.getY(), joinButton.getWidth(), joinButton.getHeight())
                        .build();
                    proxy.active = true;
                    Screens.getButtons(screen).add(proxy);
                    JOIN_PROXIES.put(joinScreen, proxy);
                }
                AbstractWidget addButton = findButtonByKey(screen, "selectServer.add");
                if (addButton instanceof Button originalAdd && (addButton.visible || addButton.active)) {
                    addButton.visible = false;
                    addButton.active = false;
                    Button addProxy = Button.builder(addButton.getMessage(), btn -> originalAdd.onPress())
                        .bounds(addButton.getX(), addButton.getY(), addButton.getWidth(), addButton.getHeight())
                        .build();
                    Screens.getButtons(screen).add(addProxy);
                }
                ScreenMouseEvents.allowMouseClick(joinScreen).register((clickedScreen, mouseX, mouseY, button) -> {
                    if (button != 0) {
                        return true;
                    }
                    return !handleJoinDoubleClick(joinScreen);
                });
            }
            if (isDirectConnectScreen(screen)) {
                AbstractWidget joinButton = findButtonByKey(screen, "selectServer.select");
                if (joinButton instanceof Button original && (joinButton.visible || joinButton.active)) {
                    joinButton.visible = false;
                    joinButton.active = false;
                    Button proxy = Button.builder(joinButton.getMessage(), btn -> handleDirectConnect(screen, original))
                        .bounds(joinButton.getX(), joinButton.getY(), joinButton.getWidth(), joinButton.getHeight())
                        .build();
                    Screens.getButtons(screen).add(proxy);
                }
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
                if (DlcRemoteManager.maybeShowFromManager(managerScreen)) {
                    return;
                }
                Minecraft.getInstance().setScreen(managerScreen);
            }).bounds(x, y, w, h).build();
            Button updateButton = Button.builder(Component.translatable("dlc_manager.update.import_icon"),
                btn -> DlcUpdateClient.openUpdateManager(screen))
                .bounds(x + w + 4, y, 20, h)
                .build();

            if (!hasDlcButton(screen)) {
                Screens.getButtons(screen).add(button);
            }
            if (DlcPathConfig.isUButtomShown() && DlcPathConfig.isUpdateManagerOn()
                && findButtonByKey(screen, "dlc_manager.update.import_icon") == null) {
                Screens.getButtons(screen).add(updateButton);
            }
            alignTitleButtons(screen);
            pendingAlignScreen = screen;
            runStartupChecksAsync(screen);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.screen instanceof JoinMultiplayerScreen joinScreen) {
                Button proxy = JOIN_PROXIES.get(joinScreen);
                if (proxy != null) {
                    Button original = JOIN_BUTTONS.get(joinScreen);
                    if (original != null) {
                        original.visible = false;
                        original.active = false;
                        proxy.visible = true;
                        proxy.active = getSelectedServerData(joinScreen) != null;
                    } else {
                        proxy.active = getSelectedServerData(joinScreen) != null;
                    }
                }
            }
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
        if (startupCheckRunning) {
            return;
        }
        startupCheckRunning = true;
        new Thread(() -> {
            LOGGER.info("[dlc-manager] startup check begin");
            long startAt = System.currentTimeMillis();
            UpdateCheckResult updateResult = null;
            boolean hasUpdate = false;
            boolean hasRequired = false;
            java.util.List<DlcRequiredEntry> missing = java.util.List.of();
            DlcSelfUpdateService.SelfUpdateResult selfUpdateResult = null;
            boolean hasSelfUpdate = false;
            boolean updateManagerOn = DlcPathConfig.isUpdateManagerOn();
            boolean selfUpdateAutoCheckOn = DlcPathConfig.isSelfUpdateAutoCheckOn();
            String changelogText = "";
            String changelogVersion = null;
            if (updateManagerOn) {
                Path dlcRoot = DlcPaths.dlcRoot();
                if (dlcRoot != null) {
                    DlcUpdateVersion currentVersion = DlcUpdateVersion.load(dlcRoot);
                    if (currentVersion != null) {
                        String currentDlc = currentVersion.getDlc();
                        String shown = currentVersion.getChangelogShown();
                        if (currentDlc != null && !currentDlc.isBlank() && (shown == null || !currentDlc.equals(shown))) {
                            DlcUpdateMeta meta = DlcUpdateService.loadMetaForVersion(currentDlc);
                            if (meta != null) {
                                changelogText = meta.resolveChangelog(PlatformServices.get().languageCode());
                                if (changelogText != null && !changelogText.isBlank()) {
                                    changelogVersion = currentDlc;
                                }
                            }
                        }
                    }
                }
            }
            if (updateManagerOn && !DlcUpdateClient.isPrompted()) {
                long updateAt = System.currentTimeMillis();
                updateResult = DlcUpdateService.checkForUpdates();
                hasUpdate = updateResult != null && updateResult.hasUpdates();
                int updateCount = updateResult == null || updateResult.getUpdates() == null ? 0 : updateResult.getUpdates().size();
                LOGGER.info("[dlc-manager] update check done: hasUpdate={}, count={}, took={}ms", hasUpdate, updateCount, System.currentTimeMillis() - updateAt);
            }
            if (selfUpdateAutoCheckOn && !selfUpdatePrompted) {
                long selfAt = System.currentTimeMillis();
                selfUpdateResult = DlcSelfUpdateService.checkForUpdate();
                hasSelfUpdate = selfUpdateResult != null && selfUpdateResult.hasUpdate;
                LOGGER.info("[dlc-manager] self update check done: hasUpdate={}, took={}ms", hasSelfUpdate, System.currentTimeMillis() - selfAt);
            }
            if (!hasUpdate) {
                if (!DlcRequiredManager.hasSkipMarker()) {
                    long requiredAt = System.currentTimeMillis();
                    hasRequired = DlcRequiredManager.hasMissingDownloadsForPrompt();
                    if (hasRequired) {
                        missing = DlcRequiredManager.collectMissingDownloadsForPrompt();
                    }
                    LOGGER.info("[dlc-manager] required check done: hasRequired={}, missingCount={}, took={}ms", hasRequired, missing.size(), System.currentTimeMillis() - requiredAt);
                }
            }
            final boolean finalHasUpdate = hasUpdate;
            final UpdateCheckResult finalUpdateResult = updateResult;
            final boolean finalHasRequired = hasRequired;
            final java.util.List<DlcRequiredEntry> finalMissing = missing;
            final String finalChangelog = changelogText;
            final String finalChangelogVersion = changelogVersion;
            final boolean finalHasSelfUpdate = hasSelfUpdate;
            final DlcSelfUpdateService.SelfUpdateResult finalSelfUpdateResult = selfUpdateResult;
            Minecraft.getInstance().execute(() -> {
                startupCheckRunning = false;
                LOGGER.info("[dlc-manager] startup check finished in {}ms", System.currentTimeMillis() - startAt);
                if (!(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                    return;
                }
                Runnable continueFlow = () -> {
                    if (finalHasSelfUpdate && finalSelfUpdateResult != null && !selfUpdatePrompted) {
                        selfUpdatePrompted = true;
                        Minecraft.getInstance().setScreen(new DlcSelfUpdateScreen(screen, finalSelfUpdateResult));
                        return;
                    }
                    if (finalHasUpdate && finalUpdateResult != null) {
                        DlcUpdateClient.showPrompt(screen, finalUpdateResult);
                        return;
                    }
                    if (finalHasRequired && !finalMissing.isEmpty()) {
                        DlcRequiredManager.markPrompted();
                        Minecraft.getInstance().setScreen(new com.cozooo.dlc_manager.client.screen.DlcRequiredDownloadScreen(screen, finalMissing));
                        return;
                    }
                    DlcNoticeManager.showFromTitle(screen);
                };
                if (finalChangelog != null && !finalChangelog.isBlank() && finalChangelogVersion != null && !finalChangelogVersion.isBlank()) {
                    Minecraft.getInstance().setScreen(new com.cozooo.dlc_manager.client.screen.DlcUpdateChangelogScreen(screen, finalChangelog, () -> {
                        Path root = DlcPaths.dlcRoot();
                        if (root != null) {
                            DlcUpdateVersion version = DlcUpdateVersion.load(root);
                            version.setChangelogShown(finalChangelogVersion);
                            version.save(root);
                        }
                        continueFlow.run();
                    }));
                    return;
                }
                continueFlow.run();
            });
        }, "dlc-startup-check").start();
    }

    private static boolean isRealmsButton(AbstractWidget widget) {
        if (widget.getMessage().getContents() instanceof TranslatableContents contents) {
            return "menu.online".equals(contents.getKey());
        }
        return false;
    }

    private static AbstractWidget findButtonByKey(Screen screen, String key) {
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                if (widget.getMessage().getContents() instanceof TranslatableContents contents) {
                    if (key.equals(contents.getKey())) {
                        return widget;
                    }
                }
            }
        }
        return null;
    }

    private static boolean canOpenServerManager() {
        if (!DlcPathConfig.isServerSupportOn()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        if (minecraft.hasSingleplayerServer()) {
            return false;
        }
        return minecraft.player.hasPermissions(2);
    }

    private static void openManagerFromCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        Screen managerScreen = new DlcMaLiLibScreen(parent);
        if (DlcRemoteManager.maybeShowFromManager(managerScreen)) {
            return;
        }
        minecraft.setScreen(managerScreen);
    }

    private static void openSelfUpdateFromCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new DlcSelfUpdateScreen(minecraft.screen, null));
    }

    private static void openUpdateManagerFromCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        DlcUpdateClient.openUpdateManager(minecraft.screen);
    }

    private static void openUpdatePromptFromCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        UpdateCheckResult result = DlcUpdateService.checkForUpdates();
        if (result == null || !result.hasUpdates()) {
            SystemToast.addOrUpdate(minecraft.getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                Component.translatable("dlc_manager.update.no_updates"), Component.empty());
            return;
        }
        DlcUpdateClient.showPrompt(minecraft.screen, result);
    }

    private static void openServerManagerFromCommand() {
        if (!canOpenServerManager()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new DlcServerListScreen(minecraft.screen));
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

    private static void handleJoin(JoinMultiplayerScreen screen) {
        ServerData serverData = getSelectedServerData(screen);
        if (serverData == null) {
            invokeOriginalJoin(screen);
            return;
        }
        DlcServerEntry entry = createEntryFromServer(serverData);
        DlcServerVersionUtil.VersionPair serverVersion = resolveServerVersion(serverData, entry, screen);
        if (serverVersion == null) {
            invokeOriginalJoin(screen);
            return;
        }
        DlcUpdateVersion localVersion = DlcUpdateVersion.load(DlcPaths.dlcRoot());
        boolean mismatch = localVersion == null
            || localVersion.getBase() == null
            || localVersion.getDlc() == null
            || !serverVersion.base.equals(localVersion.getBase())
            || !serverVersion.dlc.equals(localVersion.getDlc());
        if (!mismatch) {
            invokeOriginalJoin(screen);
            return;
        }
        Minecraft.getInstance().setScreen(new DlcServerMismatchScreen(screen, entry, serverVersion, localVersion));
    }

    private static void handleDirectConnect(Screen screen, Button originalJoinButton) {
        String address = getDirectConnectAddress(screen);
        if (address == null || address.isBlank()) {
            originalJoinButton.onPress();
            return;
        }
        ServerData serverData = createServerDataForAddress(address);
        if (serverData == null) {
            originalJoinButton.onPress();
            return;
        }
        DlcServerEntry entry = createEntryFromServer(serverData);
        pingServerForMotd(serverData, () -> {
            DlcServerVersionUtil.VersionPair serverVersion = resolveServerVersion(serverData, entry, screen);
            if (serverVersion == null) {
                originalJoinButton.onPress();
                return;
            }
            DlcUpdateVersion localVersion = DlcUpdateVersion.load(DlcPaths.dlcRoot());
            boolean mismatch = localVersion == null
                || localVersion.getBase() == null
                || localVersion.getDlc() == null
                || !serverVersion.base.equals(localVersion.getBase())
                || !serverVersion.dlc.equals(localVersion.getDlc());
            if (!mismatch) {
                originalJoinButton.onPress();
                return;
            }
            Minecraft.getInstance().setScreen(new DlcServerMismatchScreen(screen, entry, serverVersion, localVersion));
        });
    }

    private static boolean handleJoinDoubleClick(JoinMultiplayerScreen screen) {
        ServerData serverData = getSelectedServerData(screen);
        if (serverData == null) {
            return false;
        }
        String address = serverData.ip == null ? serverData.name : serverData.ip;
        long now = Util.getMillis();
        boolean isDouble = screen == lastJoinClickScreen
            && address != null
            && address.equals(lastJoinClickAddress)
            && now - lastJoinClickMs <= 300L;
        lastJoinClickMs = now;
        lastJoinClickAddress = address;
        lastJoinClickScreen = screen;
        if (!isDouble) {
            return false;
        }
        handleJoin(screen);
        return true;
    }

    private static void invokeOriginalJoin(JoinMultiplayerScreen screen) {
        Button original = JOIN_BUTTONS.get(screen);
        if (original != null) {
            original.onPress();
            return;
        }
        joinSelectedServer(screen);
    }

    private static DlcServerVersionUtil.VersionPair resolveServerVersion(ServerData data, DlcServerEntry entry, Screen screen) {
        if (entry != null) {
            com.cozooo.dlc_manager.client.server.DlcServerInfo cached =
                com.cozooo.dlc_manager.client.server.DlcServerCache.get(entry.getKey());
            if (cached != null && cached.baseVersion != null && cached.dlcVersion != null) {
                return new DlcServerVersionUtil.VersionPair(cached.baseVersion, cached.dlcVersion);
            }
        }
        String motd = data.motd == null ? "" : data.motd.getString();
        DlcServerVersionUtil.VersionPair version = DlcServerVersionUtil.parse(motd);
        if (version != null) {
            return version;
        }
        try {
            String playerName = null;
            String playerUuid = null;
            if (Minecraft.getInstance().player != null) {
                playerName = Minecraft.getInstance().player.getGameProfile().getName();
                playerUuid = Minecraft.getInstance().player.getUUID().toString();
            }
            var info = com.cozooo.dlc_manager.client.server.DlcServerTcpClient.fetchServerInfo(entry, 1500, playerName, playerUuid);
            if (info == null || info.baseVersion == null || info.dlcVersion == null) {
                return null;
            }
            return new DlcServerVersionUtil.VersionPair(info.baseVersion, info.dlcVersion);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String getDirectConnectAddress(Screen screen) {
        try {
            for (var field : screen.getClass().getDeclaredFields()) {
                if (EditBox.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(screen);
                    if (value instanceof EditBox editBox) {
                        return editBox.getValue();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static void pingServerForMotd(ServerData data, Runnable onComplete) {
        Runnable completeOnMain = () -> Minecraft.getInstance().execute(onComplete);
        try {
            Object pinger = getServerStatusPinger(Minecraft.getInstance());
            if (pinger == null) {
                completeOnMain.run();
                return;
            }
            for (var method : pinger.getClass().getMethods()) {
                if (!"pingServer".equals(method.getName())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length < 2 || params[0] != ServerData.class) {
                    continue;
                }
                if (params.length == 2 && params[1] == Runnable.class) {
                    method.invoke(pinger, data, completeOnMain);
                    return;
                }
                if (params.length == 3 && params[1] == Runnable.class && params[2] == Runnable.class) {
                    method.invoke(pinger, data, completeOnMain, completeOnMain);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        completeOnMain.run();
    }

    private static Object getServerStatusPinger(Minecraft minecraft) {
        try {
            var method = minecraft.getClass().getMethod("getServerStatusPinger");
            method.setAccessible(true);
            return method.invoke(minecraft);
        } catch (Throwable ignored) {
        }
        for (var field : minecraft.getClass().getDeclaredFields()) {
            if (!field.getType().getSimpleName().contains("ServerStatusPinger")) {
                continue;
            }
            try {
                field.setAccessible(true);
                return field.get(minecraft);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static ServerData createServerDataForAddress(String address) {
        try {
            for (var ctor : ServerData.class.getDeclaredConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 3 && params[0] == String.class && params[1] == String.class) {
                    ctor.setAccessible(true);
                    if (params[2] == boolean.class || params[2] == Boolean.class) {
                        return (ServerData) ctor.newInstance(address, address, false);
                    }
                    if (params[2].isEnum()) {
                        Object[] constants = params[2].getEnumConstants();
                        Object value = constants.length > 0 ? constants[0] : null;
                        for (Object constant : constants) {
                            if ("OTHER".equalsIgnoreCase(constant.toString())) {
                                value = constant;
                                break;
                            }
                        }
                        return (ServerData) ctor.newInstance(address, address, value);
                    }
                }
                if (params.length == 2 && params[0] == String.class && params[1] == String.class) {
                    ctor.setAccessible(true);
                    return (ServerData) ctor.newInstance(address, address);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isDirectConnectScreen(Screen screen) {
        return screen.getClass().getName().endsWith("DirectConnectScreen");
    }

    private static void joinSelectedServer(JoinMultiplayerScreen screen) {
        try {
            var method = screen.getClass().getDeclaredMethod("joinSelectedServer");
            method.setAccessible(true);
            method.invoke(screen);
            return;
        } catch (Throwable ignored) {
            // Fallback below.
        }
        try {
            var method = screen.getClass().getDeclaredMethod("joinSelected");
            method.setAccessible(true);
            method.invoke(screen);
        } catch (Throwable ignored) {
            // Best-effort fallback.
        }
    }

    private static ServerData getSelectedServerData(JoinMultiplayerScreen screen) {
        try {
            for (var method : screen.getClass().getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == ServerData.class) {
                    method.setAccessible(true);
                    return (ServerData) method.invoke(screen);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object list = findServerSelectionList(screen);
            if (list == null) {
                return null;
            }
            Object entry = getSelectedEntry(list);
            return extractServerData(entry);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object findServerSelectionList(JoinMultiplayerScreen screen) {
        for (var field : screen.getClass().getDeclaredFields()) {
            if (field.getType().getSimpleName().contains("ServerSelectionList")) {
                try {
                    field.setAccessible(true);
                    return field.get(screen);
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            for (var child : screen.children()) {
                if (child != null && child.getClass().getSimpleName().contains("ServerSelectionList")) {
                    return child;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object getSelectedEntry(Object list) throws Exception {
        Object entry = invokeNoArg(list, "getSelected");
        if (entry == null) {
            entry = invokeNoArg(list, "getSelectedEntry");
        }
        if (entry == null) {
            entry = invokeNoArg(list, "getSelectedServer");
        }
        return unwrapOptional(entry);
    }

    private static ServerData extractServerData(Object entry) throws Exception {
        if (entry == null) {
            return null;
        }
        if (entry instanceof ServerData serverData) {
            return serverData;
        }
        Object direct = invokeNoArg(entry, "getServerData");
        if (direct instanceof ServerData serverData) {
            return serverData;
        }
        for (var field : entry.getClass().getDeclaredFields()) {
            if (field.getType() == ServerData.class) {
                field.setAccessible(true);
                return (ServerData) field.get(entry);
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            var method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
        }
        try {
            var method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object unwrapOptional(Object value) {
        if (value instanceof java.util.Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }

    private static DlcServerEntry createEntryFromServer(ServerData data) {
        DlcServerEntry entry = new DlcServerEntry();
        entry.name = data.name;
        String ip = data.ip == null ? "" : data.ip;
        String host = ip;
        int port = DlcPathConfig.getServerPort();
        int index = ip.lastIndexOf(':');
        if (index > 0 && index < ip.length() - 1) {
            host = ip.substring(0, index);
            try {
                port = Integer.parseInt(ip.substring(index + 1));
            } catch (NumberFormatException ignored) {
                port = DlcPathConfig.getServerPort();
            }
        }
        entry.host = host;
        entry.port = port;
        entry.useTcp = true;
        return entry;
    }

    private static String getPlayerName() {
        if (Minecraft.getInstance().player == null) {
            return "";
        }
        return Minecraft.getInstance().player.getGameProfile().getName();
    }

    private static String getPlayerUuid() {
        if (Minecraft.getInstance().player == null) {
            return "";
        }
        return Minecraft.getInstance().player.getUUID().toString();
    }

    private static void openServerManagerFromIngame(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        ServerData data = minecraft.getCurrentServer();
        if (data == null) {
            minecraft.setScreen(new DlcServerListScreen(parent));
            return;
        }
        DlcServerEntry entry = createEntryFromServer(data);
        if (entry == null) {
            minecraft.setScreen(new DlcServerListScreen(parent));
            return;
        }
        DlcServerList.load();
        DlcServerEntry existing = findEntryByKey(entry.getKey());
        if (existing != null) {
            entry = existing;
        }
        if (entry.password != null && !entry.password.isBlank()) {
            DlcServerEntry finalEntry = entry;
            CompletableFuture
                .supplyAsync(() -> fetchServerInfo(finalEntry))
                .whenComplete((info, throwable) -> minecraft.execute(() -> {
                    if (info != null) {
                        saveEntryToList(finalEntry);
                        com.cozooo.dlc_manager.client.server.DlcServerCache.put(finalEntry.getKey(), info);
                        minecraft.setScreen(new DlcServerMaLiLibScreen(parent, finalEntry, info));
                        return;
                    }
                    minecraft.setScreen(new DlcServerPasswordScreen(parent, finalEntry, false));
                }));
            return;
        }
        minecraft.setScreen(new DlcServerPasswordScreen(parent, entry, false));
    }

    private static DlcServerInfo fetchServerInfo(DlcServerEntry entry) {
        try {
            return com.cozooo.dlc_manager.client.server.DlcServerTcpClient.fetchServerInfo(entry, 5000,
                getPlayerName(), getPlayerUuid());
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
        int desiredW = DlcPathConfig.getTitleWidth();
        int desiredH = DlcPathConfig.getTitleHeight();
        int buttonW = desiredW > 0 ? desiredW : rowW;
        int buttonH = desiredH > 0 ? desiredH : rowH;
        int[] pos = DlcPathConfig.computeTitleButtonPosition(screen.width, screen.height, buttonW, buttonH, rowX, rowY);
        rowX = pos[0];
        rowY = pos[1];
        if (desiredH > 0) {
            rowH = buttonH;
            rowStep = rowH + 4;
        }

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
            int dlcWidth = desiredW > 0 ? desiredW : halfWidth;
            if (updateWidget != null) {
                int baseWidth = desiredW > 0 ? desiredW : halfWidth;
                dlcWidth = Math.max(40, baseWidth - iconWidth - iconGap);
                updateWidget.setWidth(iconWidth);
                updateWidget.setX(dlcX + dlcWidth + iconGap);
                updateWidget.setY(rowY);
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
            int dlcWidth = desiredW > 0 ? desiredW : rowW;
            if (updateWidget != null) {
                int baseWidth = desiredW > 0 ? desiredW : rowW;
                dlcWidth = Math.max(40, baseWidth - iconWidth - iconGap);
                updateWidget.setWidth(iconWidth);
                updateWidget.setX(rowX + dlcWidth + iconGap);
                updateWidget.setY(rowY);
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
            // Fall back to direct field access for 1.20.x.
        }
        try {
            java.lang.reflect.Field field = AbstractWidget.class.getDeclaredField("height");
            field.setAccessible(true);
            field.setInt(widget, height);
        } catch (Exception ignored) {
            // Best-effort only.
        }
    }

    private static void syncEnabledResourcePacks() {
        Minecraft minecraft = Minecraft.getInstance();
        PackRepository repo = minecraft.getResourcePackRepository();
        try {
            repo.reload();
            java.util.Set<String> selected = new java.util.LinkedHashSet<>(repo.getSelectedIds());
            java.util.Set<String> originalSelected = new java.util.LinkedHashSet<>(selected);
            boolean modified = false;
            var grouped = new DlcScanner().scan();
            for (DlcItem item : grouped.getOrDefault(DlcType.RESOURCE_PACK, java.util.List.of())) {
                if (!item.isEnabled()) {
                    continue;
                }
                String fileName = item.getSourcePath().getFileName().toString();
                String packId = resolvePackId(repo, fileName);
                if (packId == null) {
                    java.nio.file.Path target = minecraft.getResourcePackDirectory().resolve(fileName);
                    copyRecursive(item.getSourcePath(), target);
                    repo.reload();
                    packId = resolvePackId(repo, fileName);
                    modified = true;
                }
                if (packId != null) {
                    if (!isPackSelected(repo, selected, fileName)) {
                        if (selected.add(packId)) {
                            modified = true;
                        }
                    }
                }
            }
            if (modified || !selected.equals(originalSelected)) {
                repo.setSelected(selected);
                minecraft.options.resourcePacks = new java.util.ArrayList<>(selected);
                minecraft.options.save();
                minecraft.reloadResourcePacks();
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
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
