package com.cozooo.dlc_manager.bootstrap

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService
import com.cozooo.dlc_manager.core.DlcStateConfig
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.platform.DlcPlatformServices

object DlcBootstrap {
    @JvmStatic
    fun initializeCore() {
        DlcSelfUpdateService.applyPendingUpdate()
        if (DlcPlatformServices.isDedicatedServer()) {
            DlcConfig.loadServer()
        } else {
            DlcConfig.loadClient()
        }
        DlcStartupRequirements.verifyBeforeGameLoad()
        DlcStateConfig.load()
        if (!DlcStartupRequirements.usesEarlyInstallation() && DlcPlatformServices.isDedicatedServer()) {
            try { com.cozooo.dlc_manager.client.update.DlcPendingUpdate.process({ println(it) }, true) }
            catch (exception: java.io.IOException) { throw IllegalStateException("Pending update recovery failed", exception) }
            DlcModpackAutoUpdater.runIfConfigured(true) { println(it) }
        }
    }
}
