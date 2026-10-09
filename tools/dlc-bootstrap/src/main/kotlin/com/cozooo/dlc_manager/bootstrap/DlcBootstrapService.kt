package com.cozooo.dlc_manager.bootstrap

import cpw.mods.modlauncher.api.IEnvironment
import cpw.mods.modlauncher.api.ITransformationService
import cpw.mods.modlauncher.api.ITransformer
import java.io.IOException
import java.util.Locale
import java.util.Properties
import java.util.function.BooleanSupplier
import java.util.function.Consumer

class DlcBootstrapService(
    private val progress: Consumer<Map<String, Any>>,
    private val cancelled: BooleanSupplier
) : ITransformationService {
    constructor() : this(Consumer {}, BooleanSupplier { false })

    override fun name() = "dlcbootstrap"
    override fun onLoad(environment: IEnvironment, otherServices: Set<String>) {}
    override fun transformers(): List<ITransformer<*>> = emptyList()

    override fun initialize(environment: IEnvironment) {
        System.clearProperty(DlcStartupRequirements.READY_PROPERTY)
        val profile = Properties()
        var complete = false
        try {
            checkCancelled()
            javaClass.getResourceAsStream("/dlc-bootstrap.properties").use { input ->
                if (input == null) throw IOException("Missing DLC bootstrap profile")
                profile.load(input)
            }
            val loader = profile.getProperty("loader") ?: throw IOException("Invalid DLC bootstrap profile")
            val minecraft = profile.getProperty("minecraft") ?: throw IOException("Invalid DLC bootstrap profile")
            val game = environment.getProperty(IEnvironment.Keys.GAMEDIR.get())
                .orElseThrow { IOException("Game directory is unavailable before mod discovery") }
            val target = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get())
                .orElseThrow { IOException("Launch target is unavailable before mod discovery") }
            val server = target.lowercase(Locale.ROOT).contains("server")
            DlcModPayload.prepare(game, target.lowercase(Locale.ROOT).contains("dev"))
            DlcEarlyInstaller.install(game, game.resolve("config"), loader, minecraft, server,
                { println("[dlc-manager-bootstrap] $it") }, { publish(it) }, BooleanSupplier { isCancelled() })
            checkCancelled()
            DlcStartupRequirements.markComplete(game)
            checkCancelled()
            complete = true
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException(DlcStartupRequirements.FAILURE_MESSAGE + ": installation interrupted; startup stopped", ex)
        } catch (ex: IOException) {
            throw IllegalStateException(DlcStartupRequirements.FAILURE_MESSAGE + ": startup stopped. Check the bootstrap log and retry launching.", ex)
        } catch (ex: RuntimeException) {
            throw IllegalStateException(DlcStartupRequirements.FAILURE_MESSAGE + ": startup stopped. Check the bootstrap log and retry launching.", ex)
        } finally {
            if (!complete || isCancelled()) System.clearProperty(DlcStartupRequirements.READY_PROPERTY)
        }
    }

    private fun publish(value: DlcEarlyInstaller.Progress) {
        progress.accept(java.util.Map.of<String, Any>(
            "stage", value.stage.name,
            "completed", value.completedCount,
            "total", value.totalCount,
            "resource", value.resourceName,
            "bytes", value.downloadedBytes,
            "totalBytes", value.totalBytes
        ))
    }

    private fun isCancelled(): Boolean = cancelled.asBoolean || Thread.currentThread().isInterrupted

    private fun checkCancelled() {
        if (isCancelled()) throw InterruptedException("Required resource installation interrupted")
    }
}
