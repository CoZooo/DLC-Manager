package com.cozooo.dlc_manager.world

import com.cozooo.dlc_manager.bootstrap.DlcStartupRequirements
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcScanner
import com.cozooo.dlc_manager.core.DlcType
import com.cozooo.dlc_manager.server.DlcServerApi
import com.cozooo.dlc_manager.server.DlcServerDownloadService
import com.cozooo.dlc_manager.server.DlcServerService
import com.cozooo.dlc_manager.server.DlcServerUpdateNotifier
import com.mojang.logging.LogUtils
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.event.events.common.PlayerEvent
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

object DlcWorldSync {
    private val LOGGER = LogUtils.getLogger()

    @JvmStatic
    fun register() {
        LifecycleEvent.SERVER_STARTED.register { server ->
            applyDatapacks(server)
            if (server.isDedicatedServer) {
                DlcConfig.loadServer()
                DlcServerService.updateServerStatus(server)
                DlcServerApi.start(server)
                DlcServerService.start()
                if (!DlcStartupRequirements.usesEarlyInstallation()) startServerRequiredDownload(server)
            }
        }
        LifecycleEvent.SERVER_STOPPING.register {
            DlcServerApi.stop()
            DlcServerService.stop()
        }
        PlayerEvent.PLAYER_JOIN.register(DlcServerUpdateNotifier::onPlayerJoin)
    }

    private fun startServerRequiredDownload(server: MinecraftServer) {
        Thread({
            var didWork = false
            for (root in DlcConfig.getDlcRoots()) {
                val result = DlcServerDownloadService.downloadAndApplyRequired(root)
                if (result?.success != true) {
                    LOGGER.error("[dlc-manager] required resources could not be installed; stopping server: {}", result?.error ?: "no result")
                    server.halt(false)
                    return@Thread
                }
                didWork = didWork || result.didWork()
            }
            if (didWork) {
                LOGGER.info("[dlc-manager] server required download applied, restarting server")
                server.halt(false)
            }
        }, "dlc-manager-server-required").start()
    }

    private fun applyDatapacks(server: MinecraftServer) {
        val grouped = DlcScanner().scan()
        val dataPacks = grouped[DlcType.DATA_PACK]
        if (dataPacks.isNullOrEmpty()) {
            return
        }

        val worldRoot = server.getWorldPath(LevelResource.ROOT)
        val datapacksDir = worldRoot.resolve("datapacks")
        try {
            Files.createDirectories(datapacksDir)
        } catch (_: IOException) {
            return
        }

        for (candidate in dataPacks) {
            val item = candidate ?: continue
            val source = item.getSourcePath() ?: continue
            val target = datapacksDir.resolve(source.fileName.toString())
            try {
                if (item.isEnabled()) {
                    copyRecursive(source, target)
                } else {
                    deleteRecursive(target)
                }
            } catch (_: IOException) {
            }
        }

        updateLevelDat(worldRoot, dataPacks)
    }

    private fun updateLevelDat(worldRoot: Path, dataPacks: List<DlcItem?>) {
        //? if >=26.1 {
        //return
        //?} else {
        val levelDat = worldRoot.resolve("level.dat")
        if (!Files.exists(levelDat)) {
            return
        }

        try {
            val root = DlcNbtCompat.readCompressed(levelDat)
            val data = root.getCompound("Data")
            val dataPacksTag = data.getCompound("DataPacks")
            val enabled = dataPacksTag.getList("Enabled", 8)
            val disabled = dataPacksTag.getList("Disabled", 8)

            val enabledSet = HashSet<String>()
            for (i in 0 until enabled.size) {
                enabledSet.add(enabled.getString(i))
            }
            val disabledSet = HashSet<String>()
            for (i in 0 until disabled.size) {
                disabledSet.add(disabled.getString(i))
            }

            for (candidate in dataPacks) {
                val item = candidate ?: continue
                val source = item.getSourcePath() ?: continue
                val packId = "file/" + source.fileName.toString()
                if (item.isEnabled()) {
                    enabledSet.add(packId)
                    disabledSet.remove(packId)
                } else {
                    enabledSet.remove(packId)
                    disabledSet.add(packId)
                }
            }

            val newEnabled = ListTag()
            for (entry in enabledSet) {
                newEnabled.add(StringTag.valueOf(entry))
            }
            val newDisabled = ListTag()
            for (entry in disabledSet) {
                newDisabled.add(StringTag.valueOf(entry))
            }

            dataPacksTag.put("Enabled", newEnabled)
            dataPacksTag.put("Disabled", newDisabled)
            data.put("DataPacks", dataPacksTag)
            root.put("Data", data)

            DlcNbtCompat.writeCompressed(root, levelDat)
        } catch (_: IOException) {
        }
        //?}
    }

    @Throws(IOException::class)
    private fun copyRecursive(source: Path, target: Path) {
        if (Files.isDirectory(source)) {
            Files.walkFileTree(
                source,
                object : SimpleFileVisitor<Path>() {
                    @Throws(IOException::class)
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val relative = source.relativize(dir)
                        Files.createDirectories(target.resolve(relative))
                        return FileVisitResult.CONTINUE
                    }

                    @Throws(IOException::class)
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val relative = source.relativize(file)
                        Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @Throws(IOException::class)
    private fun deleteRecursive(path: Path) {
        if (!Files.exists(path)) {
            return
        }
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                @Throws(IOException::class)
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                @Throws(IOException::class)
                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }
}
