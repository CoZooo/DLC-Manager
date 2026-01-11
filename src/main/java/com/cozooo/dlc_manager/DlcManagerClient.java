package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.client.screen.DlcMaLiLibScreen;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = DlcManager.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DlcManagerClient {
    public static void init(IEventBus modBus) {
        modBus.addListener(DlcManagerClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // No-op for now.
    }

    @Mod.EventBusSubscriber(modid = DlcManager.MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {
        private static boolean resourcePackSynced = false;

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
                Minecraft.getInstance().setScreen(new DlcMaLiLibScreen(screen));
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
                button.setX(x);
                button.setY(y);
                button.setWidth(w);
                button.setHeight(h);
            }
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
            minecraft.execute(ClientEvents::syncEnabledResourcePacks);
        }

        private static void syncEnabledResourcePacks() {
            Minecraft minecraft = Minecraft.getInstance();
            PackRepository repo = minecraft.getResourcePackRepository();
            try {
                repo.reload();
                java.util.Set<String> selected = new java.util.LinkedHashSet<>(repo.getSelectedIds());
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
                    }
                    if (packId != null) {
                        selected.add(packId);
                    }
                }
                repo.setSelected(selected);
                minecraft.options.resourcePacks = new java.util.ArrayList<>(selected);
                minecraft.options.save();
                minecraft.reloadResourcePacks();
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
}
