package com.cozooo.dlc_manager.client.screen;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

public final class DlcUiAsyncSearch<K> {
    public static final class Entry<K> {
        private final K key;
        private final String[] fields;
        public Entry(K key, String... fields) {
            this.key = key;
            this.fields = Arrays.stream(fields).map(value -> value == null ? "" : value).toArray(String[]::new);
        }
        public K getKey() { return key; }
    }
    private record Request<K>(long generation, String query, List<Entry<K>> entries, Consumer<Set<K>> apply) {}
    private final Executor worker, owner;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Request<K>> pending = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean();

    public DlcUiAsyncSearch(Executor worker, Executor owner) { this.worker = worker; this.owner = owner; }

    public void request(String query, List<Entry<K>> entries, Consumer<Set<K>> apply) {
        long revision = generation.incrementAndGet();
        List<Entry<K>> snapshot = List.copyOf(entries);
        if (query == null || query.isBlank()) {
            pending.set(null);
            Set<K> keys = new HashSet<>();
            for (Entry<K> entry : snapshot) keys.add(entry.key);
            deliver(new Request<>(revision, "", snapshot, apply), Collections.unmodifiableSet(keys));
        } else {
            pending.set(new Request<>(revision, query, snapshot, apply));
            start();
        }
    }

    public void cancel() { generation.incrementAndGet(); pending.set(null); }

    private void start() {
        if (running.compareAndSet(false, true)) worker.execute(this::drain);
    }

    private void drain() {
        try {
            Request<K> request;
            while ((request = pending.getAndSet(null)) != null) {
                Set<K> matches = new HashSet<>();
                for (Entry<K> entry : request.entries) {
                    if (generation.get() != request.generation) break;
                    if (DlcUiSearch.matches(request.query, entry.fields)) matches.add(entry.key);
                }
                if (generation.get() == request.generation) deliver(request, Collections.unmodifiableSet(matches));
            }
        } finally {
            running.set(false);
            if (pending.get() != null) start();
        }
    }

    private void deliver(Request<K> request, Set<K> result) {
        owner.execute(() -> { if (generation.get() == request.generation) request.apply.accept(result); });
    }
}
