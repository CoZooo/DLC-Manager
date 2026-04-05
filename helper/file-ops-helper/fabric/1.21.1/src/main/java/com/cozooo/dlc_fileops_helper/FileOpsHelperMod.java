package com.cozooo.dlc_fileops_helper;

import net.fabricmc.api.ModInitializer;

public final class FileOpsHelperMod implements ModInitializer {
    @Override
    public void onInitialize() {
        FileOpsHelperBootstrap.initialize();
    }
}

