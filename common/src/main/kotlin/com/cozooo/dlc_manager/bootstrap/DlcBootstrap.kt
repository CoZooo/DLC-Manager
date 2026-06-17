package com.cozooo.dlc_manager.bootstrap

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService
import com.cozooo.dlc_manager.core.DlcStateConfig
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime

object DlcBootstrap {
    @JvmStatic
    fun initializeCore() {
        DlcSelfUpdateService.applyPendingUpdate()
        if (CrossPlatformRuntime.isDedicatedServer()) {
            DlcPathConfig.loadServer()
        } else {
            DlcPathConfig.loadClient()
        }
        DlcStateConfig.load()
    }

    @JvmStatic
    fun <T> initializeClientEntrypoint(modBusClass: Class<T>, modBus: T) {
        if (CrossPlatformRuntime.isDedicatedServer()) {
            return
        }

        try {
            val client = Class.forName("com.cozooo.dlc_manager.DlcManagerClient")
            client.getMethod("init", modBusClass).invoke(null, modBus)
        } catch (_: Throwable) {
        }
    }
}
