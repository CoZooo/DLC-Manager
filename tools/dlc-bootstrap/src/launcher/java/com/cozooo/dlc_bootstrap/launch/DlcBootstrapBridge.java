package com.cozooo.dlc_bootstrap.launch;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import java.util.List;
import java.util.Set;

public final class DlcBootstrapBridge implements ITransformationService {
    @Override public String name() { return "dlcbootstrap"; }
    @Override public void onLoad(IEnvironment environment, Set<String> otherServices) {}
    @Override public void initialize(IEnvironment environment) { DlcEarlyRuntime.initialize(environment); }
    @Override @SuppressWarnings("rawtypes") public List<ITransformer> transformers() { return List.of(); }
}
