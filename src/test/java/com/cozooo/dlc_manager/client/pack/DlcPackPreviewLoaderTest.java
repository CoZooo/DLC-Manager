package com.cozooo.dlc_manager.client.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DlcPackPreviewLoaderTest {
    @TempDir Path temp;

    @Test
    void readingReturnsImmediatelyAndDeliversOnlyOnOwnerExecutor() throws Exception {
        Path dlc = createPack();
        var worker = Executors.newSingleThreadExecutor();
        var release = new CountDownLatch(1);
        try {
            worker.submit(() -> { release.await(); return null; });
            var owner = new LinkedBlockingQueue<Runnable>();
            var loader = new DlcPackPreviewLoader(worker);
            var delivered = new AtomicInteger();
            Thread ownerThread = Thread.currentThread();
            loader.load(temp, dlc, temp.resolve("order.dc"), owner::add, snapshot -> {
                assertSame(ownerThread, Thread.currentThread());
                assertEquals(1, snapshot.previews.size());
                assertEquals("preview", snapshot.previews.get(0).metadata.identifier);
                assertArrayEquals("icon".getBytes(StandardCharsets.UTF_8), snapshot.previews.get(0).icon);
                delivered.incrementAndGet();
            }, error -> fail(error));
            assertEquals(0, delivered.get());
            assertTrue(owner.isEmpty());
            release.countDown();
            Runnable delivery = owner.poll(10, TimeUnit.SECONDS);
            assertNotNull(delivery);
            assertEquals(0, delivered.get());
            delivery.run();
            assertEquals(1, delivered.get());
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void cancelledAndSupersededResultsCannotUpdatePage() throws Exception {
        Path dlc = createPack();
        var worker = Executors.newSingleThreadExecutor();
        try {
            var owner = new LinkedBlockingQueue<Runnable>();
            var loader = new DlcPackPreviewLoader(worker);
            var delivered = new AtomicInteger();
            loader.load(temp, dlc, temp.resolve("order.dc"), owner::add, snapshot -> delivered.incrementAndGet(), error -> fail(error));
            Runnable cancelled = owner.poll(10, TimeUnit.SECONDS);
            assertNotNull(cancelled);
            loader.cancel();
            cancelled.run();
            assertEquals(0, delivered.get());
            loader.load(temp, dlc, temp.resolve("order.dc"), owner::add, snapshot -> delivered.incrementAndGet(), error -> fail(error));
            Runnable obsolete = owner.poll(10, TimeUnit.SECONDS);
            assertNotNull(obsolete);
            loader.load(temp, dlc, temp.resolve("order.dc"), owner::add, snapshot -> delivered.incrementAndGet(), error -> fail(error));
            Runnable current = owner.poll(10, TimeUnit.SECONDS);
            assertNotNull(current);
            obsolete.run();
            assertEquals(0, delivered.get());
            current.run();
            assertEquals(1, delivered.get());
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private Path createPack() throws Exception {
        Path dlc = temp.resolve("DLC");
        Files.createDirectories(dlc.resolve("pack"));
        try (var zip = new ZipOutputStream(Files.newOutputStream(dlc.resolve("pack/preview.zip")))) {
            zip.putNextEntry(new ZipEntry("metadata/pack.dc"));
            zip.write("[basic]\nidentifier = \"preview\"\nversion = \"1.0.0\"\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("metadata/icon.png"));
            zip.write("icon".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return dlc;
    }
}
