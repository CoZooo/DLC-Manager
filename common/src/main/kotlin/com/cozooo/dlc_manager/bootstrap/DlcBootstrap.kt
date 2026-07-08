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
        DlcStateConfig.load()
    }

    @JvmStatic
    fun <T> initializeClientEntrypoint(modBusClass: Class<T>, modBus: T) {
        if (DlcPlatformServices.isDedicatedServer()) {
            return
        }

        try {
            val client = Class.forName("com.cozooo.dlc_manager.DlcManagerClient")
            client.getMethod("init", modBusClass).invoke(null, modBus)
        } catch (_: Throwable) {
        }
    }
}
