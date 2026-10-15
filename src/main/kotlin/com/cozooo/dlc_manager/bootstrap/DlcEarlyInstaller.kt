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
import java.util.function.BooleanSupplier
import java.util.function.Consumer

object DlcEarlyInstaller {
    data class Result(val checkedCount: Int, val downloadedCount: Int, val installedCount: Int)

    enum class Stage {
        SCANNING, PLANNED, DOWNLOADING, INSTALLING, ENTRY_COMPLETE, VERIFYING, COMPLETE, FAILED
    }

    data class Progress(val stage: Stage, val completedCount: Int, val totalCount: Int,
                        val resourceName: String, val downloadedBytes: Long, val totalBytes: Long)

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun install(gameDir: Path, configDir: Path, loader: String, minecraftVersion: String, server: Boolean, log: Consumer<String>): Result =
        install(gameDir, configDir, loader, minecraftVersion, server, log, null, BooleanSupplier { false })

    @JvmStatic
    @Synchronized
    @Throws(IOException::class, InterruptedException::class)
    fun install(gameDir: Path, configDir: Path, loader: String, minecraftVersion: String, server: Boolean,
                log: Consumer<String>, progress: Consumer<Progress>?, cancelled: BooleanSupplier?): Result {
        val owner = Thread.currentThread()
        val reporter = Reporter(log, progress)
        val previous = DlcPlatformServices.get()
        try {
            checkCancelled(owner, cancelled)
            DlcPlatformServices.setProvider(EarlyProvider(gameDir.toAbsolutePath().normalize(), configDir.toAbsolutePath().normalize(), loader, minecraftVersion, server))
            advance(reporter, Stage.SCANNING, owner, cancelled)
            reporter.log("Scanning required resources")
            if (server) DlcConfig.loadServer() else DlcConfig.loadClient()
            checkCancelled(owner, cancelled)
            com.cozooo.dlc_manager.client.update.DlcPendingUpdate.process({ reporter.log(it) }, false)
            validateConfigs(DlcConfig.getDlcRoots())
            checkCancelled(owner, cancelled)
            val entries = DlcRequiredRuntime.loadAllEntries()
            reporter.plan(entries.size)
            advance(reporter, Stage.PLANNED, owner, cancelled)
            DlcRequiredService.reconcileEntries(entries)
            var downloaded = 0
            var installed = 0
            for (entry in entries) {
                checkCancelled(owner, cancelled)
                val progressEntry = reporter.beginEntry(entry.resolveDisplayName())
                try {
                    if (!DlcRequiredService.isDownloaded(entry)) {
                        advance(reporter, Stage.DOWNLOADING, owner, cancelled)
                        reporter.log("Downloading required resource: ${entry.resolveDisplayName()}")
                        DlcRequiredService.downloadOne(entry, { bytes, total -> reporter.download(progressEntry, bytes, total) },
                            { cancellationRequested(owner, cancelled) })
                        checkCancelled(owner, cancelled)
                        reporter.flushDownload()
                        downloaded++
                    }
                    advance(reporter, Stage.INSTALLING, owner, cancelled)
                    reporter.log("Applying required resource: ${entry.resolveDisplayName()}")
                    if (DlcRequiredService.installOne(entry)) installed++
                    checkCancelled(owner, cancelled)
                    reporter.entryComplete()
                    checkCancelled(owner, cancelled)
                } catch (ex: IOException) {
                    if (cancellationRequested(owner, cancelled)) throw InterruptedException("Required resource installation interrupted").also { it.initCause(ex) }
                    throw IOException("Cannot install required resource '${entry.getIdentifier()}' from ${entry.getConfigPath()}", ex)
                } catch (ex: RuntimeException) {
                    if (cancellationRequested(owner, cancelled)) throw InterruptedException("Required resource installation interrupted").also { it.initCause(ex) }
                    throw IOException("Cannot install required resource '${entry.getIdentifier()}' from ${entry.getConfigPath()}", ex)
                }
            }
            advance(reporter, Stage.VERIFYING, owner, cancelled)
            verifyEntries(entries)
            checkCancelled(owner, cancelled)
            reporter.log("Required resources ready: ${entries.size} checked, $downloaded downloaded, $installed installed")
            checkCancelled(owner, cancelled)
            reporter.stage(Stage.COMPLETE)
            return Result(entries.size, downloaded, installed)
        } catch (ex: Throwable) {
            reporter.failed()
            throw ex
        } finally {
            DlcPlatformServices.setProvider(previous)
        }
    }

