package com.cozooo.dlc_manager.bootstrap

import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.HexFormat
import java.util.jar.JarFile

object DlcModPayload {
    const val RESOURCE = "META-INF/dlc-manager/mod.jar"
    private var modFile: Path? = null
    private var development = false

    @JvmStatic
    @JvmOverloads
    @Synchronized
    @Throws(IOException::class)
    fun prepare(game: Path, developmentLaunch: Boolean = false) {
        val cache = game.resolve(".dlc-manager/runtime")
        Files.createDirectories(cache)
        val temp = Files.createTempFile(cache, "payload-", ".tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            DlcModPayload::class.java.getResourceAsStream("/$RESOURCE").use { input ->
                if (input == null) throw IOException("Embedded DLC Manager mod is missing")
                Files.copy(DigestInputStream(input, digest), temp, StandardCopyOption.REPLACE_EXISTING)
            }
            val hash = digest.digest()
            val target = cache.resolve(HexFormat.of().formatHex(hash)).resolve("dlc-manager.jar")
            Files.createDirectories(target.parent)
            if (!Files.isRegularFile(target) || !matches(target, hash)) {
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            JarFile(target.toFile()).use { jar ->
                if (jar.getEntry("META-INF/mods.toml") == null && jar.getEntry("META-INF/neoforge.mods.toml") == null) {
                    throw IOException("Embedded DLC Manager mod metadata is missing")
                }
            }
            val distributionProperty = System.getProperty(DlcDistributionPaths.DISTRIBUTION_PROPERTY)
            if (distributionProperty.isNullOrBlank()) throw IOException("DLC Manager distribution path is unavailable before mod discovery")
            val distribution = Path.of(distributionProperty)
            modFile = target.toAbsolutePath().normalize()
            development = developmentLaunch
            System.setProperty(DlcDistributionPaths.PAYLOAD_PROPERTY, modFile.toString())
            System.setProperty(DlcDistributionPaths.DISTRIBUTION_PROPERTY, distribution.toAbsolutePath().normalize().toString())
        } catch (ex: NoSuchAlgorithmException) {
            throw IOException("Cannot prepare embedded DLC Manager mod", ex)

        } finally {
            Files.deleteIfExists(temp)
        }
    }

    @JvmStatic
    fun isDevelopment(): Boolean = development

    @JvmStatic
    fun getModFile(): Path = modFile ?: throw IllegalStateException("DLC Manager mod was not prepared before discovery")

    private fun matches(path: Path, expected: ByteArray): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(Files.newInputStream(path), digest).use { it.transferTo(OutputStream.nullOutputStream()) }
        return MessageDigest.isEqual(digest.digest(), expected)
    }
}
