package com.cozooo.dlc_manager.server

import com.cozooo.dlc_manager.core.DlcTmpPaths
import java.io.BufferedWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DlcServerOps {
    private val stampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")

    @JvmStatic
    fun begin(dlcRoot: Path?, gameDir: Path?, name: String?): ServerOpContext {
        val stamp = LocalDateTime.now().format(stampFormat)
        val safeName = if (name == null || name.isBlank()) "op" else name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val root = DlcTmpPaths.resolve(dlcRoot, "server_ops", stamp + "_" + safeName)!!
        val backup = root.resolve("backup")
        val logPath = root.resolve("log.txt")
        try {
            Files.createDirectories(backup)
        } catch (_: IOException) {
        }
        return ServerOpContext(root, backup, logPath, gameDir)
    }

    class ServerOpContext(
        private val root: Path,
        private val backupRoot: Path?,
        private val logPath: Path,
        private val gameDir: Path?,
    ) : AutoCloseable {
        private var writer: BufferedWriter? = null

        fun getRoot(): Path {
            return root
        }

        fun log(message: String?) {
            if (message == null) {
                return
            }
            try {
                if (writer == null) {
                    Files.createDirectories(logPath.parent)
                    writer = Files.newBufferedWriter(logPath, StandardCharsets.UTF_8)
                }
                writer!!.write(message)
                writer!!.newLine()
                writer!!.flush()
            } catch (_: IOException) {
            }
        }

        fun backupIfExists(target: Path?) {
            if (target == null || !Files.exists(target)) {
                return
            }
            val backupPath = resolveBackupPath(target) ?: return
            try {
                if (Files.isDirectory(target)) {
                    copyRecursive(target, backupPath)
                } else {
                    Files.createDirectories(backupPath.parent)
                    Files.copy(target, backupPath, StandardCopyOption.REPLACE_EXISTING)
                }
                log("BACKUP $target")
            } catch (_: IOException) {
            }
        }

        fun deleteRecursive(target: Path?) {
            if (target == null || !Files.exists(target)) {
                return
            }
            try {
                Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
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
                })
                log("DELETE $target")
            } catch (_: IOException) {
            }
        }

        private fun resolveBackupPath(target: Path): Path? {
            if (backupRoot == null) {
                return null
            }
            val normalizedTarget = target.normalize()
            if (gameDir != null) {
                val normalizedRoot = gameDir.normalize()
                if (normalizedTarget.startsWith(normalizedRoot)) {
                    val relative = normalizedRoot.relativize(normalizedTarget)
                    return backupRoot.resolve(relative)
                }
            }
            return backupRoot.resolve(normalizedTarget.fileName)
        }

        override fun close() {
            if (writer != null) {
                try {
                    writer!!.close()
                } catch (_: IOException) {
                }
                writer = null
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyRecursive(source: Path, target: Path) {
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relative = source.relativize(dir)
                Files.createDirectories(target.resolve(relative))
                return FileVisitResult.CONTINUE
            }

            @Throws(IOException::class)
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relative = source.relativize(file)
                val dest = target.resolve(relative)
                Files.createDirectories(dest.parent)
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
