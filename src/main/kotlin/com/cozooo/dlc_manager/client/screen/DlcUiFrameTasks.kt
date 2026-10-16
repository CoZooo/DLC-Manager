package com.cozooo.dlc_manager.client.screen

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor

class DlcUiFrameTasks : Executor {
    private val pending = ConcurrentLinkedQueue<Runnable>()
    private var ready: List<Runnable> = emptyList()

    override fun execute(task: Runnable) {
        pending.add(task)
    }

    fun runPending() {
        val current = ready
        ready = emptyList()
        current.forEach { it.run() }
        val count = pending.size
        val next = ArrayList<Runnable>(count)
        for (index in 0 until count) {
            val task = pending.poll() ?: break
            next.add(task)
        }
        ready = next
    }

    fun clear() {
        pending.clear()
        ready = emptyList()
    }
}
