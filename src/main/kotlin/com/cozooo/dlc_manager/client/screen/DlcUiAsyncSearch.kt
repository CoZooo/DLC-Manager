package com.cozooo.dlc_manager.client.screen

import java.util.Collections
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

class DlcUiAsyncSearch<K>(private val worker: Executor, private val owner: Executor) {
    class Entry<K>(val key: K, vararg fields: String?) {
        internal val fields = fields.map { it.orEmpty() }.toTypedArray()
    }
    private class Request<K>(val generation: Long, val query: String, val entries: List<Entry<K>>, val apply: Consumer<Set<K>>)
    private val generation = AtomicLong()
    private val pending = AtomicReference<Request<K>?>()
    private val running = AtomicBoolean()

    fun request(query: String?, entries: List<Entry<K>>, apply: Consumer<Set<K>>) {
        val revision = generation.incrementAndGet()
        val snapshot = entries.toList()
        if (query.isNullOrBlank()) {
            pending.set(null)
            deliver(Request(revision, "", snapshot, apply), Collections.unmodifiableSet(snapshot.mapTo(HashSet()) { it.key }))
        } else {
            pending.set(Request(revision, query, snapshot, apply))
            start()
        }
    }

    fun cancel() { generation.incrementAndGet(); pending.set(null) }

    private fun start() {
        if (running.compareAndSet(false, true)) worker.execute { drain() }
    }

    private fun drain() {
        try {
            while (true) {
                val request = pending.getAndSet(null) ?: break
                val matches = HashSet<K>()
                for (entry in request.entries) {
                    if (generation.get() != request.generation) break
                    if (DlcUiSearch.matches(request.query, *entry.fields)) matches.add(entry.key)
                }
                if (generation.get() == request.generation) deliver(request, Collections.unmodifiableSet(matches))
            }
        } finally {
            running.set(false)
            if (pending.get() != null) start()
        }
    }

    private fun deliver(request: Request<K>, result: Set<K>) {
        owner.execute { if (generation.get() == request.generation) request.apply.accept(result) }
    }
}
