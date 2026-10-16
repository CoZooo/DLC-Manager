package com.cozooo.dlc_manager.bootstrap

import com.cozooo.dlc_manager.platform.DlcPlatformServices
import java.io.IOException
import java.nio.file.Path

object DlcStartupRequirements {
    const val READY_PROPERTY = "dlc_manager.early.ready"
    const val FAILURE_MESSAGE = "Required resource installation failed"

    @JvmStatic
    fun usesEarlyInstallation(): Boolean {
        val loader = DlcPlatformServices.loaderName()
        return loader.equals("forge", ignoreCase = true) || loader.equals("neoforge", ignoreCase = true)
    }

    @JvmStatic
    fun markComplete(game: Path) {
        System.setProperty(READY_PROPERTY, game.toAbsolutePath().normalize().toString())
    }

    @JvmStatic
    fun verifyBeforeGameLoad() {
        if (!usesEarlyInstallation()) return
        val game = DlcPlatformServices.gameDir().toAbsolutePath().normalize().toString()
        if (System.getProperty(READY_PROPERTY) != game) {
            throw IllegalStateException("$FAILURE_MESSAGE: Early installation did not complete")
        }
        try {
            DlcEarlyInstaller.verifyInstalled()
        } catch (ex: IOException) {
            throw IllegalStateException("$FAILURE_MESSAGE: Startup stopped", ex)
        } catch (ex: RuntimeException) {
            throw IllegalStateException("$FAILURE_MESSAGE: Startup stopped", ex)
        }
    }
}
