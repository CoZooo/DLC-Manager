package com.cozooo.dlc_manager.bootstrap

import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntime
import com.cozooo.dlc_manager.client.required.DlcRequiredService
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcDcFormatUtil
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

object DlcEarlyInstaller {
    data class Result(val checkedCount: Int, val downloadedCount: Int, val installedCount: Int)

    @JvmStatic
    @Synchronized
    @Throws(IOException::class, InterruptedException::class)
    fun install(gameDir: Path, configDir: Path, loader: String, minecraftVersion: String, server: Boolean, log: Consumer<String>): Result {
        val previous = DlcPlatformServices.get()
        DlcPlatformServices.setProvider(EarlyProvider(gameDir.toAbsolutePath().normalize(), configDir.toAbsolutePath().normalize(), loader, minecraftVersion, server))
        try {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Required resource installation interrupted")
            if (server) DlcConfig.loadServer() else DlcConfig.loadClient()
            validateConfigs(DlcConfig.getDlcRoots())
            val entries = DlcRequiredRuntime.loadAllEntries()
            var downloaded = 0
            var installed = 0
            for (entry in entries) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("Required resource installation interrupted")
                try {
                    if (!DlcRequiredService.isDownloaded(entry)) {
                        log.accept("Downloading required resource: ${entry.resolveDisplayName()}")
                        val lastProgress = AtomicLong()
                        DlcRequiredService.downloadOne(entry, { bytes, total ->
                            val now = System.nanoTime()
                            if (now - lastProgress.get() >= 1_000_000_000L) {
                                lastProgress.set(now)
                                log.accept("${entry.resolveDisplayName()}: $bytes${if (total > 0) "/$total" else ""} bytes")
                            }
                        }, { Thread.currentThread().isInterrupted })
                        downloaded++
                    }
                    if (DlcRequiredRuntime.entryNeedsApply(entry) && DlcRequiredService.installOne(entry)) installed++
                } catch (ex: IOException) {
                    throw IOException("Cannot install required resource '${entry.getIdentifier()}' from ${entry.getConfigPath()}", ex)
                } catch (ex: RuntimeException) {
                    throw IOException("Cannot install required resource '${entry.getIdentifier()}' from ${entry.getConfigPath()}", ex)
                }
            }
            verifyEntries(entries)
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Required resource installation interrupted")
            log.accept("Required resources ready: ${entries.size} checked, $downloaded downloaded, $installed installed")
            return Result(entries.size, downloaded, installed)
        } finally {
            DlcPlatformServices.setProvider(previous)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun verifyInstalled() {
        validateConfigs(DlcConfig.getDlcRoots())
        verifyEntries(DlcRequiredRuntime.loadAllEntries())
    }

    private fun verifyEntries(entries: List<DlcRequiredEntry>) {
        if (DlcRequiredService.collectMissingOrApply(entries).isNotEmpty()) {
            throw IOException("Required resource installation incomplete; game loading cannot continue")
        }
        for (entry in entries) {
            val fileName = DlcRequiredService.resolveExistingFileName(entry)
            val source = entry.resolveRequiredPath(fileName)
            if (source == null || !Files.isRegularFile(source)) throw IOException("Required resource is not a regular file: $source")
            for (target in entry.resolveAppliedTargets()) {
                val name = target.fileName?.toString() ?: ""
                val file = if (name.endsWith(".jar") || name.endsWith(".zip")) target else target.resolve(fileName)
                if (!Files.isRegularFile(file)) throw IOException("Required resource target is not installed: $file")
            }
        }
    }

    private fun validateConfigs(roots: List<Path>) {
        for (root in roots) {
            val required = root.resolve("required")
            if (!Files.exists(required)) continue
            if (!Files.isDirectory(required)) throw IOException("Required resource directory is not a directory: $required")
            Files.newDirectoryStream(required).use { paths ->
                for (path in paths) {
                    val name = path.fileName.toString()
                    if (!DlcDcFormatUtil.isDcName(name) || DlcDcFormatUtil.stripConfigExtension(name).equals("notice", ignoreCase = true)) continue
                    if (DlcRequiredLoader.loadSingle(path, root) == null) throw IOException("Invalid required resource configuration: $path")
                }
            }
        }
    }

    private class EarlyProvider(private val game: Path, private val config: Path, private val loader: String, private val version: String, private val server: Boolean) : DlcPlatformServices.Provider {
        override fun gameDir() = game
        override fun configDir() = config
        override fun loaderName() = loader
        override fun mcVersion() = version
        override fun isDedicatedServer() = server
        override fun isModLoaded(modId: String?) = false
        override fun languageCode() = DlcPlatformServices.readLanguageFromOptions(game) ?: "en_us"
        override fun modVersion(modId: String?) = ""
        override fun modFilePath(modId: String?): Path? = null
        override fun playerName() = ""
        override fun playerUuid() = ""
    }
}
