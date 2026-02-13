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
import com.cozooo.dlc_manager.client.screen.DlcPackReleaseUpdateScreen;
import com.cozooo.dlc_manager.client.screen.DlcUpdatePromptScreen;
import com.cozooo.dlc_manager.client.screen.DlcUpdateChangelogScreen;
import com.cozooo.dlc_manager.client.server.DlcServerCache;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerList;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class DlcClientRuntime {
    private static volatile boolean startupCheckRunning = false;
    private static volatile boolean startupChecksCompleted = false;
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
        if (startupCheckRunning || startupChecksCompleted) {
            return;
        }
        startupCheckRunning = true;
        startupChecksCompleted = true;
        new Thread(() -> {
            LOGGER.info("[dlc-manager] startup check begin");
            long startAt = System.currentTimeMillis();
            UpdateCheckResult updateResult = null;
            boolean hasUpdate = false;
            DlcPackReleaseUpdateService.CheckResult packUpdateResult = null;
            boolean hasPackUpdate = false;
            DlcSelfUpdateService.SelfUpdateResult selfUpdateResult = null;
            boolean hasSelfUpdate = false;
            boolean hasRequired = false;
            java.util.List<DlcRequiredEntry> missing = java.util.List.of();
            boolean updateManagerOn = DlcPathConfig.isUpdateManagerOn();
            boolean selfUpdateAutoCheckOn = DlcPathConfig.isSelfUpdateAutoCheckOn();
            String changelogText = "";
            String changelogVersion = null;
            String currentBaseVersion = "1.0.0";
            String currentDlcVersion = "1.0.0";
            Path dlcRoot = DlcPaths.dlcRoot();
            if (dlcRoot != null) {
                DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
                if (version != null) {
                    if (version.getBase() != null && !version.getBase().isBlank()) {
                        currentBaseVersion = version.getBase();
                    }
                    if (version.getDlc() != null && !version.getDlc().isBlank()) {
                        currentDlcVersion = version.getDlc();
                    }
                }
            }
            String repoUrl = DlcPathConfig.getUpdateGithubRepoUrl();
            if (repoUrl != null && !repoUrl.isBlank()) {
                long packAt = System.currentTimeMillis();
                packUpdateResult = DlcPackReleaseUpdateService.checkForUpdates(
                    DlcPathConfig.getModpackUrl(),
                    repoUrl,
                    currentBaseVersion,
                    currentDlcVersion,
                    DlcPackReleaseUpdateService.Side.CLIENT
                );
                boolean promptSkipped = packUpdateResult != null
                    && packUpdateResult.hasUpdate()
                    && DlcPackReleaseUpdateService.isPromptSkippedForUpdate(packUpdateResult, DlcPackReleaseUpdateService.Side.CLIENT);
                boolean alreadyDownloaded = packUpdateResult != null
                    && packUpdateResult.hasUpdate()
                    && DlcPackReleaseUpdateService.hasDownloadedClientPackages(packUpdateResult);
                hasPackUpdate = packUpdateResult != null && packUpdateResult.hasUpdate() && !alreadyDownloaded && !promptSkipped;
                LOGGER.info("[dlc-manager] pack update check done: hasUpdate={}, alreadyDownloaded={}, promptSkipped={}, took={}ms",
                    hasPackUpdate, alreadyDownloaded, promptSkipped, System.currentTimeMillis() - packAt);
            }
            if (updateManagerOn) {
                if (dlcRoot != null) {
                    DlcUpdateVersion currentVersion = DlcUpdateVersion.load(dlcRoot);
                    if (currentVersion != null) {
                        String currentDlc = currentVersion.getDlc();
                        String shown = currentVersion.getChangelogShown();
                        if (currentDlc != null && !currentDlc.isBlank()
                            && shown != null && !shown.isBlank()
                            && !currentDlc.equals(shown)) {
                            List<DlcUpdateMeta> pending = collectPendingChangelogMeta(shown, currentDlc);
                            if (!pending.isEmpty()) {
                                changelogText = buildPendingChangelogText(pending, CrossPlatformRuntime.languageCode());
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
                boolean promptSkipped = updateResult != null
                    && updateResult.hasUpdates()
                    && DlcUpdateService.isPromptSkippedForUpdate(updateResult);
                hasUpdate = updateResult != null && updateResult.hasUpdates() && !promptSkipped;
                int updateCount = updateResult == null || updateResult.getUpdates() == null ? 0 : updateResult.getUpdates().size();
                LOGGER.info("[dlc-manager] update check done: hasUpdate={}, count={}, promptSkipped={}, took={}ms",
                    hasUpdate, updateCount, promptSkipped, System.currentTimeMillis() - updateAt);
            }
            if (selfUpdateAutoCheckOn && !selfUpdatePrompted) {
                long selfAt = System.currentTimeMillis();
                selfUpdateResult = DlcSelfUpdateService.checkForUpdate();
                boolean promptSkipped = selfUpdateResult != null
                    && selfUpdateResult.hasUpdate
                    && DlcSelfUpdateService.isPromptSkippedForUpdate(selfUpdateResult);
                hasSelfUpdate = selfUpdateResult != null && selfUpdateResult.hasUpdate && !promptSkipped;
                LOGGER.info("[dlc-manager] self update check done: hasUpdate={}, promptSkipped={}, took={}ms",
                    hasSelfUpdate, promptSkipped, System.currentTimeMillis() - selfAt);
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
            boolean finalHasPackUpdate = hasPackUpdate;
            DlcPackReleaseUpdateService.CheckResult finalPackUpdateResult = packUpdateResult;
            boolean finalHasSelfUpdate = hasSelfUpdate;
            DlcSelfUpdateService.SelfUpdateResult finalSelfUpdateResult = selfUpdateResult;
            boolean finalHasRequired = hasRequired;
            java.util.List<DlcRequiredEntry> finalMissing = missing;
            String finalChangelog = changelogText;
            String finalChangelogVersion = changelogVersion;
            Minecraft.getInstance().execute(() -> {
                startupCheckRunning = false;
                LOGGER.info("[dlc-manager] startup check finished in {}ms", System.currentTimeMillis() - startAt);
                if (!(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                    return;
                }
                Runnable flow = () -> {
                    DlcNoticeManager.showFromTitle(screen);
                };
                if (finalHasRequired && !finalMissing.isEmpty()) {
                    flow = wrapRequiredPrompt(screen, finalMissing, flow);
                }
                if (finalHasUpdate && finalUpdateResult != null) {
                    flow = wrapUpdatePrompt(screen, finalUpdateResult, flow);
                }
                if (finalChangelog != null && !finalChangelog.isBlank() && finalChangelogVersion != null && !finalChangelogVersion.isBlank()) {
                    flow = wrapChangelogPrompt(screen, finalChangelog, finalChangelogVersion, flow);
                }
                if (finalHasPackUpdate && finalPackUpdateResult != null) {
                    flow = wrapPackUpdatePrompt(screen, finalPackUpdateResult, flow);
                }
                if (finalHasSelfUpdate && finalSelfUpdateResult != null && !selfUpdatePrompted) {
                    flow = wrapSelfUpdatePrompt(screen, finalSelfUpdateResult, flow);
                }
                flow.run();
            });
        }, "dlc-startup-check").start();
    }

    private static Runnable wrapSelfUpdatePrompt(Screen fallback, DlcSelfUpdateService.SelfUpdateResult result, Runnable next) {
        return () -> {
            if (result == null || !result.hasUpdate) {
                next.run();
                return;
            }
            selfUpdatePrompted = true;
            Screen continuation = createContinuationScreen(fallback, next);
            Minecraft.getInstance().setScreen(new DlcSelfUpdateScreen(continuation, result, true));
        };
    }

    private static Runnable wrapPackUpdatePrompt(Screen fallback, DlcPackReleaseUpdateService.CheckResult result, Runnable next) {
        return () -> {
            if (result == null || !result.hasUpdate()) {
                next.run();
                return;
            }
            Screen continuation = createContinuationScreen(fallback, next);
            Minecraft.getInstance().setScreen(new DlcPackReleaseUpdateScreen(continuation, result, true));
        };
    }

    private static Runnable wrapChangelogPrompt(Screen fallback, String changelog, String changelogVersion, Runnable next) {
        return () -> {
            if (changelog == null || changelog.isBlank() || changelogVersion == null || changelogVersion.isBlank()) {
                next.run();
                return;
            }
            Screen continuation = createContinuationScreen(fallback, next);
            Minecraft.getInstance().setScreen(new DlcUpdateChangelogScreen(continuation, changelog, () -> {
                Path root = DlcPaths.dlcRoot();
                if (root != null) {
                    DlcUpdateVersion version = DlcUpdateVersion.load(root);
                    version.setChangelogShown(changelogVersion);
                    version.save(root);
                }
                Minecraft.getInstance().setScreen(continuation);
            }));
        };
    }

    private static Runnable wrapUpdatePrompt(Screen fallback, UpdateCheckResult result, Runnable next) {
        return () -> {
            if (result == null || !result.hasUpdates()) {
                next.run();
                return;
            }
            DlcUpdateClient.markPrompted();
            Screen continuation = createContinuationScreen(fallback, next);
            Minecraft.getInstance().setScreen(new DlcUpdatePromptScreen(continuation, result, true));
        };
    }

    private static Runnable wrapRequiredPrompt(Screen fallback, List<DlcRequiredEntry> missing, Runnable next) {
        return () -> {
            if (missing == null || missing.isEmpty()) {
                next.run();
                return;
            }
            DlcRequiredManager.markPrompted();
            Screen continuation = createContinuationScreen(fallback, next);
            Minecraft.getInstance().setScreen(new DlcRequiredDownloadScreen(continuation, missing));
        };
    }

    private static Screen createContinuationScreen(Screen fallback, Runnable next) {
        return new Screen(Component.empty()) {
            private boolean started;

            @Override
            protected void init() {
                if (started) {
                    return;
                }
                started = true;
                Minecraft minecraft = Minecraft.getInstance();
                next.run();
                if (minecraft.screen == this) {
                    minecraft.setScreen(fallback);
                }
            }

            @Override
            public boolean shouldCloseOnEsc() {
                return false;
            }
        };
    }

    private static List<DlcUpdateMeta> collectPendingChangelogMeta(String shownVersion, String currentVersion) {
        if (shownVersion == null || shownVersion.isBlank() || currentVersion == null || currentVersion.isBlank()
            || shownVersion.equals(currentVersion)) {
            return List.of();
        }
        List<DlcUpdateMeta> reverse = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String cursor = currentVersion;
        while (cursor != null && !cursor.isBlank() && !cursor.equals(shownVersion) && visited.add(cursor)) {
            DlcUpdateMeta meta = DlcUpdateService.loadMetaForVersion(cursor);
            if (meta == null) {
                break;
            }
            String oldVersion = meta.getDlcOld();
            String newVersion = meta.getDlcNew();
            if (oldVersion == null || oldVersion.isBlank() || newVersion == null || newVersion.isBlank()) {
                break;
            }
            if (!cursor.equals(newVersion)) {
                break;
            }
            reverse.add(meta);
            cursor = oldVersion;
        }
        if (reverse.isEmpty()) {
            return List.of();
        }
        Collections.reverse(reverse);
        return reverse;
    }

    private static String buildPendingChangelogText(List<DlcUpdateMeta> metas, String lang) {
        if (metas == null || metas.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (DlcUpdateMeta meta : metas) {
            if (meta == null) {
                continue;
            }
            String log = meta.resolveChangelog(lang);
            if (log == null || log.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder
                .append(safeVersion(meta.getDlcOld()))
                .append(" -> ")
                .append(safeVersion(meta.getDlcNew()))
                .append("\n")
                .append(log.trim());
        }
        return builder.toString();
    }

    private static String safeVersion(String value) {
        return value == null || value.isBlank() ? "1.0.0" : value;
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
