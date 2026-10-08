package com.cozooo.dlc_manager.bootstrap

import cpw.mods.modlauncher.api.IEnvironment
import cpw.mods.modlauncher.api.ITransformationService
import cpw.mods.modlauncher.api.ITransformer
import java.io.IOException
import java.util.Locale
import java.util.Properties

class DlcBootstrapService : ITransformationService {
    override fun name() = "dlcbootstrap"
    override fun onLoad(environment: IEnvironment, otherServices: Set<String>) {}
    override fun transformers(): List<ITransformer<*>> = emptyList()

    override fun initialize(environment: IEnvironment) {
        val profile = Properties()
        try {
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
            DlcEarlyInstaller.install(game, game.resolve("config"), loader, minecraft, server) {
                println("[dlc-manager-bootstrap] $it")
            }
            DlcStartupRequirements.markComplete(game)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException(DlcStartupRequirements.FAILURE_MESSAGE + ": installation interrupted; startup stopped", ex)
        } catch (ex: IOException) {
            throw IllegalStateException(DlcStartupRequirements.FAILURE_MESSAGE + ": startup stopped. Check the bootstrap log and retry launching.", ex)
        } catch (ex: RuntimeException) {
            throw IllegalStateException(DlcStartupRequirements.FAILURE_MESSAGE + ": startup stopped. Check the bootstrap log and retry launching.", ex)
        }
    }
}
