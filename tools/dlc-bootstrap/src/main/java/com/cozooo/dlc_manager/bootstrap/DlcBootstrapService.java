package com.cozooo.dlc_manager.bootstrap;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public final class DlcBootstrapService implements ITransformationService {
    @Override public String name() { return "dlcbootstrap"; }
    @Override public void onLoad(IEnvironment environment, Set<String> otherServices) {}
    @SuppressWarnings("rawtypes")
    @Override public List<ITransformer> transformers() { return List.of(); }

    @Override public void initialize(IEnvironment environment) {
        Properties profile = new Properties();
        try (var input = getClass().getResourceAsStream("/dlc-bootstrap.properties")) {
            if (input == null) throw new IOException("Missing DLC bootstrap profile");
            profile.load(input);
            String loader = profile.getProperty("loader");
            String minecraft = profile.getProperty("minecraft");
            if (loader == null || minecraft == null) throw new IOException("Invalid DLC bootstrap profile");
            Path game = environment.getProperty(IEnvironment.Keys.GAMEDIR.get())
                .orElseThrow(() -> new IOException("Game directory is unavailable before mod discovery"));
            String target = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get())
                .orElseThrow(() -> new IOException("Launch target is unavailable before mod discovery"));
            boolean server = target.toLowerCase(Locale.ROOT).contains("server");
            DlcModPayload.prepare(game, target.toLowerCase(Locale.ROOT).contains("dev"));
            DlcEarlyInstaller.install(game, game.resolve("config"), loader, minecraft, server,
                message -> System.out.println("[dlc-manager-bootstrap] " + message));
            DlcStartupRequirements.markComplete(game);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(DlcStartupRequirements.FAILURE_MESSAGE + ": installation interrupted; startup stopped", ex);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException(DlcStartupRequirements.FAILURE_MESSAGE + ": startup stopped. Check the bootstrap log and retry launching.", ex);
        }
    }
}
