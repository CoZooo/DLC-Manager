package com.cozooo.dlc_fileops_helper;

import net.neoforged.fml.common.Mod;

@Mod(FileOpsHelperConstants.MOD_ID)
public final class FileOpsHelperMod {
    public FileOpsHelperMod() {
        FileOpsHelperBootstrap.initialize();
    }
}

