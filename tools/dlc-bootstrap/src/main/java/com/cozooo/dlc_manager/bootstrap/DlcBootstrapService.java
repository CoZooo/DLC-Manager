package com.cozooo.dlc_manager.bootstrap;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class DlcBootstrapService implements ITransformationService {
    private final Consumer<Map<String, Object>> progress;
    private final BooleanSupplier cancelled;

    public DlcBootstrapService() { this(ignored -> {}, () -> false); }

    public DlcBootstrapService(Consumer<Map<String, Object>> progress, BooleanSupplier cancelled) {
        this.progress = Objects.requireNonNull(progress);
        this.cancelled = Objects.requireNonNull(cancelled);
    }

    @Override public String name() { return "dlcbootstrap"; }
    @Override public void onLoad(IEnvironment environment, Set<String> otherServices) {}
    @SuppressWarnings("rawtypes")
    @Override public List<ITransformer> transformers() { return List.of(); }

    @Override public void initialize(IEnvironment environment) {
        System.clearProperty(DlcStartupRequirements.READY_PROPERTY);
        Properties profile = new Properties();
        boolean complete = false;
        try (var input = getClass().getResourceAsStream("/dlc-bootstrap.properties")) {
            checkCancelled();
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
                message -> System.out.println("[dlc-manager-bootstrap] " + message), this::publish, this::isCancelled);
            checkCancelled();
            DlcStartupRequirements.markComplete(game);
            checkCancelled();
            complete = true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(DlcStartupRequirements.FAILURE_MESSAGE + ": installation interrupted; startup stopped", ex);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException(DlcStartupRequirements.FAILURE_MESSAGE + ": startup stopped. Check the bootstrap log and retry launching.", ex);
        } finally {
            if (!complete || isCancelled()) System.clearProperty(DlcStartupRequirements.READY_PROPERTY);
        }
    }

    private void publish(DlcEarlyInstaller.Progress value) {
        progress.accept(Map.of(
            "stage", value.getStage().name(),
            "completed", value.getCompletedCount(),
            "total", value.getTotalCount(),
            "resource", Objects.requireNonNullElse(value.getResourceName(), ""),
            "bytes", value.getDownloadedBytes(),
            "totalBytes", value.getTotalBytes()
        ));
    }

    private boolean isCancelled() { return cancelled.getAsBoolean() || Thread.currentThread().isInterrupted(); }

    private void checkCancelled() throws InterruptedException {
        if (isCancelled()) throw new InterruptedException("Required resource installation interrupted");
    }
}
