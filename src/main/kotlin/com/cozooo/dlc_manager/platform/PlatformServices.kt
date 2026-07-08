package com.cozooo.dlc_manager.platform

object PlatformServices {
    interface Provider : DlcPlatformServices.Provider

    @JvmStatic
    fun setProvider(provider: DlcPlatformServices.Provider?) {
        DlcPlatformServices.setProvider(provider)
    }

    @JvmStatic
    fun get(): DlcPlatformServices.Provider {
        return DlcPlatformServices.get()
    }
}