    private fun cancellationRequested(owner: Thread, cancelled: BooleanSupplier?): Boolean =
        owner.isInterrupted || Thread.currentThread().isInterrupted || cancelled?.asBoolean == true

    private fun checkCancelled(owner: Thread, cancelled: BooleanSupplier?) {
        if (cancellationRequested(owner, cancelled)) throw InterruptedException("Required resource installation interrupted")
    }

    private fun advance(reporter: Reporter, stage: Stage, owner: Thread, cancelled: BooleanSupplier?) {
        checkCancelled(owner, cancelled)
        reporter.stage(stage)
        checkCancelled(owner, cancelled)
    }

    private class Reporter(private val log: Consumer<String>, private var observer: Consumer<Progress>?) {
        private var current = Progress(Stage.SCANNING, 0, -1, "", 0, -1)
        private var lastBytesNanos = 0L
        private var lastLogNanos = 0L
        private var entryGeneration = 0
        private var terminal = false

        @Synchronized fun plan(total: Int) {
            current = Progress(current.stage, 0, total, "", 0, -1)
        }

        @Synchronized fun beginEntry(name: String?): Int {
            current = Progress(current.stage, current.completedCount, current.totalCount, name.orEmpty(), 0, -1)
            lastBytesNanos = 0
            lastLogNanos = 0
            return ++entryGeneration
        }

        @Synchronized fun stage(stage: Stage) {
            if (terminal) return
            val summary = stage == Stage.VERIFYING || stage == Stage.COMPLETE
            current = Progress(stage, current.completedCount, current.totalCount, if (summary) "" else current.resourceName,
                if (summary) 0 else current.downloadedBytes, if (summary) -1 else current.totalBytes)
            terminal = stage == Stage.COMPLETE || stage == Stage.FAILED
            publish()
        }

        @Synchronized fun download(entry: Int, bytes: Long, total: Long) {
            if (terminal || entry != entryGeneration || current.stage != Stage.DOWNLOADING
                || Thread.currentThread().isInterrupted) return
            if (bytes == 0L && total == 1L) return
            val downloaded = if (bytes <= 0) 0 else maxOf(current.downloadedBytes, bytes)
            current = Progress(Stage.DOWNLOADING, current.completedCount, current.totalCount, current.resourceName,
                downloaded, if (total >= 0) total else -1)
            val now = System.nanoTime()
            if (lastBytesNanos == 0L || now - lastBytesNanos >= 100_000_000L) {
                lastBytesNanos = now
                publish()
            }
            if (lastLogNanos == 0L || now - lastLogNanos >= 1_000_000_000L) {
                lastLogNanos = now
                logBytes()
            }
        }

        @Synchronized fun flushDownload() {
            if (terminal || current.stage != Stage.DOWNLOADING) return
            publish()
            logBytes()
        }

        @Synchronized fun entryComplete() {
            if (terminal) return
            current = Progress(Stage.ENTRY_COMPLETE, current.completedCount + 1, current.totalCount,
                current.resourceName, current.downloadedBytes, current.totalBytes)
            publish()
        }

        @Synchronized fun failed() {
            if (terminal) return
            stage(Stage.FAILED)
            log("Required resource installation failed; startup cannot continue")
        }

        fun log(message: String) {
            try { log.accept(message) }
            catch (_: RuntimeException) { }
        }

        private fun logBytes() {
            log("${current.resourceName}: ${current.downloadedBytes}${if (current.totalBytes >= 0) "/${current.totalBytes}" else ""} bytes")
        }

        private fun publish() {
            val listener = observer ?: return
            try { listener.accept(current) }
            catch (ex: RuntimeException) {
                observer = null
                log("Progress observer unavailable; continuing with bootstrap logging (${ex.javaClass.simpleName})")
            }
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
