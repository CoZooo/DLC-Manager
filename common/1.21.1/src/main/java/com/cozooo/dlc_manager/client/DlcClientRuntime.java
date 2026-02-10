package com.cozooo.dlc_manager.client;

import com.cozooo.dlc_manager.client.notice.DlcNoticeManager;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen;
import com.cozooo.dlc_manager.client.screen.DlcRequiredDownloadScreen;
import com.cozooo.dlc_manager.client.screen.DlcSelfUpdateScreen;
import com.cozooo.dlc_manager.client.screen.DlcServerListScreen;
import com.cozooo.dlc_manager.client.screen.DlcServerMaLiLibScreen;
import com.cozooo.dlc_manager.client.screen.DlcServerManagerScreen;
import com.cozooo.dlc_manager.client.screen.DlcServerPasswordScreen;
import com.cozooo.dlc_manager.client.screen.DlcUpdateChangelogScreen;
import com.cozooo.dlc_manager.client.server.DlcServerCache;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class DlcClientRuntime {
    private static volatile boolean startupCheckRunning = false;
    private static volatile boolean selfUpdatePrompted = false;
    private static final Logger LOGGER = LogUtils.getLogger();

    private DlcClientRuntime() {
    }

    public static AbstractWidget findButtonByKey(Screen screen, String key) {
        for (Object child : screen.children()) {
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

    public static boolean canOpenServerManager() {
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

    public static void openManagerFromCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        Screen managerScreen = new DlcMaLiLibScreen(parent);
        if (DlcRemoteManager.tryShowFromManager(managerScreen)) {
            return;
        }
        minecraft.setScreen(managerScreen);
    }

    public static void openSelfUpdateFromCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new DlcSelfUpdateScreen(minecraft.screen, null));
    }

    public static void openUpdateManagerFromCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        DlcUpdateClient.openUpdateManager(minecraft.screen);
    }

    public static void openUpdatePromptFromCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        UpdateCheckResult result = DlcUpdateService.checkForUpdates();
        if (result == null || !result.hasUpdates()) {
            SystemToast.addOrUpdate(
                minecraft.getToasts(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("dlc_manager.update.no_updates"),
                Component.empty()
            );
            return;
        }
        DlcUpdateClient.showPrompt(minecraft.screen, result);
    }

    public static void openServerManagerFromCommand() {
        if (!canOpenServerManager()) {
            return;
        }
        openServerManagerFromIngame(null);
    }

    public static void openServerManagerFromIngame(Screen parent) {
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
                        DlcServerCache.put(finalEntry.getKey(), info);
                        minecraft.setScreen(new DlcServerManagerScreen(parent, finalEntry, info, DlcServerManagerScreen.Tab.SERVERS, true));
                        return;
                    }
                    minecraft.setScreen(new DlcServerPasswordScreen(parent, finalEntry, false, true));
                }));
            return;
        }
        minecraft.setScreen(new DlcServerPasswordScreen(parent, entry, false, true));
    }

    public static void runStartupChecksAsync(Screen screen) {
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
                                changelogText = meta.resolveChangelog(CrossPlatformRuntime.languageCode());
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
            boolean finalHasUpdate = hasUpdate;
            UpdateCheckResult finalUpdateResult = updateResult;
            boolean finalHasRequired = hasRequired;
            java.util.List<DlcRequiredEntry> finalMissing = missing;
            String finalChangelog = changelogText;
            String finalChangelogVersion = changelogVersion;
            boolean finalHasSelfUpdate = hasSelfUpdate;
            DlcSelfUpdateService.SelfUpdateResult finalSelfUpdateResult = selfUpdateResult;
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
                        Minecraft.getInstance().setScreen(new DlcRequiredDownloadScreen(screen, finalMissing));
                        return;
                    }
                    DlcNoticeManager.showFromTitle(screen);
                };
                if (finalChangelog != null && !finalChangelog.isBlank() && finalChangelogVersion != null && !finalChangelogVersion.isBlank()) {
                    Minecraft.getInstance().setScreen(new DlcUpdateChangelogScreen(screen, finalChangelog, () -> {
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

    public static void syncEnabledResourcePacks() {
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
                if (packId != null && !isPackSelected(repo, selected, fileName)) {
                    if (selected.add(packId)) {
                        modified = true;
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
        }
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

    private static DlcServerInfo fetchServerInfo(DlcServerEntry entry) {
        try {
            return DlcServerConnection.fetchServerInfo(
                entry,
                5000,
                currentPlayerName(),
                currentPlayerUuid()
            );
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String currentPlayerName() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getGameProfile() != null) {
            String name = minecraft.player.getGameProfile().getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return CrossPlatformRuntime.playerName();
    }

    private static String currentPlayerUuid() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getGameProfile() != null && minecraft.player.getGameProfile().getId() != null) {
            return minecraft.player.getGameProfile().getId().toString();
        }
        return CrossPlatformRuntime.playerUuid();
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
                public java.nio.file.FileVisitResult preVisitDirectory(
                    java.nio.file.Path dir,
                    java.nio.file.attribute.BasicFileAttributes attrs
                ) throws java.io.IOException {
                    java.nio.file.Path relative = source.relativize(dir);
                    java.nio.file.Files.createDirectories(target.resolve(relative));
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(
                    java.nio.file.Path file,
                    java.nio.file.attribute.BasicFileAttributes attrs
                ) throws java.io.IOException {
                    java.nio.file.Path relative = source.relativize(file);
                    java.nio.file.Files.copy(
                        file,
                        target.resolve(relative),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    );
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } else {
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
