package com.cozooo.dlc_bootstrap.launch;

import cpw.mods.modlauncher.api.IEnvironment;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BooleanSupplier;

public interface DlcBootstrapEntrypoint {
    @FunctionalInterface
    interface ProgressSink {
        void accept(Map<String, Object> progress);
    }

    void configure(ProgressSink progress, BooleanSupplier cancelled);

    void initialize(IEnvironment environment) throws Exception;

    void initialize(Path game, String loader, String minecraft, boolean server, boolean development) throws Exception;

    Path modFile();

    boolean development();
}
