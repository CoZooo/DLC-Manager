package com.cozooo.dlc_manager.client.screen;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DlcUiAsyncSearchTest {
    private static final class QueueExecutor implements Executor {
        final Queue<Runnable> queue = new ArrayDeque<>();
        public void execute(Runnable action) { queue.add(action); }
        void drain() { while (!queue.isEmpty()) queue.remove().run(); }
    }
    private static final List<DlcUiAsyncSearch.Entry<String>> ENTRIES = List.of(
        new DlcUiAsyncSearch.Entry<>("textures", "高清材质包"),
        new DlcUiAsyncSearch.Entry<>("weapons", "Weapons"));

    @Test
    void rapidTypingComputesAndAppliesOnlyLatestQuery() {
        var worker = new QueueExecutor(); var owner = new QueueExecutor();
        var search = new DlcUiAsyncSearch<String>(worker, owner);
        var applied = new ArrayList<Set<String>>();
        search.request("weapons", ENTRIES, applied::add);
        search.request("missing", ENTRIES, applied::add);
        search.request("gaoqing", ENTRIES, applied::add);
        assertEquals(1, worker.queue.size());
        assertTrue(applied.isEmpty());
        worker.drain();
        assertTrue(applied.isEmpty());
        owner.drain();
        assertEquals(List.of(Set.of("textures")), applied);
    }

    @Test
    void clearingRestoresAllImmediatelyAndRejectsQueuedOlderResult() {
        var worker = new QueueExecutor(); var owner = new QueueExecutor();
        var search = new DlcUiAsyncSearch<String>(worker, owner);
        var applied = new ArrayList<Set<String>>();
        search.request("weapons", ENTRIES, applied::add);
        worker.drain();
        search.request("", ENTRIES, applied::add);
        assertTrue(worker.queue.isEmpty());
        owner.drain();
        assertEquals(List.of(Set.of("textures", "weapons")), applied);
    }

    @Test
    void newerQueryRejectsCompletedResultBeforeOwnerAppliesIt() {
        var worker = new QueueExecutor(); var owner = new QueueExecutor();
        var search = new DlcUiAsyncSearch<String>(worker, owner);
        var applied = new ArrayList<Set<String>>();
        search.request("weapons", ENTRIES, applied::add);
        worker.drain();
        search.request("missing", ENTRIES, applied::add);
        owner.drain();
        assertTrue(applied.isEmpty());
        worker.drain(); owner.drain();
        assertEquals(List.of(Set.of()), applied);
    }

    @Test
    void cancelRejectsQueuedComputationAndQueuedCompletion() {
        var worker = new QueueExecutor(); var owner = new QueueExecutor();
        var search = new DlcUiAsyncSearch<String>(worker, owner);
        var applied = new ArrayList<Set<String>>();
        search.request("weapons", ENTRIES, applied::add);
        search.cancel(); worker.drain(); owner.drain();
        search.request("gaoqing", ENTRIES, applied::add);
        worker.drain(); search.cancel(); owner.drain();
        assertTrue(applied.isEmpty());
        search.request("weapons", ENTRIES, applied::add);
        worker.drain(); owner.drain();
        assertEquals(List.of(Set.of("weapons")), applied);
    }

    @Test
    void snapshotsSourceListAndFieldsBeforeWorkerStarts() {
        var worker = new QueueExecutor(); var owner = new QueueExecutor();
        var search = new DlcUiAsyncSearch<String>(worker, owner);
        String[] fields = { "Weapons", null };
        var source = new ArrayList<>(List.of(new DlcUiAsyncSearch.Entry<>("weapons", fields)));
        var result = new AtomicReference<Set<String>>();
        search.request("weapons", source, result::set);
        fields[0] = "changed"; source.clear();
        worker.drain(); owner.drain();
        assertEquals(Set.of("weapons"), result.get());
        assertThrows(UnsupportedOperationException.class, () -> result.get().clear());
    }

    @Test
    void realWorkerDeliversResultOnOwnerThread() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        var completed = new LinkedBlockingQueue<Runnable>();
        var callbackThread = new AtomicReference<Thread>();
        var result = new AtomicReference<Set<String>>();
        Thread owner = Thread.currentThread();
        try {
            var search = new DlcUiAsyncSearch<String>(worker, action -> {
                assertNotSame(owner, Thread.currentThread());
                completed.add(action);
            });
            search.request("gao qing", ENTRIES, keys -> {
                callbackThread.set(Thread.currentThread()); result.set(keys);
            });
            Runnable apply = completed.poll(10, TimeUnit.SECONDS);
            assertNotNull(apply);
            assertNull(result.get());
            apply.run();
            assertSame(owner, callbackThread.get());
            assertEquals(Set.of("textures"), result.get());
        } finally {
            worker.shutdownNow();
        }
    }
}
