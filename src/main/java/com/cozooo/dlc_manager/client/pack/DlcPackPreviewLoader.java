package com.cozooo.dlc_manager.client.pack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class DlcPackPreviewLoader {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "dlc-pack-preview");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService worker;
    private final AtomicLong generation = new AtomicLong();
    private Future<?> pending;

    public DlcPackPreviewLoader() { this(WORKER); }

    public DlcPackPreviewLoader(ExecutorService worker) { this.worker = worker; }

    public void load(Path gameDir, Path dlcRoot, Path orderPath, Executor owner, Consumer<Snapshot> apply, Consumer<Exception> failed) {
        cancel();
        long request = generation.get();
        pending = worker.submit(() -> {
            try {
                if (generation.get() != request) return;
                int uninstalled = 0;
                Exception compatibilityError = null;
                try {
                    uninstalled = new DlcPackUpdateCoordinator(gameDir, dlcRoot).uninstallIncompatibleForCurrentVersion().size();
                } catch (Exception error) {
                    compatibilityError = error;
                }
                if (generation.get() != request) return;
                Map<String, DlcPackMeta> installed = new HashMap<>();
                Path installedRoot = dlcRoot.resolve("tmp/pack");
                if (Files.isDirectory(installedRoot)) {
                    try (var directories = Files.list(installedRoot)) {
                        directories.filter(Files::isDirectory).forEach(dir -> {
                            DlcPackMeta meta = DlcPackMeta.load(dir.resolve("meta.dc"));
                            if (meta != null) installed.put(meta.identifier, meta);
                        });
                    }
                }
                List<Preview> previews = new ArrayList<>();
                List<Path> archives = DlcPackArchive.findArchives(dlcRoot).stream()
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
                for (Path archive : archives) {
                    if (generation.get() != request) return;
                    Preview preview = readPreview(archive, dlcRoot);
                    if (preview != null) previews.add(preview);
                }
                Snapshot snapshot = new Snapshot(previews, installed, DlcPackOrder.load(orderPath), uninstalled, compatibilityError);
                owner.execute(() -> { if (generation.get() == request) apply.accept(snapshot); });
            } catch (Exception error) {
                owner.execute(() -> { if (generation.get() == request) failed.accept(error); });
            }
        });
    }

    public void cancel() {
        generation.incrementAndGet();
        if (pending != null) pending.cancel(false);
        pending = null;
    }

    private static Preview readPreview(Path archive, Path dlcRoot) {
        Path staging = null;
        try {
            var extracted = DlcPackArchive.extractPreviewToStaging(archive, dlcRoot);
            staging = extracted.stagingDir;
            var result = DlcPackPackage.inspectPreview(extracted);
            if (!result.valid || result.pack == null) return null;
            byte[] icon = null;
            try {
                if (result.pack.iconFile != null) icon = Files.readAllBytes(result.pack.iconFile);
            } catch (Exception ignored) {
            }
            return new Preview(archive.toAbsolutePath().normalize(), result.pack.metadata, icon);
        } catch (Exception ignored) {
            return null;
        } finally {
            DlcPackArchive.deleteRecursive(staging);
        }
    }

    public static final class Snapshot {
        public final List<Preview> previews;
        public final Map<String, DlcPackMeta> installed;
        public final List<String> savedOrder;
        public final int uninstalled;
        public final Exception compatibilityError;

        public Snapshot(List<Preview> previews, Map<String, DlcPackMeta> installed, List<String> savedOrder, int uninstalled, Exception compatibilityError) {
            this.previews = List.copyOf(previews);
            this.installed = Map.copyOf(installed);
            this.savedOrder = List.copyOf(savedOrder);
            this.uninstalled = uninstalled;
            this.compatibilityError = compatibilityError;
        }
    }

    public static final class Preview {
        public final Path archive;
        public final DlcPackMetadata metadata;
        public final byte[] icon;

        public Preview(Path archive, DlcPackMetadata metadata, byte[] icon) {
            this.archive = archive;
            this.metadata = metadata;
            this.icon = icon;
        }
    }
}
