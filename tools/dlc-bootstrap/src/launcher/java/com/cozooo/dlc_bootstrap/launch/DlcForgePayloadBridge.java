package com.cozooo.dlc_bootstrap.launch;

//? if >=1.20.1 <1.21.1 {
/*import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModProvider;
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
*///?}
//? if <1.20.1 {
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;
import net.minecraftforge.forgespi.locating.IModLocator;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

public final class DlcForgePayloadBridge extends AbstractJarFileModLocator implements IModLocator {
    @Override public String name() { return "dlcmanagerpayload"; }
    @Override public void initArguments(Map<String, ?> arguments) {}
    @Override public Stream<Path> scanCandidates() {
        if (DlcEarlyRuntime.development()) return Stream.empty();
        return Stream.of(DlcEarlyRuntime.modFile());
    }
}
//?}
