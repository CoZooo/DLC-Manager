package com.cozooo.dlc_bootstrap.launch;

import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModProvider;
import net.minecraftforge.forgespi.locating.IModLocator;
import java.util.List;
import java.util.Map;

public final class DlcForgePayloadBridge extends AbstractJarFileModProvider implements IModLocator {
    @Override public String name() { return "dlcmanagerpayload"; }
    @Override public void initArguments(Map<String, ?> arguments) {}
    @Override public List<ModFileOrException> scanMods() {
        if (DlcEarlyRuntime.development()) return List.of();
        return List.of(createMod(DlcEarlyRuntime.modFile()));
    }
}
