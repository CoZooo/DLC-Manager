package com.cozooo.dlc_manager.world;

import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.server.DlcServerApi;
import com.cozooo.dlc_manager.server.DlcServerDownloadService;
import com.cozooo.dlc_manager.server.DlcServerService;
import com.cozooo.dlc_manager.server.DlcServerUpdateNotifier;
import com.mojang.logging.LogUtils;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DlcWorldSync {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private DlcWorldSync() {
    }

    public static void register() {
        LifecycleEvent.SERVER_STARTED.register(server -> {
            applyDatapacks(server);
            if (server.isDedicatedServer()) {
                DlcPathConfig.loadServer();
                DlcServerService.updateServerStatus(server);
                DlcServerApi.start(server);
                DlcServerService.start();
                startServerRequiredDownload(server);
            }
        });
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            DlcServerApi.stop();
            DlcServerService.stop();
        });
        PlayerEvent.PLAYER_JOIN.register(DlcServerUpdateNotifier::onPlayerJoin);
    }

    private static void startServerRequiredDownload(MinecraftServer server) {
        new Thread(() -> {
            DlcServerDownloadService.DownloadResult result = DlcServerDownloadService.downloadAndApplyRequired(DlcPathConfig.getDlcRoot());
            if (result != null && result.didWork()) {
                LOGGER.info("[dlc-manager] server required download applied, restarting server");
                server.halt(false);
            }
        }, "dlc-manager-server-required").start();
    }

    private static void applyDatapacks(MinecraftServer server) {
        Map<DlcType, List<DlcItem>> grouped = new DlcScanner().scan();
        List<DlcItem> dataPacks = grouped.get(DlcType.DATA_PACK);
        if (dataPacks == null || dataPacks.isEmpty()) {
            return;
        }

        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path datapacksDir = worldRoot.resolve("datapacks");
        try {
            Files.createDirectories(datapacksDir);
        } catch (IOException ignored) {
            return;
        }

        for (DlcItem item : dataPacks) {
            Path target = datapacksDir.resolve(item.getSourcePath().getFileName().toString());
            try {
                if (item.isEnabled()) {
                    copyRecursive(item.getSourcePath(), target);
                } else {
                    deleteRecursive(target);
                }
            } catch (IOException ignored) {
            }
        }

        updateLevelDat(worldRoot, dataPacks);
    }

    private static void updateLevelDat(Path worldRoot, List<DlcItem> dataPacks) {
        Path levelDat = worldRoot.resolve("level.dat");
        if (!Files.exists(levelDat)) {
            return;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(levelDat.toFile());
            CompoundTag data = root.getCompound("Data");
            CompoundTag dataPacksTag = data.getCompound("DataPacks");
            ListTag enabled = dataPacksTag.getList("Enabled", 8);
            ListTag disabled = dataPacksTag.getList("Disabled", 8);

            Set<String> enabledSet = new HashSet<>();
            for (int i = 0; i < enabled.size(); i++) {
                enabledSet.add(enabled.getString(i));
            }
            Set<String> disabledSet = new HashSet<>();
            for (int i = 0; i < disabled.size(); i++) {
                disabledSet.add(disabled.getString(i));
            }

            for (DlcItem item : dataPacks) {
                String packId = "file/" + item.getSourcePath().getFileName().toString();
                if (item.isEnabled()) {
                    enabledSet.add(packId);
                    disabledSet.remove(packId);
                } else {
                    enabledSet.remove(packId);
                    disabledSet.add(packId);
                }
            }

            ListTag newEnabled = new ListTag();
            for (String entry : enabledSet) {
                newEnabled.add(StringTag.valueOf(entry));
            }
            ListTag newDisabled = new ListTag();
            for (String entry : disabledSet) {
                newDisabled.add(StringTag.valueOf(entry));
            }

            dataPacksTag.put("Enabled", newEnabled);
            dataPacksTag.put("Disabled", newDisabled);
            data.put("DataPacks", dataPacksTag);
            root.put("Data", data);

            NbtIo.writeCompressed(root, levelDat.toFile());
        } catch (IOException ignored) {
        }
    }

    private static void copyRecursive(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    Path relative = source.relativize(dir);
                    Files.createDirectories(target.resolve(relative));
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    Path relative = source.relativize(file);
                    Files.copy(file, target.resolve(relative), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } else {
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
}
