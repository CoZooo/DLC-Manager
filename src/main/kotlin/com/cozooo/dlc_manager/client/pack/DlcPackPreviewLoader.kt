package com.cozooo.dlc_manager.client.pack

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

class DlcPackPreviewLoader @JvmOverloads constructor(private val worker: ExecutorService = WORKER) {
    private val generation = AtomicLong()
    private var pending: Future<*>? = null

    fun load(gameDir: Path, dlcRoot: Path, orderPath: Path, owner: Executor, apply: Consumer<Snapshot>, failed: Consumer<Exception>) {
        cancel()
        val request = generation.get()
        pending = worker.submit {
            try {
                if (generation.get() != request) return@submit
                var uninstalled = 0
                var compatibilityError: Exception? = null
                try {
                    uninstalled = DlcPackUpdateCoordinator(gameDir, dlcRoot).uninstallIncompatibleForCurrentVersion().size
                } catch (error: Exception) {
                    compatibilityError = error
                }
                if (generation.get() != request) return@submit
                val installed = HashMap<String, DlcPackMeta>()
                val installedRoot = dlcRoot.resolve("tmp/pack")
                if (Files.isDirectory(installedRoot)) {
                    Files.list(installedRoot).use { directories ->
                        directories.filter(Files::isDirectory).forEach { dir ->
                            DlcPackMeta.load(dir.resolve("meta.dc"))?.let { installed[it.identifier] = it }
                        }
                    }
                }
                val previews = ArrayList<Preview>()
                val archives = DlcPackArchive.findArchives(dlcRoot)
                    .filter { DlcPackArchive.isArchiveFileName(it.fileName.toString()) }
                    .sortedBy { it.fileName.toString().lowercase(Locale.ROOT) }
                for (archive in archives) {
                    if (generation.get() != request) return@submit
                    readPreview(archive, dlcRoot)?.let(previews::add)
                }
                val snapshot = Snapshot(previews.toList(), installed.toMap(), DlcPackOrder.load(orderPath).toList(), uninstalled, compatibilityError)
                owner.execute { if (generation.get() == request) apply.accept(snapshot) }
            } catch (error: Exception) {
                owner.execute { if (generation.get() == request) failed.accept(error) }
            }
        }
    }

    fun cancel() {
        generation.incrementAndGet()
        pending?.cancel(false)
        pending = null
    }

    class Snapshot(
        @JvmField val previews: List<Preview>,
        @JvmField val installed: Map<String, DlcPackMeta>,
        @JvmField val savedOrder: List<String>,
        @JvmField val uninstalled: Int,
        @JvmField val compatibilityError: Exception?,
    )

    class Preview(
        @JvmField val archive: Path,
        @JvmField val metadata: DlcPackMetadata,
        @JvmField val icon: ByteArray?,
    )

    companion object {
        private val WORKER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "dlc-pack-preview").apply { isDaemon = true }
        }

        private fun readPreview(archive: Path, dlcRoot: Path): Preview? {
            var staging: Path? = null
            return try {
                val extracted = DlcPackArchive.extractPreviewToStaging(archive, dlcRoot)
                staging = extracted.stagingDir
                val result = DlcPackPackage.inspectPreview(extracted)
                val pack = result.pack
                if (!result.valid || pack == null) return null
                val icon = try { pack.iconFile?.let(Files::readAllBytes) } catch (_: Exception) { null }
                Preview(archive.toAbsolutePath().normalize(), pack.metadata, icon)
            } catch (_: Exception) {
                null
            } finally {
                DlcPackArchive.deleteRecursive(staging)
            }
        }
    }
}
