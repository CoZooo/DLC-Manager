package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcPathUtil
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object DlcPackModrinthManifest {
    fun interface Downloader {
        @Throws(IOException::class)
        fun download(url: String, directory: Path, name: String, size: Long, progress: DlcDownloadService.ProgressListener?, cancel: DlcDownloadService.CancelChecker?): Path
    }
    @JvmStatic @Throws(IOException::class)
    fun read(path: Path): List<FileRef> {
        try {
            Files.newBufferedReader(path).use { reader ->
                val index = JsonParser.parseReader(reader).asJsonObject
                if (index["formatVersion"].asString != "1" || index["game"].asString != "minecraft") throw IOException("Unsupported Modrinth pack format or game")
                val paths = HashSet<String>()
                val files = index.getAsJsonArray("files").map { element ->
                    val file = element.asJsonObject
                    val relative = file["path"].asString.replace('\\', '/')
                    if (relative.startsWith('/') || relative.endsWith('/') || ':' in relative || '\u0000' in relative || relative.split('/').any { it.isEmpty() || it == "." || it == ".." } || !paths.add(relative.lowercase(Locale.ROOT))) throw IOException("Invalid or duplicate Modrinth file path: $relative")
                    val hashes = file.getAsJsonObject("hashes")
                    val sha1 = hashes["sha1"].asString
                    val sha512 = hashes["sha512"].asString
                    if (!sha1.matches(Regex("(?i)[0-9a-f]{40}")) || !sha512.matches(Regex("(?i)[0-9a-f]{128}"))) throw IOException("Invalid Modrinth file hashes: $relative")
                    val size = file["fileSize"].asBigDecimal.longValueExact()
                    if (size < 0) throw IOException("Invalid Modrinth file size: $relative")
                    val downloads = file.getAsJsonArray("downloads").map {
                        val uri = URI.create(it.asString)
                        if (!uri.scheme.equals("https", true) || uri.host == null || uri.userInfo != null || uri.fragment != null) throw IOException("Invalid Modrinth download URL: $relative")
                        uri.toASCIIString()
                    }
                    if (downloads.isEmpty()) throw IOException("Missing Modrinth download URLs: $relative")
                    val env = if (file.has("env")) file.getAsJsonObject("env") else null
                    FileRef(relative, sha1, sha512, size, java.util.List.copyOf(downloads), environment(env, "client"), environment(env, "server"))
                }
                return java.util.List.copyOf(files)
            }
        } catch (error: RuntimeException) {
            throw IOException("Invalid modrinth.index.json", error)
        }
    }

    private fun environment(env: JsonObject?, side: String): String {
        val value = if (env == null) "required" else env[side].asString
        if (value !in setOf("required", "optional", "unsupported")) throw IOException("Invalid Modrinth environment: $side")
        return value
    }

    @JvmStatic
    fun filesForEnvironment(pack: DlcPackPackage): List<FileRef> {
        val server = DlcPlatformServices.isDedicatedServer()
        return pack.modrinthFiles.filter { (if (server) it.server else it.client) != "unsupported" }
            .filter { !DlcPackInstallPlan.isMetadataFile(it.path) && !DlcPackInstallPlan.isIgnoredModpackJarPath(it.path) }
    }

    @JvmStatic
    fun resolveManifestTargets(pack: DlcPackPackage): List<DlcPackInstallPlan.FileEntry> = filesForEnvironment(pack).filter { !hasOverride(pack, it.path) }
        .map { DlcPackInstallPlan.FileEntry(pack.manifestFile ?: pack.root, it.path) }

    @JvmStatic @Throws(IOException::class, InterruptedException::class)
    fun downloadManifestFiles(pack: DlcPackPackage, staging: Path, packDir: Path, cancel: DlcDownloadService.CancelChecker?, progress: DlcPackInstaller.ProgressListener?): List<DlcPackInstallPlan.FileEntry> {
        return downloadManifestFiles(pack, staging, packDir, cancel, progress, DlcDownloadService::downloadKnown)
    }

    @JvmStatic @Throws(IOException::class, InterruptedException::class)
    fun downloadManifestFiles(pack: DlcPackPackage, staging: Path, packDir: Path, cancel: DlcDownloadService.CancelChecker?, progress: DlcPackInstaller.ProgressListener?, downloader: Downloader): List<DlcPackInstallPlan.FileEntry> {
        val files = filesForEnvironment(pack).filter { !hasOverride(pack, it.path) }
        if (files.isEmpty()) return emptyList()
        val completed = AtomicInteger()
        val worker = Executors.newFixedThreadPool(DlcConfig.getMaxConcurrentDownloads().coerceIn(1, files.size))
        try {
            val tasks = files.mapIndexed { index, file -> Callable {
                checkCanceled(cancel)
                val cached = DlcPathUtil.resolveInside(packDir.resolve("mr_cache"), file.path)
                if (!verifyFile(cached, file)) {
                    var failure: IOException? = null
                    var downloaded = false
                    for (url in file.downloads) {
                        checkCanceled(cancel)
                        try {
                            val output = downloader.download(url, staging.resolve("mr_manifest").resolve(index.toString()),
                                Path.of(file.path).fileName.toString(), file.fileSize,
                                { done, total -> progress?.onProgress("Downloading ${file.path}", (completed.get() + if (total > 0) (done.toFloat() / total).coerceAtMost(1f) else 0f) / files.size) }, cancel)
                            if (!verifyFile(output, file)) throw IOException("Modrinth file integrity check failed: ${file.path}")
                            Files.createDirectories(cached.parent)
                            Files.move(output, cached, StandardCopyOption.REPLACE_EXISTING)
                            downloaded = true
                            break
                        } catch (error: DlcDownloadService.DownloadCanceledException) { throw error }
                        catch (error: IOException) { failure = error }
                    }
                    if (!downloaded) throw failure ?: IOException("Failed to download ${file.path}")
                }
                checkCanceled(cancel)
                val done = completed.incrementAndGet()
                progress?.onProgress("Resolved ${file.path}", done.toFloat() / files.size)
                DlcPackInstallPlan.FileEntry(cached, file.path)
            } }
            return worker.invokeAll(tasks).map { future ->
                try { future.get() } catch (error: ExecutionException) {
                    val cause = error.cause
                    if (cause is IOException) throw cause
                    throw IOException("Failed to download Modrinth pack files", cause)
                }
            }
        } finally {
            worker.shutdownNow()
            var interrupted = false
            while (!worker.isTerminated) {
                try { worker.awaitTermination(1, TimeUnit.SECONDS) }
                catch (_: InterruptedException) { interrupted = true }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun hasOverride(pack: DlcPackPackage, relative: String) = pack.installRoots().any { Files.isRegularFile(it.resolve(relative)) }
    private fun checkCanceled(cancel: DlcDownloadService.CancelChecker?) {
        if (Thread.currentThread().isInterrupted || cancel?.isCanceled() == true) throw DlcDownloadService.DownloadCanceledException("canceled")
    }

    @JvmStatic @Throws(IOException::class)
    fun verifyFile(path: Path, file: FileRef): Boolean = Files.isRegularFile(path) && Files.size(path) == file.fileSize && file.sha512.equals(DlcDownloadUtil.computeHashHex(path, "SHA-512"), true) && file.sha1.equals(DlcDownloadUtil.computeHashHex(path, "SHA-1"), true)

    class FileRef(@JvmField val path: String, @JvmField val sha1: String, @JvmField val sha512: String, @JvmField val fileSize: Long,
        downloads: List<String>, @JvmField val client: String, @JvmField val server: String) {
        @JvmField val downloads: List<String> = java.util.List.copyOf(downloads)
    }
}
