package com.cozooo.dlc_bootstrap.launch;

import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;

public final class DlcNeoForgePayloadBridge implements IModFileCandidateLocator {
    @Override public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        if (!DlcEarlyRuntime.ready()) DlcEarlyRuntime.initialize((Object) context);
        if (DlcEarlyRuntime.development()) return;
        pipeline.addPath(DlcEarlyRuntime.modFile(), ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR)
            .orElseThrow(() -> new IllegalStateException("Cannot discover embedded DLC Manager mod"));
    }
}
