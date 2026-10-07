package com.cozooo.dlc_manager.client.screen

import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Supplier

class DlcUiInputBinding(
    private val value: Supplier<String?>,
    private val current: BooleanSupplier,
    private val change: Consumer<String>
) : Runnable {
    private var previous = value.get().orEmpty()

    override fun run() {
        if (!current.asBoolean) return
        val next = value.get().orEmpty()
        if (next == previous) return
        previous = next
        change.accept(next)
    }
}
