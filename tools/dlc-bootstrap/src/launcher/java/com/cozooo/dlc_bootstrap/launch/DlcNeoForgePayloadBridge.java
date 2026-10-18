package com.cozooo.dlc_bootstrap.launch;

//? if >=1.21.1 {
/*
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;

public final class DlcNeoForgePayloadBridge implements IModFileCandidateLocator {
    @Override public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        try {
            if (!DlcEarlyRuntime.ready()) {
                DlcEarlyRuntime.initialize(context);
            }
            if (DlcEarlyRuntime.development()) return;
            pipeline.addPath(DlcEarlyRuntime.modFile(), ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR)
                .orElseThrow(() -> new IllegalStateException("Cannot discover embedded DLC Manager mod"));
        } catch (RuntimeException | LinkageError failure) {
            throw new net.neoforged.fml.ModLoadingException(java.util.List.of(
                net.neoforged.fml.ModLoadingIssue.error("fml.modloadingissue.technical_error", failure.getMessage()).withCause(failure)));
        }
    }
}
*///?}
