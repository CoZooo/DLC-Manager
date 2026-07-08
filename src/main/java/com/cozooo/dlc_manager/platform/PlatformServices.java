package com.cozooo.dlc_manager.platform;

public final class PlatformServices {
    public interface Provider extends DlcPlatformServices.Provider {
    }

    private PlatformServices() {
    }

    public static void setProvider(DlcPlatformServices.Provider provider) {
        DlcPlatformServices.setProvider(provider);
    }

    public static DlcPlatformServices.Provider get() {
        return DlcPlatformServices.get();
    }
}
