package com.cozooo.dlc_bootstrap.launch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class DlcEarlyProgressTest {
    @AfterEach
    void clearReadyAndInterruption() {
        System.clearProperty(DlcEarlyProgress.READY_PROPERTY);
        Thread.interrupted();
    }

    @Test
    void dedicatedServerNeverConnectsToUiOrStartsAnInstallerThread() throws Exception {
        Thread caller = Thread.currentThread();
        DlcEarlyProgress.run("forgeserver", () -> { fail("Server connected to native UI"); return null; }, (progress, cancelled) -> {
            assertSame(caller, Thread.currentThread());
            assertFalse(cancelled.getAsBoolean());
            progress.accept(update("COMPLETE", 0, 0, "", 0, -1));
            System.setProperty(DlcEarlyProgress.READY_PROPERTY, "server");
        });
        assertEquals("server", System.getProperty(DlcEarlyProgress.READY_PROPERTY));
    }

    @Test
    void missingNativeApiFallsBackToSynchronousInstallation() throws Exception {
        Thread caller = Thread.currentThread();
        DlcEarlyProgress.run("forgeclient", () -> { throw new NoClassDefFoundError("optional native UI"); }, (progress, cancelled) -> {
            assertSame(caller, Thread.currentThread());
            System.setProperty(DlcEarlyProgress.READY_PROPERTY, "client");
        });
        assertEquals("client", System.getProperty(DlcEarlyProgress.READY_PROPERTY));
    }

    @Test
    void failedInitialUiUpdateClosesDisplayAndUsesTheSynchronousPath() throws Exception {
        Thread caller = Thread.currentThread();
        RecordingDisplay display = new RecordingDisplay();
        display.onUpdate = frame -> { throw new IllegalStateException("renderer unavailable"); };
        DlcEarlyProgress.run("forgeclient", () -> display, (progress, cancelled) -> {
            assertSame(caller, Thread.currentThread());
            System.setProperty(DlcEarlyProgress.READY_PROPERTY, "installed");
        });
        assertEquals(1, display.closes);
        assertEquals("installed", System.getProperty(DlcEarlyProgress.READY_PROPERTY));
    }

    @Test
    void workerPublishesSnapshotsWhileAllUiCallsStayOnTheStartupThread() throws Exception {
        Thread caller = Thread.currentThread();
        ClassLoader context = caller.getContextClassLoader();
        RecordingDisplay display = new RecordingDisplay();
        CountDownLatch visible = new CountDownLatch(1);
        display.onUpdate = frame -> { if (frame.current() == 356) visible.countDown(); };
        AtomicReference<Thread> worker = new AtomicReference<>();
        DlcEarlyProgress.run("forgeclient", () -> display, (progress, cancelled) -> {
            worker.set(Thread.currentThread());
            assertNotSame(caller, Thread.currentThread());
            assertSame(context, Thread.currentThread().getContextClassLoader());
            progress.accept(update("DOWNLOADING", 1, 4, "example.jar", 1024L * 1024 * 1024, 2L * 1024 * 1024 * 1024));
            assertTrue(visible.await(5, TimeUnit.SECONDS));
            progress.accept(update("COMPLETE", 4, 4, "", 0, -1));
            System.setProperty(DlcEarlyProgress.READY_PROPERTY, "installed");
        });
        assertSame(caller, display.owner);
        assertFalse(worker.get().isAlive());
        assertEquals(1, display.closes);
        assertTrue(display.ticks > 0);
        assertEquals("installed", System.getProperty(DlcEarlyProgress.READY_PROPERTY));
    }

    @Test
    void laterUiFailureDoesNotAbortInstallation() throws Exception {
        RecordingDisplay display = new RecordingDisplay();
        CountDownLatch failedUi = new CountDownLatch(1);
        display.onUpdate = frame -> {
            if (frame.label().startsWith("Downloading")) {
                failedUi.countDown();
                throw new IllegalStateException("display disconnected");
            }
        };
        DlcEarlyProgress.run("forgeclient", () -> display, (progress, cancelled) -> {
            progress.accept(update("DOWNLOADING", 0, 1, "example.jar", 1, 2));
            assertTrue(failedUi.await(5, TimeUnit.SECONDS));
            assertFalse(cancelled.getAsBoolean());
            System.setProperty(DlcEarlyProgress.READY_PROPERTY, "installed");
        });
        assertEquals(1, display.closes);
        assertEquals("installed", System.getProperty(DlcEarlyProgress.READY_PROPERTY));
    }

    @Test
    void installerExceptionIsNotMistakenForADisplayFailure() {
        RecordingDisplay display = new RecordingDisplay();
        IOException failure = new IOException("download failed");
        IOException thrown = assertThrows(IOException.class, () -> DlcEarlyProgress.run("forgeclient", () -> display, (progress, cancelled) -> {
            System.setProperty(DlcEarlyProgress.READY_PROPERTY, "must not survive");
            throw failure;
        }));
        assertSame(failure, thrown);
        assertNull(System.getProperty(DlcEarlyProgress.READY_PROPERTY));
        assertEquals(1, display.closes);
    }

    @Test
    void interruptionBeforeStartingDoesNotConnectUiOrRunTheInstaller() {
        Thread.currentThread().interrupt();
        assertThrows(InterruptedException.class, () -> DlcEarlyProgress.run("forgeclient", () -> {
            fail("Opened UI after cancellation"); return null;
        }, (progress, cancelled) -> fail("Ran installer after cancellation")));
        assertTrue(Thread.currentThread().isInterrupted());
        assertNull(System.getProperty(DlcEarlyProgress.READY_PROPERTY));
    }

    @Test
    void parentInterruptionWaitsForActualWorkerExitAndRemovesLateReady() throws Exception {
        RecordingDisplay display = new RecordingDisplay();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch cancellationSeen = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(1);
        AtomicBoolean tokenSeen = new AtomicBoolean();
        AtomicBoolean interruptedOnReturn = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread startup = new Thread(() -> {
            try {
                DlcEarlyProgress.run("forgeclient", () -> display, (progress, cancelled) -> {
                    started.countDown();
                    try {
                        while (!cancelled.getAsBoolean()) Thread.sleep(5);
                    } catch (InterruptedException expected) {
                    }
                    tokenSeen.set(cancelled.getAsBoolean());
                    cancellationSeen.countDown();
                    progress.accept(update("COMPLETE", 1, 1, "", 0, -1));
                    boolean released = false;
                    while (!released) {
                        try { released = releaseWorker.await(1, TimeUnit.SECONDS); }
                        catch (InterruptedException ignored) {}
                    }
                    System.setProperty(DlcEarlyProgress.READY_PROPERTY, "late completion");
                });
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                interruptedOnReturn.set(Thread.currentThread().isInterrupted());
                returned.countDown();
            }
        }, "test-startup");
        startup.start();
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            startup.interrupt();
            assertTrue(cancellationSeen.await(5, TimeUnit.SECONDS));
            assertFalse(returned.await(100, TimeUnit.MILLISECONDS), "initialize returned while its worker was still running");
        } finally {
            releaseWorker.countDown();
            startup.join(5000);
        }
        assertFalse(startup.isAlive());
        assertTrue(tokenSeen.get());
        assertInstanceOf(InterruptedException.class, failure.get());
        assertTrue(interruptedOnReturn.get());
        assertNull(System.getProperty(DlcEarlyProgress.READY_PROPERTY));
        assertEquals(1, display.closes);
        assertTrue(display.frames.stream().noneMatch(frame -> frame.label().startsWith("Complete")),
            "A late completion snapshot must not replace the cancellation label");
    }

    @Test
    void wrappedWorkerInterruptionPropagatesAndPreservesTheCause() {
        RecordingDisplay display = new RecordingDisplay();
        IllegalStateException wrapped = new IllegalStateException("installer interrupted", new InterruptedException("cancelled"));
        InterruptedException thrown = assertThrows(InterruptedException.class,
            () -> DlcEarlyProgress.run("forgeclient", () -> display, (progress, cancelled) -> {
                Thread.currentThread().interrupt();
                throw wrapped;
            }));
        assertSame(wrapped, thrown.getCause());
        assertTrue(Thread.currentThread().isInterrupted());
        assertNull(System.getProperty(DlcEarlyProgress.READY_PROPERTY));
    }

    @Test
    void unknownTotalsUseAnIndeterminateMeter() {
        var frame = DlcEarlyProgress.frame(update("SCANNING", 0, -1, "", 0, -1));
        assertEquals(0, frame.steps());
        assertEquals(0, frame.current());
        assertTrue(frame.label().startsWith("Scanning"));
    }

    @Test
    void byteProgressUsesLongValuesAndContributesToOverallEntryProgress() {
        var frame = DlcEarlyProgress.frame(update("DOWNLOADING", 1, 4, "large.jar", 4L << 30, 8L << 30));
        assertEquals(1000, frame.steps());
        assertEquals(356, frame.current());
        assertTrue(frame.label().contains("2/4"));
        assertTrue(frame.label().contains("4.0 GiB/8.0 GiB 50%"));
        var completedEntry = DlcEarlyProgress.frame(update("ENTRY_COMPLETE", 2, 4, "large.jar", 8L << 30, 8L << 30));
        assertEquals(500, completedEntry.current(), "A completed entry must not be counted twice");
        assertTrue(completedEntry.label().startsWith("Ready"));
    }

    @Test
    void nativeLabelsAreAsciiAndProgressIsClamped() {
        var frame = DlcEarlyProgress.frame(update("DOWNLOADING", 8, 2, "资源\n" + "x".repeat(160), 5000, 100));
        assertEquals(999, frame.current());
        assertTrue(frame.label().chars().allMatch(character -> character >= 32 && character <= 126));
        assertTrue(frame.label().contains("..."));
        assertTrue(frame.label().length() <= 120);
        var complete = DlcEarlyProgress.frame(update("COMPLETE", 0, 0, "", 0, -1));
        assertEquals(1000, complete.current());
    }

    @Test
    void lastDownloadAndInstallationRemainMonotonicAndDoNotFinishBeforeComplete() {
        var downloading = DlcEarlyProgress.frame(update("DOWNLOADING", 0, 1, "example.jar", 100, 100));
        var installing = DlcEarlyProgress.frame(update("INSTALLING", 0, 1, "example.jar", 100, 100));
        var installed = DlcEarlyProgress.frame(update("ENTRY_COMPLETE", 1, 1, "example.jar", 100, 100));
        var verifying = DlcEarlyProgress.frame(update("VERIFYING", 1, 1, "", 0, -1));
        var complete = DlcEarlyProgress.frame(update("COMPLETE", 1, 1, "", 0, -1));
        assertEquals(850, downloading.current());
        assertEquals(900, installing.current());
        assertEquals(999, installed.current());
        assertEquals(999, verifying.current());
        assertEquals(1000, complete.current());
        assertTrue(downloading.label().contains("1/1"));
        assertTrue(downloading.label().contains("100%"), "File percent must still report actual downloaded bytes");
        assertTrue(installing.label().contains("1/1"));
    }

    @Test
    void knownFileSizeDoesNotInventAnOverallPercentageWhenEntryTotalIsUnknown() {
        var downloading = DlcEarlyProgress.frame(update("DOWNLOADING", 2, -1, "example.jar", 50, 100));
        assertEquals(0, downloading.steps());
        assertEquals(0, downloading.current());
        assertFalse(downloading.label().contains("2/"));
        assertTrue(downloading.label().contains("50%"));
    }

    private static Map<String, Object> update(String stage, int completed, int total, String resource, long bytes, long totalBytes) {
        return Map.of("stage", stage, "completed", completed, "total", total, "resource", resource,
            "bytes", bytes, "totalBytes", totalBytes);
    }

    private static final class RecordingDisplay implements DlcEarlyProgress.Display {
        Thread owner;
        int ticks;
        int closes;
        final List<DlcEarlyProgress.Frame> frames = new ArrayList<>();
        Consumer<DlcEarlyProgress.Frame> onUpdate = frame -> {};

        private void checkThread() {
            if (owner == null) owner = Thread.currentThread();
            assertSame(owner, Thread.currentThread(), "Native UI was called from a worker");
        }

        @Override public void update(DlcEarlyProgress.Frame frame) {
            checkThread();
            frames.add(frame);
            onUpdate.accept(frame);
        }
        @Override public void tick() { checkThread(); ticks++; }
        @Override public void close() { checkThread(); closes++; }
    }
}
