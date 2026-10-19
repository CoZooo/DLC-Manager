package com.cozooo.dlc_manager.sync

import com.cozooo.dlc_manager.core.DlcFileMatch
import com.cozooo.dlc_manager.core.DlcResourceHistory
import java.io.IOException
import java.nio.file.*
import java.util.function.BooleanSupplier
import java.util.zip.*

object SyncArchive {
    @JvmStatic @Throws(IOException::class) fun size(path: Path): Long {
        if (!Files.isDirectory(path)) return Files.size(path)
        var size = 0L
        Files.walk(path).use { paths ->
            for (child in paths.toList()) {
                check { false }
                if (Files.isSymbolicLink(child)) throw SyncFailure("symbolic_resource")
                if (Files.isRegularFile(child)) size = Math.addExact(size, Files.size(child))
            }
        }
        return size
    }
    @JvmStatic @Throws(IOException::class) fun inventory(root: Path, canceled: BooleanSupplier): List<SyncModel.File> {
        val result = mutableListOf<SyncModel.File>()
        if (!Files.isDirectory(root)) return result
        Files.walk(root).use { paths ->
            for (path in paths.sorted().toList()) {
                check(canceled)
                if (Files.isSymbolicLink(path)) throw SyncFailure("symbolic_resource")
                if (Files.isRegularFile(path)) result.add(SyncModel.File(root.relativize(path).toString().replace('\\', '/'), Files.size(path), DlcFileMatch.hash(path)))
            }
        }
        return result
    }
    @JvmStatic @Throws(IOException::class) fun reusable(root: Path, existing: List<SyncModel.File>, canceled: BooleanSupplier): List<SyncModel.File> {
        val files = existing.associateBy { it.id }
        return inventory(root, canceled).filter { SyncModel.same(it, files[it.id]) }
    }
    private fun child(root: Path, name: String): Path {
        if (name.isBlank() || name.contains('\\') || name.contains(':') || name.startsWith('/') || name.split('/').any { it.isEmpty() || it == "." || it == ".." }) throw SyncFailure("invalid_reuse_path")
        val path = root.resolve(name).normalize()
        if (!path.startsWith(root) || path == root) throw SyncFailure("resource_escape")
        var part: Path? = path
        while (part != null && part.startsWith(root)) {
            if (Files.isSymbolicLink(part)) throw SyncFailure("symbolic_resource")
            part = part.parent
        }
        return path
    }
    @JvmStatic @Throws(IOException::class) fun pack(source: Path, archive: Path, canceled: BooleanSupplier) = pack(source, archive, canceled, emptyList())
    @JvmStatic @Throws(IOException::class) fun pack(source: Path, archive: Path, canceled: BooleanSupplier, reused: List<SyncModel.File>) {
        val omitted = reused.map { it.id }.toSet()
        ZipOutputStream(Files.newOutputStream(archive)).use { output ->
            Files.walk(source).use { paths ->
                output.setLevel(0)
                val buffer = ByteArray(65536)
                for (child in paths.sorted().toList()) {
                    check(canceled)
                    if (child == source) continue
                    if (Files.isSymbolicLink(child)) throw SyncFailure("symbolic_resource")
                    val relative = source.relativize(child).toString().replace('\\', '/')
                    if (Files.isRegularFile(child) && relative in omitted) continue
                    output.putNextEntry(ZipEntry(relative + if (Files.isDirectory(child)) "/" else ""))
                    if (Files.isRegularFile(child)) Files.newInputStream(child).use { input ->
                        while (true) { val count = input.read(buffer); if (count == -1) break; check(canceled); output.write(buffer, 0, count) }
                    }
                    output.closeEntry()
                }
            }
        }
    }
    @JvmStatic @Throws(IOException::class) fun install(archive: Path, target: Path, size: Long, hash: String, journal: SyncModel.Journal, canceled: BooleanSupplier) {
        install(archive, target, size, hash, journal, canceled, emptyList())
    }
    @JvmStatic @Throws(IOException::class) fun install(archive: Path, target: Path, size: Long, hash: String, journal: SyncModel.Journal, canceled: BooleanSupplier, reused: List<SyncModel.File>) {
        if (!Files.exists(target.parent)) journal.capture(target)
        Files.createDirectories(target.parent)
        val staging = Files.createTempDirectory(target.parent, ".dlc-sync-")
        try {
            var received = 0L
            for (file in reused) {
                check(canceled)
                val source = child(target, file.id)
                val destination = child(staging, file.id)
                if (!Files.isRegularFile(source) || Files.size(source) != file.size || DlcFileMatch.fullHash(source) != file.hash) throw SyncFailure("reused_changed", "${file.id}")
                Files.createDirectories(destination.parent); Files.copy(source, destination)
                received = Math.addExact(received, file.size)
                if (received > size) throw SyncFailure("archive_size")
            }
            ZipInputStream(Files.newInputStream(archive)).use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val entry = input.nextEntry ?: break
                    check(canceled)
                    val name = entry.name
                    if (name.contains('\\') || name.contains(':') || name.startsWith('/')) throw SyncFailure("invalid_archive_path")
                    val path = staging.resolve(name).normalize()
                    if (!path.startsWith(staging) || path == staging) throw SyncFailure("archive_escape")
                    if (entry.isDirectory) Files.createDirectories(path)
                    else {
                        Files.createDirectories(path.parent)
                        Files.newOutputStream(path, StandardOpenOption.CREATE_NEW).use { output ->
                            while (true) {
                                val count = input.read(buffer); if (count == -1) break
                                check(canceled); received = Math.addExact(received, count.toLong())
                                if (received > size) throw SyncFailure("archive_size")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }
            if (received != size || DlcFileMatch.fullHash(staging) != hash) throw SyncFailure("directory_integrity")
            check(canceled); journal.capture(target); DlcResourceHistory.delete(target)
            try { Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(staging, target) }
        } finally { DlcResourceHistory.delete(staging) }
    }
    private fun check(canceled: BooleanSupplier) {
        if (canceled.asBoolean || Thread.currentThread().isInterrupted) throw SyncFailure("canceled")
    }
}
