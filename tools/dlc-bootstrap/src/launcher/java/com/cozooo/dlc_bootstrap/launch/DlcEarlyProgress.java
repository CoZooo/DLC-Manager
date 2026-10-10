package com.cozooo.dlc_bootstrap.launch;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class DlcEarlyProgress {
    static final String READY_PROPERTY = "dlc_manager.early.ready";
    private static final Map<String, Object> INITIAL = Map.of("stage", "SCANNING", "completed", 0, "total", -1);

    private DlcEarlyProgress() {}

    @FunctionalInterface
    interface Installation {
        void run(Consumer<Map<String, Object>> progress, BooleanSupplier cancelled) throws Exception;
    }

    @FunctionalInterface
    interface DisplayFactory {
        Display open() throws Exception;
    }

    interface Display {
        void update(Frame frame) throws Exception;
        void tick() throws Exception;
        void close() throws Exception;
    }

    record Frame(int steps, int current, String label) {}

    static void run(String launchTarget, String loader, ClassLoader classLoader, Installation installation) throws Exception {
        run(launchTarget, () -> NativeDisplay.open(loader, classLoader), installation);
    }

    static void run(String launchTarget, DisplayFactory factory, Installation installation) throws Exception {
        System.clearProperty(READY_PROPERTY);
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Map<String, Object>> latest = new AtomicReference<>(INITIAL);
        Consumer<Map<String, Object>> publish = value -> {
            if (value != null) latest.set(Collections.unmodifiableMap(new HashMap<>(value)));
        };
        Ui ui = new Ui();
        boolean success = false;
        try {
            checkCancelled(cancelled);
            if (launchTarget != null && launchTarget.toLowerCase(Locale.ROOT).contains("client")) {
                ui.open(factory);
                ui.update(frame(INITIAL));
            }
            if (ui.display == null) {
                installation.run(publish, () -> cancelled.get() || Thread.currentThread().isInterrupted());
            } else {
                runWithWindow(ui, latest, publish, cancelled, installation);
            }
            checkCancelled(cancelled);
            success = true;
        } finally {
            ui.close();
            if (!success || cancelled.get() || Thread.currentThread().isInterrupted()) {
                System.clearProperty(READY_PROPERTY);
            }
        }
    }

    private static void runWithWindow(Ui ui, AtomicReference<Map<String, Object>> latest,
                                      Consumer<Map<String, Object>> publish, AtomicBoolean cancelled,
                                      Installation installation) throws Exception {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        var executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "DLC-Manager-EarlyInstaller");
            thread.setDaemon(true);
            thread.setContextClassLoader(context);
            workerThread.set(thread);
            return thread;
        });
        CountDownLatch finished = new CountDownLatch(1);
        Future<?> work;
        try {
            work = executor.submit(() -> {
                boolean success = false;
                try {
                    checkCancelled(cancelled);
                    installation.run(publish, () -> cancelled.get() || Thread.currentThread().isInterrupted());
                    checkCancelled(cancelled);
                    success = true;
                    return null;
                } finally {
                    if (Thread.currentThread().isInterrupted()) cancelled.set(true);
                    if (!success || cancelled.get() || Thread.currentThread().isInterrupted()) {
                        System.clearProperty(READY_PROPERTY);
                    }
                    finished.countDown();
                }
            });
        } catch (RuntimeException | Error failure) {
            executor.shutdownNow();
            throw failure;
        }

        boolean interrupted = false;
        Map<String, Object> displayed = null;
        try {
            while (finished.getCount() != 0) {
                Map<String, Object> snapshot = latest.get();
                if (!cancelled.get() && snapshot != displayed) {
                    ui.update(frame(snapshot));
                    displayed = snapshot;
                }
                ui.tick();
                try {
                    finished.await(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException exception) {
                    interrupted = true;
                    cancelled.set(true);
                    workerThread.get().interrupt();
                    ui.update(new Frame(0, 0, "Cancelling required resource installation"));
                }
            }
            if (interrupted) throw new InterruptedException("Required resource installation interrupted");
            try {
                work.get();
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cancelled.get()) {
                    InterruptedException interruption = new InterruptedException("Required resource installation interrupted");
                    interruption.initCause(cause);
                    throw interruption;
                }
                if (cause instanceof Exception checked) throw checked;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException(cause);
            }
            ui.update(frame(latest.get()));
            ui.tick();
        } catch (InterruptedException exception) {
            interrupted = true;
            cancelled.set(true);
            workerThread.get().interrupt();
            throw exception;
        } finally {
            if (finished.getCount() != 0) {
                cancelled.set(true);
                workerThread.get().interrupt();
                while (finished.getCount() != 0) {
                    try {
                        finished.await(50, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException exception) {
                        interrupted = true;
                    }
                }
            }
            executor.shutdownNow();
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException exception) {
                    interrupted = true;
                    cancelled.set(true);
                }
            }
            Thread worker = workerThread.get();
            while (worker.isAlive()) {
                try {
                    worker.join(50);
                } catch (InterruptedException exception) {
                    interrupted = true;
                    cancelled.set(true);
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void checkCancelled(AtomicBoolean cancelled) throws InterruptedException {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            cancelled.set(true);
            throw new InterruptedException("Required resource installation interrupted");
        }
    }

    static Frame frame(Map<String, Object> snapshot) {
        String stage = text(snapshot, "stage");
        String action = switch (stage) {
            case "SCANNING" -> "Scanning";
            case "PLANNED" -> "Resources found";
            case "DOWNLOADING" -> "Downloading";
            case "INSTALLING" -> "Installing";
            case "ENTRY_COMPLETE" -> "Ready";
            case "VERIFYING" -> "Verifying";
            case "COMPLETE" -> "Complete";
            case "FAILED" -> "Failed";
            default -> "Working";
        };
        long completed = Math.max(0, number(snapshot, "completed", 0));
        long total = number(snapshot, "total", -1);
        long bytes = Math.max(0, number(snapshot, "bytes", 0));
        long totalBytes = number(snapshot, "totalBytes", -1);
        boolean downloading = stage.equals("DOWNLOADING");
        boolean installing = stage.equals("INSTALLING");
        double partial = installing ? 0.90 : downloading && totalBytes > 0 ? 0.85 * Math.min(1, bytes / (double) totalBytes) : 0;
        int steps = total >= 0 || stage.equals("COMPLETE") ? 1000 : 0;
        double ratio = total > 0 ? (completed + partial) / total : 0;
        int current = stage.equals("COMPLETE") ? 1000 : Math.min(999, (int) (Math.max(0, Math.min(1, ratio)) * steps));
        StringBuilder label = new StringBuilder(action);
        if (total >= 0) {
            long entry = downloading || installing ? Math.min(completed + 1, total) : completed;
            label.append(' ').append(entry).append('/').append(total);
        }
        String resource = ascii(text(snapshot, "resource"), 24);
        if (resource.chars().anyMatch(character -> Character.isLetterOrDigit(character))) label.append(" - ").append(resource);
        if (downloading || bytes > 0) {
            label.append(" [").append(bytes(bytes));
            if (totalBytes >= 0) label.append('/').append(bytes(totalBytes));
            if (totalBytes > 0) label.append(' ').append((int) (Math.min(1, bytes / (double) totalBytes) * 100)).append('%');
            label.append(']');
        }
        return new Frame(steps, current, ascii(label.toString(), 120));
    }

    private static long number(Map<String, Object> value, String key, long fallback) {
        return value.get(key) instanceof Number number ? number.longValue() : fallback;
    }

    private static String text(Map<String, Object> value, String key) {
        return value.get(key) instanceof String text ? text : "";
    }

    private static String bytes(long value) {
        if (value < 1024) return value + " B";
        String[] units = { "KiB", "MiB", "GiB", "TiB" };
        double scaled = value;
        int unit = -1;
        do { scaled /= 1024; unit++; } while (scaled >= 1024 && unit < units.length - 1);
        return String.format(Locale.ROOT, "%.1f %s", scaled, units[unit]);
    }

    private static String ascii(String value, int maximum) {
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < value.length() && safe.length() < maximum; i++) {
            char c = value.charAt(i);
            safe.append(c >= 32 && c <= 126 ? c : '?');
        }
        if (value.length() > maximum && maximum >= 3) safe.replace(maximum - 3, maximum, "...");
        return safe.toString();
    }

    private static final class Ui {
        private Display display;

        void open(DisplayFactory factory) {
            try { display = factory.open(); }
            catch (Exception | LinkageError failure) { unavailable(failure); }
        }

        void update(Frame frame) {
            if (display == null) return;
            try { display.update(frame); }
            catch (Exception | LinkageError failure) { unavailable(failure); close(); }
        }

        void tick() {
            if (display == null) return;
            try { display.tick(); }
            catch (Exception | LinkageError failure) { unavailable(failure); close(); }
        }

        void close() {
            Display old = display;
            display = null;
            if (old == null) return;
            try { old.close(); }
            catch (Exception | LinkageError failure) { unavailable(failure); }
        }

        private void unavailable(Throwable failure) {
            Throwable cause = failure instanceof InvocationTargetException wrapped ? wrapped.getCause() : failure;
            System.err.println("[dlc-manager-bootstrap] Native progress display unavailable: " + cause);
        }
    }

    private static final class NativeDisplay implements Display {
        private final Runnable tick;
        private final Method create;
        private final Method label;
        private final Method absolute;
        private final Method complete;
        private Object meter;
        private int steps = -1;

        private NativeDisplay(Runnable tick, Class<?> manager, Class<?> meterType, boolean neoForge) throws ReflectiveOperationException {
            this.tick = tick;
            create = manager.getMethod(neoForge ? "prependProgressBar" : "addProgressBar", String.class, int.class);
            label = meterType.getMethod("label", String.class);
            absolute = meterType.getMethod("setAbsolute", int.class);
            complete = meterType.getMethod("complete");
        }

        static Display open(String loader, ClassLoader classLoader) throws ReflectiveOperationException {
            boolean neoForge = "neoforge".equals(loader);
            if (!neoForge && !"forge".equals(loader)) return null;
            String prefix = neoForge ? "net.neoforged.fml.loading." : "net.minecraftforge.fml.loading.";
            Class<?> fml = Class.forName(prefix + "FMLLoader", false, classLoader);
            Runnable runnable;
            try {
                Object tick = fml.getField("progressWindowTick").get(null);
                if (!(tick instanceof Runnable value)) return null;
                runnable = value;
            } catch (NoSuchFieldException missingLegacyTick) {
                if (!neoForge) throw missingLegacyTick;
                Method renderTick = Class.forName(prefix + "ImmediateWindowHandler", false, classLoader)
                    .getMethod("renderTick");
                runnable = () -> {
                    try { renderTick.invoke(null); }
                    catch (ReflectiveOperationException exception) { throw new IllegalStateException("Cannot update NeoForge early window", exception); }
                };
            }
            Class<?> config = Class.forName(prefix + "FMLConfig", false, classLoader);
            Class<?> values = Class.forName(prefix + "FMLConfig$ConfigValue", false, classLoader);
            Object control = values.getField("EARLY_WINDOW_CONTROL").get(null);
            if (!Boolean.TRUE.equals(config.getMethod("getBoolConfigValue", values).invoke(null, control))) return null;
            Object provider = values.getField("EARLY_WINDOW_PROVIDER").get(null);
            if (!"fmlearlywindow".equals(config.getMethod("getConfigValue", values).invoke(null, provider))) return null;
            Class.forName(neoForge ? "net.neoforged.fml.earlydisplay.DisplayWindow" : "net.minecraftforge.fml.earlydisplay.DisplayWindow",
                false, classLoader);
            Class<?> manager = Class.forName(prefix + "progress.StartupNotificationManager", false, classLoader);
            Class<?> meter = Class.forName(prefix + "progress.ProgressMeter", false, classLoader);
            return new NativeDisplay(runnable, manager, meter, neoForge);
        }

        @Override public void update(Frame frame) throws ReflectiveOperationException {
            if (meter == null || steps != frame.steps()) {
                close();
                meter = create.invoke(null, "DLC Manager", frame.steps());
                steps = frame.steps();
            }
            label.invoke(meter, frame.label());
            absolute.invoke(meter, frame.current());
        }

        @Override public void tick() { tick.run(); }

        @Override public void close() throws ReflectiveOperationException {
            Object old = meter;
            meter = null;
            if (old != null) complete.invoke(old);
        }
    }
}
