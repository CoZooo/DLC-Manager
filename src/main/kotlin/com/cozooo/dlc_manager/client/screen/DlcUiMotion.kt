package com.cozooo.dlc_manager.client.screen

class DlcUiMotion(value: Double) {
    private var start = value.coerceIn(0.0, 1.0)
    private var target = start
    private var startedAt = 0L

    fun getTarget(): Double = target

    fun animateTo(value: Double, now: Long) {
        val next = value.coerceIn(0.0, 1.0)
        if (next == target) return
        start = valueAt(now)
        target = next
        startedAt = now
    }

    fun valueAt(now: Long): Double {
        if (start == target) return target
        val time = ((now - startedAt).toDouble() / 240_000_000L).coerceIn(0.0, 1.0)
        if (time == 0.0) return start
        if (time == 1.0) return target
        var low = 0.0
        var high = 1.0
        repeat(24) {
            val t = (low + high) / 2
            val x = 0.6 * t * (1 - t) * (1 - t) + t * t * t
            if (x < time) low = t else high = t
        }
        val t = (low + high) / 2
        return start + (target - start) * t * t * (3 - 2 * t)
    }
}
