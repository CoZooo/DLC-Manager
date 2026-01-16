package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen;
import com.cozooo.dlc_manager.client.screen.DlcPathConfigScreen;
import com.cozooo.dlc_manager.client.notice.DlcNoticeManager;
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager;
import com.cozooo.dlc_manager.client.required.DlcRequiredManager;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.repository.PackRepository;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
            () -> (modContainer, parent) -> new DlcPathConfigScreen(parent));
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
            if (DlcRequiredManager.maybeShowFromTitle(screen)) {
                return;
            }
            DlcNoticeManager.showFromTitle(screen);
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
}
