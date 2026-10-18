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
            System.clearProperty(READY_PROPERTY)
            throw IllegalStateException(DlcBootstrapI18n.text("failure.summary") + " " + DlcBootstrapI18n.text("early_incomplete"))
        }
        try {
            DlcEarlyInstaller.verifyInstalled()
        } catch (ex: IOException) {
            System.clearProperty(READY_PROPERTY)
            throw IllegalStateException(DlcBootstrapI18n.text("failure.summary") + " " + DlcBootstrapI18n.text("verification_failed"), ex)
        } catch (ex: RuntimeException) {
            System.clearProperty(READY_PROPERTY)
            throw IllegalStateException(DlcBootstrapI18n.text("failure.summary") + " " + DlcBootstrapI18n.text("verification_failed"), ex)
        }
    }
}
