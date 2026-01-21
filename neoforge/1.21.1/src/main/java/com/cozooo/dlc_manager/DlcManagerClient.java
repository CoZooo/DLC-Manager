package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen;
import com.cozooo.dlc_manager.client.screen.DlcPathConfigScreen;
import com.cozooo.dlc_manager.client.notice.DlcNoticeManager;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult;
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.repository.PackRepository;
import com.cozooo.dlc_manager.client.update.DlcUpdateClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import java.nio.file.Path;

public class DlcManagerClient {
    private static volatile boolean startupCheckRunning = false;
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
                if (DlcRemoteManager.maybeShowFromManager(managerScreen)) {
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

    private static void shiftButtonRow(Screen screen, int deltaY, String... keys) {
        for (String key : keys) {
            AbstractWidget widget = findButtonByKey(screen, key);
            if (widget != null) {
                widget.setY(widget.getY() - deltaY);
            }
        }
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
            boolean updateManagerOn = DlcPathConfig.isUpdateManagerOn();
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
            Minecraft.getInstance().execute(() -> {
                startupCheckRunning = false;
                LOGGER.info("[dlc-manager] startup check finished in {}ms", System.currentTimeMillis() - startAt);
                if (!(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.TitleScreen)) {
                    return;
                }
                Runnable continueFlow = () -> {
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
}
